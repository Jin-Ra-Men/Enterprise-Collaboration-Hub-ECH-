package com.ech.backend.api.user;

import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자 프로필 이미지 저장. 단일 하위 디렉터리 {@value #USER_PROFILE_SUBDIR} 에
 * {@code {sanitizedEmployeeNo}.{ext}} 형태로 둔다.
 */
@Service
public class UserProfileImageService {

    public static final String USER_PROFILE_SUBDIR = "user-profiles";

    private static final long MAX_BYTES = 3L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UserRepository userRepository;
    private final AppSettingsService appSettingsService;

    public UserProfileImageService(UserRepository userRepository, AppSettingsService appSettingsService) {
        this.userRepository = userRepository;
        this.appSettingsService = appSettingsService;
    }

    public boolean hasProfileImage(User user) {
        return user.getProfileImageRelPath() != null && !user.getProfileImageRelPath().isBlank();
    }

    public Path resolveAbsolutePath(String relativePath) {
        String base = appSettingsService.getFileStorageDir();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("파일 저장 경로가 설정되지 않았습니다.");
        }
        Path root = Paths.get(base).normalize();
        Path full = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!full.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 프로필 이미지 경로입니다.");
        }
        return full;
    }

    /**
     * 사원번호가 바뀌면 디스크 파일명·DB 경로를 함께 맞춘다.
     */
    @Transactional
    public void renameStorageIfNeeded(String oldEmployeeNo, String newEmployeeNo, User user) {
        if (oldEmployeeNo == null || newEmployeeNo == null) {
            return;
        }
        String oldEmp = oldEmployeeNo.trim();
        String newEmp = newEmployeeNo.trim();
        if (oldEmp.equals(newEmp)) {
            return;
        }
        String rel = user.getProfileImageRelPath();
        if (rel == null || rel.isBlank()) {
            return;
        }
        if (!rel.startsWith(USER_PROFILE_SUBDIR + "/")) {
            return;
        }
        String ext = extensionFromRelPath(rel);
        if (ext == null) {
            return;
        }
        String newRel = buildRelativePath(newEmp, ext);
        Path oldAbs = resolveAbsolutePath(rel);
        Path newAbs = resolveAbsolutePath(newRel);
        try {
            if (Files.isRegularFile(oldAbs)) {
                Files.createDirectories(newAbs.getParent());
                Files.move(oldAbs, newAbs, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("프로필 이미지 파일명을 사원번호 변경에 맞게 옮기지 못했습니다.", e);
        }
        user.setProfileImageRelPath(newRel.replace('\\', '/'));
    }

    @Transactional
    public User saveProfileImage(User user, MultipartFile file) throws IOException {
        validateFile(file);
        String ext = inferExtension(file);
        if (ext == null) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }
        String emp = user.getEmployeeNo();
        if (emp == null || emp.isBlank()) {
            throw new IllegalArgumentException("사원번호가 없습니다.");
        }
        String newRel = buildRelativePath(emp.trim(), ext);
        Path oldAbs = null;
        String prev = user.getProfileImageRelPath();
        if (prev != null && !prev.isBlank() && !prev.equals(newRel)) {
            try {
                oldAbs = resolveAbsolutePath(prev);
            } catch (RuntimeException ignored) {
                oldAbs = null;
            }
        }

        String base = appSettingsService.getFileStorageDir();
        Path dir = Paths.get(base, USER_PROFILE_SUBDIR).normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(sanitizeEmployeeNo(emp.trim()) + "." + ext).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("잘못된 저장 경로입니다.");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        if (oldAbs != null && Files.isRegularFile(oldAbs) && !oldAbs.equals(target)) {
            Files.deleteIfExists(oldAbs);
        }

        user.setProfileImageRelPath(newRel.replace('\\', '/'));
        return userRepository.save(user);
    }

    @Transactional
    public void deleteProfileImageFile(User user) {
        String rel = user.getProfileImageRelPath();
        if (rel == null || rel.isBlank()) {
            return;
        }
        try {
            Path p = resolveAbsolutePath(rel);
            Files.deleteIfExists(p);
        } catch (IOException e) {
            // 로그만 — DB 정리는 호출부에서
        }
        user.setProfileImageRelPath(null);
        userRepository.save(user);
    }

    /** 사용자 행 삭제 전 디스크 파일만 제거한다(DB는 호출부에서 삭제). */
    public void deleteStoredFileOnly(User user) {
        String rel = user.getProfileImageRelPath();
        if (rel == null || rel.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveAbsolutePath(rel));
        } catch (IOException ignored) {
            // ignore
        }
    }

    public Resource loadAsResource(User user) {
        String rel = user.getProfileImageRelPath();
        if (rel == null || rel.isBlank()) {
            return null;
        }
        Path p = resolveAbsolutePath(rel);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        return new FileSystemResource(p);
    }

    public String probeContentType(Path path) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException ignored) {
            // fall through
        }
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public static String sanitizeEmployeeNo(String employeeNo) {
        String s = employeeNo.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        if (out.isEmpty()) {
            return "user";
        }
        return out;
    }

    public static String buildRelativePath(String employeeNo, String extLower) {
        return USER_PROFILE_SUBDIR + "/" + sanitizeEmployeeNo(employeeNo) + "." + extLower.toLowerCase(Locale.ROOT);
    }

    private static String extensionFromRelPath(String rel) {
        int dot = rel.lastIndexOf('.');
        if (dot < 0 || dot >= rel.length() - 1) {
            return null;
        }
        return rel.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("프로필 이미지는 3MB 이하여야 합니다.");
        }
    }

    private static String inferExtension(MultipartFile file) {
        String orig = file.getOriginalFilename();
        String fromName = null;
        if (orig != null) {
            int dot = orig.lastIndexOf('.');
            if (dot >= 0 && dot < orig.length() - 1) {
                fromName = orig.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        String ct = file.getContentType();
        if (fromName != null && ALLOWED_EXT.contains(fromName)) {
            return normalizeExt(fromName);
        }
        if (ct != null) {
            String c = ct.toLowerCase(Locale.ROOT);
            if (c.contains("jpeg") || c.contains("jpg")) {
                return "jpg";
            }
            if (c.contains("png")) {
                return "png";
            }
            if (c.contains("webp")) {
                return "webp";
            }
            if (c.contains("gif")) {
                return "gif";
            }
        }
        if (fromName != null) {
            return normalizeExt(fromName);
        }
        return null;
    }

    private static String normalizeExt(String ext) {
        String e = ext.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(e)) {
            return "jpg";
        }
        return ALLOWED_EXT.contains(e) ? e : null;
    }
}
