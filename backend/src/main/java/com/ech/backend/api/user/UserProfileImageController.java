package com.ech.backend.api.user;

import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.rbac.AppRole;
import com.ech.backend.common.rbac.RequireRole;
import com.ech.backend.common.security.UserPrincipal;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequireRole(AppRole.MEMBER)
public class UserProfileImageController {

    private final UserRepository userRepository;
    private final UserProfileImageService userProfileImageService;

    @Value("${app.allow-user-profile-self-upload:true}")
    private boolean allowUserProfileSelfUpload;

    public UserProfileImageController(UserRepository userRepository, UserProfileImageService userProfileImageService) {
        this.userRepository = userRepository;
        this.userProfileImageService = userProfileImageService;
    }

    /**
     * 프로필 이미지 바이너리. JWT 필요(브라우저 img src 대신 blob URL 로 로드).
     */
    @GetMapping(value = "/profile-image", params = "employeeNo")
    public ResponseEntity<Resource> getProfileImage(@RequestParam String employeeNo) {
        String emp = employeeNo.trim();
        User user = userRepository.findByEmployeeNo(emp)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Resource resource = userProfileImageService.loadAsResource(user);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            java.nio.file.Path p = resource.getFile().toPath();
            String ct = userProfileImageService.probeContentType(p);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, ct)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 본인 프로필 사진 업로드(설정으로 비활성화 가능).
     */
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadOwnProfileImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        if (!allowUserProfileSelfUpload) {
            throw new ForbiddenException("본인 프로필 사진 변경이 비활성화되어 있습니다.");
        }
        User user = userRepository.findByEmployeeNo(principal.employeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        userProfileImageService.saveProfileImage(user, file);
        return ApiResponse.success("OK");
    }
}
