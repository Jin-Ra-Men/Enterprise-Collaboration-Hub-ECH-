package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.OrgCompanyResponse;
import com.ech.backend.api.user.dto.OrgDivisionResponse;
import com.ech.backend.api.user.dto.OrganizationTreeResponse;
import com.ech.backend.api.user.dto.OrgTeamResponse;
import com.ech.backend.api.user.dto.UserProfileResponse;
import com.ech.backend.api.user.dto.UserSearchResponse;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSearchResponse> searchUsers(String keyword, String department) {
        String normalizedKeyword = normalize(keyword);
        String normalizedDepartment = normalize(department);
        Long idMatch = parseIdKeyword(normalizedKeyword);

        return userRepository.searchUsers(normalizedKeyword, normalizedDepartment, idMatch).stream()
                .map(this::toSearchResponse)
                .toList();
    }

        /**
         * 회사 → 본부 → 팀(부서) → 사용자.
         * company/division/team 컬럼이 비어 있으면 해당 레벨을 "미지정" 버킷으로 분류한다.
         *
         * @param companyKeyFilter UI 회사 셀렉트 값. null 또는 빈 문자열, {@code ORGROOT} 는 필터 없음(전체).
         *                         그 외 {@code GENERAL}, {@code EXTERNAL}, {@code COVIM365} 등은 users.company_key 와 일치하는 행만.
         */
    public OrganizationTreeResponse getOrganizationTree(String companyKeyFilter) {
        String repoKey = resolveCompanyKeyForRepository(companyKeyFilter);
        List<User> users = userRepository.findActiveUsersForOrganization(repoKey);
        Map<String, Map<String, Map<String, List<User>>>> byCompany = new LinkedHashMap<>();
        for (User u : users) {
            String co = resolveCompany(u);
            String div = resolveDivision(u);
            String team = resolveTeam(u);
            byCompany
                    .computeIfAbsent(co, k -> new LinkedHashMap<>())
                    .computeIfAbsent(div, k -> new LinkedHashMap<>())
                    .computeIfAbsent(team, k -> new ArrayList<>())
                    .add(u);
        }
        List<OrgCompanyResponse> companies = new ArrayList<>();
        for (var coEntry : byCompany.entrySet()) {
            List<OrgDivisionResponse> divisions = new ArrayList<>();
            for (var divEntry : coEntry.getValue().entrySet()) {
                List<OrgTeamResponse> teams = new ArrayList<>();
                for (var teamEntry : divEntry.getValue().entrySet()) {
                    teams.add(new OrgTeamResponse(
                            teamEntry.getKey(),
                            teamEntry.getValue().stream().map(this::toSearchResponse).toList()));
                }
                divisions.add(new OrgDivisionResponse(divEntry.getKey(), teams));
            }
            companies.add(new OrgCompanyResponse(coEntry.getKey(), divisions));
        }
        return new OrganizationTreeResponse(companies);
    }

    /**
     * null / 공백 / ORGROOT → 전체 조회(null). 그 외 대문자 정규화.
     */
    private static String resolveCompanyKeyForRepository(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        String u = t.toUpperCase();
        if ("ORGROOT".equals(u)) {
            return null;
        }
        return u;
    }

    private static String resolveCompany(User u) {
        String c = u.getCompanyName();
        return (c != null && !c.isBlank()) ? c.trim() : "미지정 회사";
    }

    private static String resolveDivision(User u) {
        String d = u.getDivisionName();
        if (d != null && !d.isBlank()) {
            return d.trim();
        }

        return "미지정 본부";
    }

    private static String resolveTeam(User u) {
        String t = u.getTeamName();
        if (t != null && !t.isBlank()) {
            return t.trim();
        }
        return "미지정 팀";
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
