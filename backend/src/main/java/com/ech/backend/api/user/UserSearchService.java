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

        Map<String, List<OrgGroup>> teamsByDivisionCode = new HashMap<>();
        List<OrgGroup> allTeams = new ArrayList<>();

        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = orgGroupRepository.findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
                    "DIVISION",
                    company.getGroupCode(),
                    true
            );
            for (OrgGroup division : divisions) {
                teamsByDivisionCode.putIfAbsent(division.getGroupCode(), new ArrayList<>());
                List<OrgGroup> teams = orgGroupRepository.findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
                        "TEAM",
                        division.getGroupCode(),
                        true
                );
                teamsByDivisionCode.get(division.getGroupCode()).addAll(teams);
                allTeams.addAll(teams);
            }
        }

        List<String> teamCodes = allTeams.stream().map(OrgGroup::getGroupCode).toList();
        List<OrgGroupMember> teamMembers = teamCodes.isEmpty()
                ? List.of()
                : orgGroupMemberRepository.findMembersByMemberGroupTypeAndGroupCodes("TEAM", teamCodes);

        Map<String, List<User>> usersByTeamCode = new HashMap<>();
        Set<String> employeeNos = teamMembers.isEmpty()
                ? Set.of()
                : teamMembers.stream().map(m -> m.getUser().getEmployeeNo()).collect(Collectors.toSet());

        for (OrgGroupMember m : teamMembers) {
            String teamCode = m.getGroup().getGroupCode();
            usersByTeamCode.computeIfAbsent(teamCode, k -> new ArrayList<>()).add(m.getUser());
        }

        Map<Long, String> jobLevelByUserId;
        Map<Long, String> jobPositionByUserId;
        Map<Long, String> jobTitleByUserId;
        if (employeeNos.isEmpty()) {
            jobLevelByUserId = Map.of();
            jobPositionByUserId = Map.of();
            jobTitleByUserId = Map.of();
        } else {
            jobLevelByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_LEVEL", employeeNos)
            );
            jobPositionByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_POSITION", employeeNos)
            );
            jobTitleByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_TITLE", employeeNos)
            );
        }

        List<OrgCompanyResponse> companiesResponse = new ArrayList<>();
        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = orgGroupRepository.findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
                    "DIVISION",
                    company.getGroupCode(),
                    true
            );

            List<OrgDivisionResponse> divisionsResponse = new ArrayList<>();
            for (OrgGroup division : divisions) {
                List<OrgGroup> teams = teamsByDivisionCode.getOrDefault(division.getGroupCode(), List.of());
                teams = teams.stream()
                        .sorted(Comparator.comparing(OrgGroup::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

                List<OrgTeamResponse> teamsResponse = teams.stream().map(team -> {
                    List<User> users = usersByTeamCode.getOrDefault(team.getGroupCode(), List.of());
                    List<UserSearchResponse> members = users.stream()
                            .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                            .map(u -> toTreeSearchResponse(
                                    u,
                                    team.getDisplayName(),
                                    jobLevelByUserId.get(u.getId()),
                                    jobPositionByUserId.get(u.getId()),
                                    jobTitleByUserId.get(u.getId())
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

    private static Map<Long, String> toDisplayByUserId(List<OrgGroupMember> members) {
        return members.stream().collect(Collectors.toMap(
                m -> m.getUser().getId(),
                m -> m.getGroup().getDisplayName(),
                (a, b) -> a
        ));
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
            String jobLevel,
            String jobPosition,
            String jobTitle
    ) {
        return new UserSearchResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getName(),
                user.getEmail(),
                department,
                jobLevel,
                jobPosition,
                jobTitle,
                user.getRole(),
                user.getStatus()
        );
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toUserProfileResponse(user);
    }

    public UserProfileResponse getProfileByEmployeeNo(String employeeNo) {
        String emp = normalize(employeeNo);
        if (emp == null) {
            throw new IllegalArgumentException("사원번호가 필요합니다.");
        }
        User user = userRepository.findByEmployeeNo(emp)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return toUserProfileResponse(user);
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        String emp = user.getEmployeeNo();
        String department = lookupDisplayName(emp, "TEAM");
        String jobLevel = lookupDisplayName(emp, "JOB_LEVEL");
        String jobPosition = lookupDisplayName(emp, "JOB_POSITION");
        String jobTitle = lookupDisplayName(emp, "JOB_TITLE");
        return new UserProfileResponse(
                user.getId(),
                user.getEmployeeNo(),
                user.getName(),
                user.getEmail(),
                department,
                jobLevel,
                jobPosition,
                jobTitle,
                user.getRole(),
                user.getStatus()
        );
    }

    private String lookupDisplayName(String employeeNo, String memberGroupType) {
        return orgGroupMemberRepository.findByUser_EmployeeNoAndMemberGroupType(employeeNo, memberGroupType)
                .map(m -> m.getGroup().getDisplayName())
                .orElse(null);
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
