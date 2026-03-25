package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.DepartmentGroupResponse;
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

    public List<DepartmentGroupResponse> listUsersGroupedByDepartment() {
        List<User> users = userRepository.findActiveUsersForOrganization();
        Map<String, List<User>> grouped = new LinkedHashMap<>();
        for (User u : users) {
            String dept = (u.getDepartment() != null && !u.getDepartment().isBlank())
                    ? u.getDepartment()
                    : "미지정";
            grouped.computeIfAbsent(dept, k -> new ArrayList<>()).add(u);
        }
        return grouped.entrySet().stream()
                .map(e -> new DepartmentGroupResponse(
                        e.getKey(),
                        e.getValue().stream().map(this::toSearchResponse).toList()))
                .toList();
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
