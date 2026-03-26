package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.OrgCompanyResponse;
import com.ech.backend.api.user.dto.OrgDivisionResponse;
import com.ech.backend.api.user.dto.OrganizationCompanyFilterOption;
import com.ech.backend.api.user.dto.OrganizationCompanyFiltersResponse;
import com.ech.backend.api.user.dto.OrganizationTreeResponse;
import com.ech.backend.api.user.dto.OrgTeamResponse;
import com.ech.backend.api.user.dto.UserProfileResponse;
import com.ech.backend.api.user.dto.UserSearchResponse;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserRepository userRepository;
    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;

    public UserSearchService(
            UserRepository userRepository,
            OrgGroupRepository orgGroupRepository,
            OrgGroupMemberRepository orgGroupMemberRepository
    ) {
        this.userRepository = userRepository;
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
    }

    public List<UserSearchResponse> searchUsers(String keyword, String department) {
        String normalizedKeyword = normalize(keyword);
        String normalizedDepartment = normalize(department);
        Long idMatch = parseIdKeyword(normalizedKeyword);

        return userRepository.searchUsers(normalizedKeyword, normalizedDepartment, idMatch);
    }

    /**
     * 회사 → 본부 → 팀(부서) → 사용자 (조직도 UI용).
     *
     * @param companyGroupCode {@code org_groups.group_code} (COMPANY 타입). null/빈 값이면 전체.
     */
    public OrganizationTreeResponse getOrganizationTree(String companyGroupCode) {
        String normalized = (companyGroupCode == null) ? null : companyGroupCode.trim();

        List<OrgGroup> companies;
        if (normalized == null || normalized.isEmpty()) {
            companies = orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("COMPANY", true);
        } else {
            Optional<OrgGroup> found = orgGroupRepository.findByGroupTypeAndGroupCode("COMPANY", normalized);
            companies = found.map(List::of).orElseGet(List::of);
        }

        if (companies.isEmpty()) {
            return new OrganizationTreeResponse(List.of());
        }

        Map<Long, OrgGroup> divisionById = new HashMap<>();
        Map<Long, List<OrgGroup>> teamsByDivisionId = new HashMap<>();
        List<OrgGroup> allTeams = new ArrayList<>();

        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = orgGroupRepository.findAllByGroupTypeAndCompanyGroup_IdAndIsActiveOrderByDisplayNameAsc(
                    "DIVISION",
                    company.getId(),
                    true
            );
            for (OrgGroup div : divisions) {
                divisionById.put(div.getId(), div);
                teamsByDivisionId.put(div.getId(), new ArrayList<>());
            }

            List<OrgGroup> teams = orgGroupRepository.findAllByGroupTypeAndCompanyGroup_IdAndIsActiveOrderByDisplayNameAsc(
                    "TEAM",
                    company.getId(),
                    true
            );
            for (OrgGroup team : teams) {
                OrgGroup parent = team.getParentGroup();
                if (parent == null) {
                    continue;
                }
                Long divisionId = parent.getId();
                teamsByDivisionId.computeIfAbsent(divisionId, k -> new ArrayList<>()).add(team);
            }
            allTeams.addAll(teams);
        }

        List<Long> teamIds = allTeams.stream().map(OrgGroup::getId).toList();
        List<OrgGroupMember> teamMembers = teamIds.isEmpty()
                ? List.of()
                : orgGroupMemberRepository.findMembersByMemberGroupTypeAndGroupIds("TEAM", teamIds);

        Map<Long, List<User>> usersByTeamId = new HashMap<>();
        Set<Long> userIds;
        if (teamMembers.isEmpty()) {
            userIds = Set.of();
        } else {
            userIds = teamMembers.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
        }

        for (OrgGroupMember m : teamMembers) {
            Long teamId = m.getGroup().getId();
            usersByTeamId.computeIfAbsent(teamId, k -> new ArrayList<>()).add(m.getUser());
        }

        Map<Long, String> jobRankByUserId;
        Map<Long, String> dutyTitleByUserId;
        if (userIds.isEmpty()) {
            jobRankByUserId = Map.of();
            dutyTitleByUserId = Map.of();
        } else {
            List<OrgGroupMember> jobMembers = orgGroupMemberRepository.findMembersByMemberGroupTypeAndUserIds("JOB_LEVEL", userIds);
            jobRankByUserId = jobMembers.stream().collect(Collectors.toMap(
                    m -> m.getUser().getId(),
                    m -> m.getGroup().getDisplayName(),
                    (a, b) -> a
            ));

            List<OrgGroupMember> dutyMembers = orgGroupMemberRepository.findMembersByMemberGroupTypeAndUserIds("DUTY_TITLE", userIds);
            dutyTitleByUserId = dutyMembers.stream().collect(Collectors.toMap(
                    m -> m.getUser().getId(),
                    m -> m.getGroup().getDisplayName(),
                    (a, b) -> a
            ));
        }

        List<OrgCompanyResponse> companiesResponse = new ArrayList<>();
        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = orgGroupRepository.findAllByGroupTypeAndCompanyGroup_IdAndIsActiveOrderByDisplayNameAsc(
                    "DIVISION",
                    company.getId(),
                    true
            );

            List<OrgDivisionResponse> divisionsResponse = new ArrayList<>();
            for (OrgGroup division : divisions) {
                List<OrgGroup> teams = teamsByDivisionId.getOrDefault(division.getId(), List.of());
                teams = teams.stream()
                        .sorted(Comparator.comparing(OrgGroup::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

                List<OrgTeamResponse> teamsResponse = teams.stream().map(team -> {
                    List<User> users = usersByTeamId.getOrDefault(team.getId(), List.of());
                    List<UserSearchResponse> members = users.stream()
                            .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                            .map(u -> toTreeSearchResponse(
                                    u,
                                    team.getDisplayName(),
                                    jobRankByUserId.get(u.getId()),
                                    dutyTitleByUserId.get(u.getId())
                            ))
                            .toList();
                    return new OrgTeamResponse(team.getDisplayName(), members);
                }).toList();

                divisionsResponse.add(new OrgDivisionResponse(division.getDisplayName(), teamsResponse));
            }

            companiesResponse.add(new OrgCompanyResponse(company.getDisplayName(), divisionsResponse));
        }

        return new OrganizationTreeResponse(companiesResponse);
    }

    /**
     * 조직도 팝업 상단 회사 셀렉트 옵션.
     * 회사 옵션은 org_groups(COMPANY, is_active=true)에서 가져온다.
     */
    public OrganizationCompanyFiltersResponse getOrganizationCompanyFilters() {
        List<OrgGroup> companies = orgGroupRepository.findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc("COMPANY", true);
        List<OrganizationCompanyFilterOption> options = new ArrayList<>();
        options.add(new OrganizationCompanyFilterOption("전체 (그룹사 공용)", null));
        for (OrgGroup company : companies) {
            options.add(new OrganizationCompanyFilterOption(company.getDisplayName(), company.getGroupCode()));
        }
        return new OrganizationCompanyFiltersResponse(options);
    }

    private static UserSearchResponse toTreeSearchResponse(
            User user,
            String department,
            String jobRank,
            String dutyTitle
    ) {
        String resolvedJobRank = (jobRank != null && !jobRank.isBlank()) ? jobRank : user.getJobRank();
        String resolvedDutyTitle = (dutyTitle != null && !dutyTitle.isBlank()) ? dutyTitle : user.getDutyTitle();
        String resolvedDepartment = (department != null) ? department : user.getDepartment();

        return new UserSearchResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getName(),
                user.getEmail(),
                resolvedDepartment,
                resolvedJobRank,
                resolvedDutyTitle,
                user.getRole(),
                user.getStatus()
        );
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return new UserProfileResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getName(),
                user.getEmail(),
                user.getDepartment(),
                user.getJobRank(),
                user.getDutyTitle(),
                user.getRole(),
                user.getStatus()
        );
    }

    private UserSearchResponse toSearchResponse(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getName(),
                user.getEmail(),
                user.getDepartment(),
                user.getJobRank(),
                user.getDutyTitle(),
                user.getRole(),
                user.getStatus()
        );
    }

    /**
     * 숫자만 입력된 검색어는 사용자 ID와 일치하는 행을 포함한다.
     */
    private static Long parseIdKeyword(String normalizedKeyword) {
        if (normalizedKeyword == null || !normalizedKeyword.matches("^\\d{1,18}$")) {
            return null;
        }
        try {
            return Long.parseLong(normalizedKeyword);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
