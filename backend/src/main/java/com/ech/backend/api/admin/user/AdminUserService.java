package com.ech.backend.api.admin.user;

import com.ech.backend.api.admin.user.dto.AdminUserListItemResponse;
import com.ech.backend.api.admin.user.dto.AdminUserSaveRequest;
import com.ech.backend.api.admin.user.dto.OrgGroupOptionResponse;
import com.ech.backend.api.user.UserProfileImageService;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelReadStateRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.kanban.KanbanBoardRepository;
import com.ech.backend.domain.kanban.KanbanCardAssigneeRepository;
import com.ech.backend.domain.kanban.KanbanCardEventRepository;
import com.ech.backend.domain.kanban.KanbanCardRepository;
import com.ech.backend.domain.kanban.KanbanColumnRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.ech.backend.domain.work.WorkItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.IOException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminUserService {

    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository userRepository;
    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final KanbanCardEventRepository kanbanCardEventRepository;
    private final KanbanCardAssigneeRepository kanbanCardAssigneeRepository;
    private final KanbanCardRepository kanbanCardRepository;
    private final KanbanColumnRepository kanbanColumnRepository;
    private final KanbanBoardRepository kanbanBoardRepository;
    private final WorkItemRepository workItemRepository;
    private final MessageRepository messageRepository;
    private final ChannelReadStateRepository channelReadStateRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelFileRepository channelFileRepository;
    private final ChannelRepository channelRepository;
    private final UserProfileImageService userProfileImageService;

    public AdminUserService(
            UserRepository userRepository,
            OrgGroupRepository orgGroupRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            PasswordEncoder passwordEncoder,
            KanbanCardEventRepository kanbanCardEventRepository,
            KanbanCardAssigneeRepository kanbanCardAssigneeRepository,
            KanbanCardRepository kanbanCardRepository,
            KanbanColumnRepository kanbanColumnRepository,
            KanbanBoardRepository kanbanBoardRepository,
            WorkItemRepository workItemRepository,
            MessageRepository messageRepository,
            ChannelReadStateRepository channelReadStateRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelFileRepository channelFileRepository,
            ChannelRepository channelRepository,
            UserProfileImageService userProfileImageService
    ) {
        this.userRepository = userRepository;
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.kanbanCardEventRepository = kanbanCardEventRepository;
        this.kanbanCardAssigneeRepository = kanbanCardAssigneeRepository;
        this.kanbanCardRepository = kanbanCardRepository;
        this.kanbanColumnRepository = kanbanColumnRepository;
        this.kanbanBoardRepository = kanbanBoardRepository;
        this.workItemRepository = workItemRepository;
        this.messageRepository = messageRepository;
        this.channelReadStateRepository = channelReadStateRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.channelFileRepository = channelFileRepository;
        this.channelRepository = channelRepository;
        this.userProfileImageService = userProfileImageService;
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
                    user.getCreatedAt(),
                    user.getProfileImageRelPath() != null && !user.getProfileImageRelPath().isBlank()
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
            userProfileImageService.renameStorageIfNeeded(employeeNo.trim(), newEmpNo, user);
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
     * 실제 DB에 CASCADE/SET NULL이 없으므로 FK 참조 순서에 맞게 모두 수동 처리한다.
     *
     * [칸반 삭제 순서]
     *  1) kanban_card_events    — actor = empNo + user's board 카드 이벤트
     *  2) kanban_card_assignees — user = empNo + user's board 카드 담당자
     *  3) kanban_cards.work_item_id NULL 초기화 (work_items 삭제 전)
     *  4) work_items            — source_channel in user's channels
     *  5) work_items            — created_by = empNo
     *  6) kanban_cards          — user's board columns 소속
     *  7) kanban_columns        — user's boards 소속
     *  8) kanban_boards         — created_by = empNo
     *
     * [채널 삭제 순서]
     *  9) channel_read_states.last_read_message_id NULL (sender + user's channels 기준)
     * 10) messages.parent_message_id NULL           (user's channels 내 전체)
     * 11) messages.parent_message_id NULL           (sender = empNo — 다른 채널)
     * 12) messages                                  — user's channels 내 전체 삭제
     * 13) messages                                  — sender = empNo (다른 채널)
     * 14) channel_read_states                       — user's channels + user 기준
     * 15) channel_members                           — user's channels + user 기준
     * 16) channel_files                             — user's channels 소속
     * 17) channel_files                             — uploaded_by = empNo
     * 18) channels                                  — created_by = empNo
     *
     * [사용자 삭제]
     * 19) users (org_group_members ON DELETE CASCADE 자동 처리)
     */
    @Transactional
    public void deleteUser(String employeeNo) {
        String empNo = employeeNo.trim();
        User toDelete = userRepository.findByEmployeeNo(empNo)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + empNo));
        userProfileImageService.deleteStoredFileOnly(toDelete);

        // ── Kanban ──────────────────────────────────────────────────────────
        kanbanCardEventRepository.deleteAllRelatedToEmployeeNo(empNo);
        kanbanCardAssigneeRepository.deleteAllRelatedToEmployeeNo(empNo);
        kanbanCardRepository.nullWorkItemRefByUserEmployeeNo(empNo);
        workItemRepository.deleteBySourceChannelCreatorEmployeeNo(empNo);
        workItemRepository.deleteByCreatorEmployeeNo(empNo);
        kanbanCardRepository.deleteByBoardCreatorEmployeeNo(empNo);
        kanbanColumnRepository.deleteByBoardCreatorEmployeeNo(empNo);
        kanbanBoardRepository.deleteByCreatorEmployeeNo(empNo);

        // ── Channel ─────────────────────────────────────────────────────────
        channelReadStateRepository.nullLastReadRefByEmployeeNo(empNo);
        messageRepository.nullParentRefByChannelCreatorEmployeeNo(empNo);
        messageRepository.nullParentRefBySenderEmployeeNo(empNo);
        messageRepository.deleteByChannelCreatorEmployeeNo(empNo);
        messageRepository.deleteBySenderEmployeeNo(empNo);
        channelReadStateRepository.deleteAllRelatedToEmployeeNo(empNo);
        channelMemberRepository.deleteAllRelatedToEmployeeNo(empNo);
        channelFileRepository.deleteByChannelCreatorEmployeeNo(empNo);
        channelFileRepository.deleteByUploaderEmployeeNo(empNo);
        channelRepository.deleteByCreatorEmployeeNo(empNo);

        // ── User ─────────────────────────────────────────────────────────────
        userRepository.deleteByEmployeeNo(empNo);
    }

    @Transactional
    public void uploadProfileImage(String employeeNo, MultipartFile file) throws IOException {
        User user = userRepository.findByEmployeeNo(employeeNo.trim())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + employeeNo));
        userProfileImageService.saveProfileImage(user, file);
    }

    @Transactional(readOnly = true)
    public OrgGroupOptionResponse getOrgGroupOptions() {
        var toOption = (java.util.function.Function<OrgGroup, OrgGroupOptionResponse.OrgGroupOption>)
                g -> new OrgGroupOptionResponse.OrgGroupOption(g.getGroupCode(), g.getDisplayName());

        List<OrgGroupOptionResponse.OrgGroupOption> companies =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("COMPANY", true)
                        .stream()
                        .map(g -> new OrgGroupOptionResponse.OrgGroupOption(
                                g.getGroupCode(), "[회사] " + g.getDisplayName()))
                        .toList();
        List<OrgGroupOptionResponse.OrgGroupOption> divisions =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("DIVISION", true)
                        .stream()
                        .map(g -> new OrgGroupOptionResponse.OrgGroupOption(
                                g.getGroupCode(), "[본부] " + g.getDisplayName()))
                        .toList();
        List<OrgGroupOptionResponse.OrgGroupOption> teamsList =
                orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("TEAM", true)
                        .stream().map(toOption).toList();
        List<OrgGroupOptionResponse.OrgGroupOption> teams = new ArrayList<>();
        teams.addAll(companies);
        teams.addAll(divisions);
        teams.addAll(teamsList);
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
                user.getCreatedAt(),
                user.getProfileImageRelPath() != null && !user.getProfileImageRelPath().isBlank()
        );
    }
}
