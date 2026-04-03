package com.ech.backend.api.admin.user;

import com.ech.backend.api.admin.user.dto.AdminUserListItemResponse;
import com.ech.backend.api.admin.user.dto.AdminUserSaveRequest;
import com.ech.backend.api.admin.user.dto.OrgGroupOptionResponse;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;

    public AdminUserService(
            UserRepository userRepository,
            OrgGroupRepository orgGroupRepository,
            OrgGroupMemberRepository orgGroupMemberRepository
    ) {
        this.userRepository = userRepository;
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
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
        userRepository.save(user);
        applyOrgAssignments(user, req);
        return toResponse(user);
    }

    @Transactional
    public AdminUserListItemResponse updateUser(String employeeNo, AdminUserSaveRequest req) {
        User user = userRepository.findByEmployeeNo(employeeNo.trim())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + employeeNo));
        user.setEmail(req.email().trim());
        user.setName(req.name().trim());
        user.setRole(req.role() != null && !req.role().isBlank() ? req.role() : "MEMBER");
        user.setStatus(req.status() != null && !req.status().isBlank() ? req.status() : "ACTIVE");
        userRepository.save(user);
        applyOrgAssignments(user, req);
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(String employeeNo) {
        User user = userRepository.findByEmployeeNo(employeeNo.trim())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + employeeNo));
        userRepository.delete(user);
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
