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

        // 회사/본부/팀 그룹 수집
        Map<String, List<OrgGroup>> divisionsByCompanyCode  = new HashMap<>();
        Map<String, List<OrgGroup>> teamsByDivisionCode     = new HashMap<>();
        List<OrgGroup> allDivisions = new ArrayList<>();
        List<OrgGroup> allTeams     = new ArrayList<>();

        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = orgGroupRepository
                    .findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
                            "DIVISION", company.getGroupCode(), true);
            divisionsByCompanyCode.put(company.getGroupCode(), divisions);
            allDivisions.addAll(divisions);

            for (OrgGroup division : divisions) {
                List<OrgGroup> teams = orgGroupRepository
                        .findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
                                "TEAM", division.getGroupCode(), true);
                teamsByDivisionCode.put(division.getGroupCode(), teams);
                allTeams.addAll(teams);
            }
        }

        // 팀 직속 멤버
        List<String> teamCodes     = allTeams.stream().map(OrgGroup::getGroupCode).toList();
        // 본부 직속 멤버 (TEAM 멤버십이 DIVISION 코드를 가리키는 경우)
        List<String> divisionCodes = allDivisions.stream().map(OrgGroup::getGroupCode).toList();
        // 회사 직속 멤버 (TEAM 멤버십이 COMPANY 코드를 가리키는 경우)
        List<String> companyCodes  = companies.stream().map(OrgGroup::getGroupCode).toList();

        List<String> allGroupCodes = new ArrayList<>();
        allGroupCodes.addAll(teamCodes);
        allGroupCodes.addAll(divisionCodes);
        allGroupCodes.addAll(companyCodes);

        List<OrgGroupMember> allMembers = allGroupCodes.isEmpty()
                ? List.of()
                : orgGroupMemberRepository.findMembersByMemberGroupTypeAndGroupCodes("TEAM", allGroupCodes);

        Map<String, List<User>> usersByGroupCode = new HashMap<>();
        Set<String> employeeNos = allMembers.isEmpty()
                ? Set.of()
                : allMembers.stream().map(m -> m.getUser().getEmployeeNo()).collect(Collectors.toSet());

        for (OrgGroupMember m : allMembers) {
            usersByGroupCode.computeIfAbsent(m.getGroup().getGroupCode(), k -> new ArrayList<>())
                    .add(m.getUser());
        }

        Map<Long, String> jobLevelByUserId;
        Map<Long, String> jobPositionByUserId;
        Map<Long, String> jobTitleByUserId;
        if (employeeNos.isEmpty()) {
            jobLevelByUserId    = Map.of();
            jobPositionByUserId = Map.of();
            jobTitleByUserId    = Map.of();
        } else {
            jobLevelByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_LEVEL", employeeNos));
            jobPositionByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_POSITION", employeeNos));
            jobTitleByUserId = toDisplayByUserId(
                    orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos("JOB_TITLE", employeeNos));
        }

        List<OrgCompanyResponse> companiesResponse = new ArrayList<>();
        for (OrgGroup company : companies) {
            List<OrgGroup> divisions = divisionsByCompanyCode.getOrDefault(company.getGroupCode(), List.of());

            // 회사 직속 멤버 (TEAM 멤버십 → COMPANY 그룹)
            List<UserSearchResponse> companyDirect = buildMemberList(
                    usersByGroupCode.getOrDefault(company.getGroupCode(), List.of()),
                    company.getDisplayName(), jobLevelByUserId, jobPositionByUserId, jobTitleByUserId);

            List<OrgDivisionResponse> divisionsResponse = new ArrayList<>();
            for (OrgGroup division : divisions) {
                List<OrgGroup> teams = teamsByDivisionCode.getOrDefault(division.getGroupCode(), List.of())
                        .stream()
                        .sorted(Comparator.comparing(OrgGroup::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

                // 본부 직속 멤버 (TEAM 멤버십 → DIVISION 그룹)
                List<UserSearchResponse> divisionDirect = buildMemberList(
                        usersByGroupCode.getOrDefault(division.getGroupCode(), List.of()),
                        division.getDisplayName(), jobLevelByUserId, jobPositionByUserId, jobTitleByUserId);

                List<OrgTeamResponse> teamsResponse = teams.stream().map(team -> {
                    List<UserSearchResponse> members = buildMemberList(
                            usersByGroupCode.getOrDefault(team.getGroupCode(), List.of()),
                            team.getDisplayName(), jobLevelByUserId, jobPositionByUserId, jobTitleByUserId);
                    return new OrgTeamResponse(team.getDisplayName(), members);
                }).toList();

                divisionsResponse.add(new OrgDivisionResponse(division.getDisplayName(), divisionDirect, teamsResponse));
            }

            companiesResponse.add(new OrgCompanyResponse(company.getDisplayName(), companyDirect, divisionsResponse));
        }

        return new OrganizationTreeResponse(companiesResponse);
    }

    /** 사용자 목록 → UserSearchResponse 변환 + 이름 가나다 정렬 */
    private List<UserSearchResponse> buildMemberList(
            List<User> users,
            String groupDisplayName,
            Map<Long, String> jobLevelMap,
            Map<Long, String> jobPositionMap,
            Map<Long, String> jobTitleMap
    ) {
        return users.stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> toTreeSearchResponse(
                        u, groupDisplayName,
                        jobLevelMap.get(u.getId()),
                        jobPositionMap.get(u.getId()),
                        jobTitleMap.get(u.getId())))
                .toList();
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
                user.getStatus(),
                user.getCreatedAt()
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
