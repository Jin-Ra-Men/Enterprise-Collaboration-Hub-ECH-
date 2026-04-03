package com.ech.backend.api.admin.user;

import com.ech.backend.api.admin.user.dto.AdminUserListItemResponse;
import com.ech.backend.api.admin.user.dto.AdminUserSaveRequest;
import com.ech.backend.api.admin.user.dto.OrgGroupOptionResponse;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.kanban.KanbanBoardRepository;
import com.ech.backend.domain.kanban.KanbanCardEventRepository;
import com.ech.backend.domain.kanban.KanbanCardRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.ech.backend.domain.work.WorkItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository userRepository;
    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final KanbanCardEventRepository kanbanCardEventRepository;
    private final KanbanCardRepository kanbanCardRepository;
    private final KanbanBoardRepository kanbanBoardRepository;
    private final WorkItemRepository workItemRepository;
    private final MessageRepository messageRepository;
    private final ChannelFileRepository channelFileRepository;
    private final ChannelRepository channelRepository;

    public AdminUserService(
            UserRepository userRepository,
            OrgGroupRepository orgGroupRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            PasswordEncoder passwordEncoder,
            KanbanCardEventRepository kanbanCardEventRepository,
            KanbanCardRepository kanbanCardRepository,
            KanbanBoardRepository kanbanBoardRepository,
            WorkItemRepository workItemRepository,
            MessageRepository messageRepository,
            ChannelFileRepository channelFileRepository,
            ChannelRepository channelRepository
    ) {
        this.userRepository = userRepository;
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.kanbanCardEventRepository = kanbanCardEventRepository;
        this.kanbanCardRepository = kanbanCardRepository;
        this.kanbanBoardRepository = kanbanBoardRepository;
        this.workItemRepository = workItemRepository;
        this.messageRepository = messageRepository;
        this.channelFileRepository = channelFileRepository;
        this.channelRepository = channelRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserListItemResponse> getAllUsers() {
        List<User> users = userRepository.findAllByOrderByNameAsc();
        if (users.isEmpty()) return List.of();

        List<String> empNos = users.stream().map(User::getEmployeeNo).toList();
        List<OrgGroupMember> allMembers = orgGroupMemberRepository.findAllByEmployeeNosWithGroupAndUser(empNos);

        // employeeNo → (memberGroupType → member)
        Map<String, Map<String, OrgGroupMember>> memberMap = allMembers.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getUser().getEmployeeNo(),
                        Collectors.toMap(OrgGroupMember::getMemberGroupType, m -> m, (a, b) -> a)
                ));

        return users.stream().map(user -> {
            Map<String, OrgGroupMember> byType = memberMap.getOrDefault(user.getEmployeeNo(), Map.of());
            OrgGroupMember team = byType.get("TEAM");
            OrgGroupMember jl   = byType.get("JOB_LEVEL");
            OrgGroupMember jp   = byType.get("JOB_POSITION");
            OrgGroupMember jt   = byType.get("JOB_TITLE");
            return new AdminUserListItemResponse(
                    user.getEmployeeNo(), user.getEmail(), user.getName(), user.getRole(), user.getStatus(),
                    team != null ? team.getGroup().getGroupCode() : null,
                    team != null ? team.getGroup().getDisplayName() : null,
                    jl   != null ? jl.getGroup().getGroupCode()   : null,
                    jl   != null ? jl.getGroup().getDisplayName() : null,
                    jp   != null ? jp.getGroup().getGroupCode()   : null,
                    jp   != null ? jp.getGroup().getDisplayName() : null,
                    jt   != null ? jt.getGroup().getGroupCode()   : null,
                    jt   != null ? jt.getGroup().getDisplayName() : null,
                    user.getCreatedAt()
            );
        }).toList();
    }

    @Transactional
    public AdminUserListItemResponse createUser(AdminUserSaveRequest req) {
        if (userRepository.findByEmployeeNo(req.employeeNo().trim()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 사원번호입니다: " + req.employeeNo());
        }
        User user = new User(req.employeeNo().trim(), req.email().trim(), req.name().trim(),
                req.role() != null && !req.role().isBlank() ? req.role() : "MEMBER");
        user.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        userRepository.save(user);
        applyOrgAssignments(user, req);
        return toResponse(user);
    }

    @Transactional
    public AdminUserListItemResponse updateUser(String employeeNo, AdminUserSaveRequest req) {
        User user = userRepository.findByEmployeeNo(employeeNo.trim())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + employeeNo));

        // 사원번호 변경 처리
        String newEmpNo = req.employeeNo() != null ? req.employeeNo().trim() : employeeNo.trim();
        if (!newEmpNo.equals(employeeNo.trim())) {
            if (userRepository.findByEmployeeNo(newEmpNo).isPresent()) {
                throw new IllegalArgumentException("이미 존재하는 사원번호입니다: " + newEmpNo);
            }
            user.setEmployeeNo(newEmpNo);
        }

        user.setEmail(req.email().trim());
        user.setName(req.name().trim());
        user.setRole(req.role() != null && !req.role().isBlank() ? req.role() : "MEMBER");
        user.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
        userRepository.save(user);
        applyOrgAssignments(user, req);
        return toResponse(user);
    }

    /**
     * 사용자 완전 삭제.
     * users.employee_no를 참조하는 FK(ON DELETE CASCADE 없는 것)를 순서대로 정리 후 삭제한다.
     *
     * 삭제 순서:
     *  1) kanban_card_events    (actor_user_id → users, RESTRICT)
     *  2) kanban_cards          work_item_id NULL 초기화 (→ work_items, RESTRICT)
     *  3) work_items            source_channel 기준 (채널 삭제 전 처리)
     *  4) work_items            created_by 기준
     *  5) kanban_boards         created_by → users (CASCADE: 컬럼→카드→담당자·이벤트)
     *  6) messages              parent_message_id NULL 초기화 (자기참조 RESTRICT 대비)
     *  7) messages              sender_id → users, RESTRICT
     *  8) channel_files         채널 삭제 전 해당 채널 소속 파일 전체 삭제 (channel_id FK, CASCADE 없는 경우)
     *  9) channel_files         uploaded_by → users, RESTRICT (다른 채널 소속)
     * 10) channels              created_by → users (CASCADE: 멤버·메시지·읽음상태·파일)
     * 11) users                 (org_group_members·channel_members 등 CASCADE 자동 정리)
     */
    @Transactional
    public void deleteUser(String employeeNo) {
        String empNo = employeeNo.trim();
        userRepository.findByEmployeeNo(empNo)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + empNo));

        // 1. 칸반 카드 이벤트 (actor → users)
        kanbanCardEventRepository.deleteByActorEmployeeNo(empNo);
        // 2. 칸반 카드의 work_item 참조 NULL 초기화 (work_items 삭제 전)
        kanbanCardRepository.nullWorkItemRefByUserEmployeeNo(empNo);
        // 3-4. work_items 삭제 (채널 기준 → 생성자 기준 순)
        workItemRepository.deleteBySourceChannelCreatorEmployeeNo(empNo);
        workItemRepository.deleteByCreatorEmployeeNo(empNo);
        // 5. 칸반 보드 삭제 (CASCADE: 컬럼→카드→담당자·이벤트)
        kanbanBoardRepository.deleteByCreatorEmployeeNo(empNo);
        // 6. 메시지 자기참조 NULL 초기화 (parent_message_id RESTRICT 대비)
        messageRepository.nullParentRefBySenderEmployeeNo(empNo);
        // 7. 메시지 삭제 (sender_id → users)
        messageRepository.deleteBySenderEmployeeNo(empNo);
        // 8. 채널 소속 파일 삭제 (channel_id FK CASCADE 없는 경우)
        channelFileRepository.deleteByChannelCreatorEmployeeNo(empNo);
        // 9. 업로더 기준 파일 삭제 (다른 채널 소속)
        channelFileRepository.deleteByUploaderEmployeeNo(empNo);
        // 10. 채널 삭제 (CASCADE: 멤버·메시지·읽음상태·파일)
        channelRepository.deleteByCreatorEmployeeNo(empNo);
        // 11. 사용자 삭제 (org_group_members 등 CASCADE 자동 처리)
        userRepository.deleteByEmployeeNo(empNo);
    }

    @Transactional(readOnly = true)
    public OrgGroupOptionResponse getOrgGroupOptions() {
        var toOption = (java.util.function.Function<OrgGroup, OrgGroupOptionResponse.OrgGroupOption>)
                g -> new OrgGroupOptionResponse.OrgGroupOption(g.getGroupCode(), g.getDisplayName());

        List<OrgGroupOptionResponse.OrgGroupOption> teams =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("TEAM", true)
                        .stream().map(toOption).toList();
        List<OrgGroupOptionResponse.OrgGroupOption> jobLevels =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("JOB_LEVEL", true)
                        .stream().map(toOption).toList();
        List<OrgGroupOptionResponse.OrgGroupOption> jobPositions =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("JOB_POSITION", true)
                        .stream().map(toOption).toList();
        List<OrgGroupOptionResponse.OrgGroupOption> jobTitles =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("JOB_TITLE", true)
                        .stream().map(toOption).toList();

        return new OrgGroupOptionResponse(teams, jobLevels, jobPositions, jobTitles);
    }

    // ──────────────────────────────────────────────────────────────────────

    private void applyOrgAssignments(User user, AdminUserSaveRequest req) {
        applyOrgMember(user, "TEAM",         req.teamGroupCode());
        applyOrgMember(user, "JOB_LEVEL",    req.jobLevelGroupCode());
        applyOrgMember(user, "JOB_POSITION", req.jobPositionGroupCode());
        applyOrgMember(user, "JOB_TITLE",    req.jobTitleGroupCode());
    }

    private void applyOrgMember(User user, String memberGroupType, String groupCode) {
        Optional<OrgGroupMember> existing =
                orgGroupMemberRepository.findByUser_EmployeeNoAndMemberGroupType(
                        user.getEmployeeNo(), memberGroupType);

        if (groupCode == null || groupCode.isBlank()) {
            existing.ifPresent(orgGroupMemberRepository::delete);
            return;
        }

        OrgGroup group = orgGroupRepository.findByGroupCode(groupCode).orElse(null);
        if (group == null) return;

        if (existing.isPresent()) {
            existing.get().setGroup(group);
        } else {
            orgGroupMemberRepository.save(new OrgGroupMember(user, group, memberGroupType));
        }
    }

    private AdminUserListItemResponse toResponse(User user) {
        OrgGroupMember team = orgGroupMemberRepository
                .findByUser_EmployeeNoAndMemberGroupType(user.getEmployeeNo(), "TEAM").orElse(null);
        OrgGroupMember jl = orgGroupMemberRepository
                .findByUser_EmployeeNoAndMemberGroupType(user.getEmployeeNo(), "JOB_LEVEL").orElse(null);
        OrgGroupMember jp = orgGroupMemberRepository
                .findByUser_EmployeeNoAndMemberGroupType(user.getEmployeeNo(), "JOB_POSITION").orElse(null);
        OrgGroupMember jt = orgGroupMemberRepository
                .findByUser_EmployeeNoAndMemberGroupType(user.getEmployeeNo(), "JOB_TITLE").orElse(null);
        return new AdminUserListItemResponse(
                user.getEmployeeNo(), user.getEmail(), user.getName(), user.getRole(), user.getStatus(),
                team != null ? team.getGroup().getGroupCode() : null,
                team != null ? team.getGroup().getDisplayName() : null,
                jl   != null ? jl.getGroup().getGroupCode()   : null,
                jl   != null ? jl.getGroup().getDisplayName() : null,
                jp   != null ? jp.getGroup().getGroupCode()   : null,
                jp   != null ? jp.getGroup().getDisplayName() : null,
                jt   != null ? jt.getGroup().getGroupCode()   : null,
                jt   != null ? jt.getGroup().getDisplayName() : null,
                user.getCreatedAt()
        );
    }
}
