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
     * 회사 → 본부 → 팀 → 사용자. company/division/team 컬럼이 비어 있으면 기본값·department로 보완한다.
     */
    public OrganizationTreeResponse getOrganizationTree() {
        List<User> users = userRepository.findActiveUsersForOrganization();
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

    private static String resolveCompany(User u) {
        String c = u.getCompanyName();
        return (c != null && !c.isBlank()) ? c.trim() : "ECH 주식회사";
    }

    private static String resolveDivision(User u) {
        String d = u.getDivisionName();
        if (d != null && !d.isBlank()) {
            return d.trim();
        }

        String dept = u.getDepartment();
        if (dept == null || dept.isBlank()) {
            return "미지정 본부";
        }

        String t = dept.trim();
        // 예) "기술본부" / "CS사업본부" / "운영본부" 등 본부 키워드 기반 유추
        int 본부Idx = t.indexOf("본부");
        if (본부Idx >= 0) {
            String candidate = t.substring(0, 본부Idx + 2).trim();
            if (!candidate.isBlank()) return candidate;
        }

        // 예) "본부-팀" 같은 형태가 department에 들어있는 경우 유추
        int dashIdx = t.indexOf('-');
        if (dashIdx > 0) {
            String candidate = t.substring(0, dashIdx).trim();
            if (!candidate.isBlank()) return candidate;
        }

        return "미지정 본부";
    }

    private static String resolveTeam(User u) {
        String t = u.getTeamName();
        if (t != null && !t.isBlank()) {
            return t.trim();
        }

        String dept = u.getDepartment();
        if (dept == null || dept.isBlank()) {
            return "미지정 팀";
        }

        String td = dept.trim();

        // divisionName이 비어 있고 department가 "본부...팀..." 문자열을 함께 갖고 있는 경우
        // 앞부분(본부)을 제외한 나머지를 팀(부서)로 유추한다.
        if (u.getDivisionName() == null || u.getDivisionName().isBlank()) {
            int 본부Idx = td.indexOf("본부");
            if (본부Idx >= 0) {
                String candidateDivision = td.substring(0, 본부Idx + 2).trim();
                String rest = td.substring(본부Idx + 2).trim();
                if (!rest.isBlank() && !rest.equals(candidateDivision)) {
                    // "-" 같은 구분자가 섞여있을 수 있으므로 앞의 구분자 제거
                    while (!rest.isBlank() && (rest.startsWith("-") || rest.startsWith("—") || rest.startsWith("_"))) {
                        rest = rest.substring(1).trim();
                    }
                    if (!rest.isBlank()) return rest;
                }
            }

            int dashIdx = td.indexOf('-');
            if (dashIdx > 0) {
                String rest = td.substring(dashIdx + 1).trim();
                if (!rest.isBlank()) return rest;
            }
        }

        return td;
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
