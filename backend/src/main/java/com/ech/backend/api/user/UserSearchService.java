package com.ech.backend.api.user;

import com.ech.backend.api.user.dto.UserSearchResponse;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
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

        return userRepository.searchUsers(normalizedKeyword, normalizedDepartment).stream()
                .map(user -> new UserSearchResponse(
                        user.getId(),
                        user.getEmployeeNo(),
                        user.getName(),
                        user.getEmail(),
                        user.getDepartment(),
                        user.getRole(),
                        user.getStatus()
                ))
                .toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
