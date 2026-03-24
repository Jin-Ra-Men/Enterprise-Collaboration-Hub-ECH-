package com.ech.backend.api.release;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.release.dto.ActivateReleaseRequest;
import com.ech.backend.api.release.dto.DeploymentHistoryResponse;
import com.ech.backend.api.release.dto.ReleaseVersionResponse;
import com.ech.backend.api.release.dto.RollbackRequest;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.release.DeploymentAction;
import com.ech.backend.domain.release.DeploymentHistory;
import com.ech.backend.domain.release.DeploymentHistoryRepository;
import com.ech.backend.domain.release.ReleaseStatus;
import com.ech.backend.domain.release.ReleaseVersion;
import com.ech.backend.domain.release.ReleaseVersionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

    @Value("${app.releases-dir:./releases}")
    private String releasesDir;

    private final ReleaseVersionRepository releaseVersionRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final AuditLogService auditLogService;

    public ReleaseService(
            ReleaseVersionRepository releaseVersionRepository,
            DeploymentHistoryRepository deploymentHistoryRepository,
            AuditLogService auditLogService
    ) {
        this.releaseVersionRepository = releaseVersionRepository;
        this.deploymentHistoryRepository = deploymentHistoryRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * WAR/JAR 파일을 업로드하고 릴리즈 버전으로 등록한다.
     * <p>파일은 {@code {releasesDir}/{version}/{fileName}} 경로에 저장된다.
     */
    @Transactional
    public ReleaseVersionResponse upload(String version, String description,
                                         Long uploadedBy, MultipartFile file) throws IOException {
        if (releaseVersionRepository.findByVersion(version).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 버전입니다: " + version);
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "release.jar";
        Path versionDir = Paths.get(releasesDir, version);
        Files.createDirectories(versionDir);
        Path targetPath = versionDir.resolve(originalName);

        String checksum = saveFileWithChecksum(file.getInputStream(), targetPath);

        ReleaseVersion release = new ReleaseVersion(
                version, originalName, targetPath.toString(),
                file.getSize(), checksum, description, uploadedBy
        );
        releaseVersionRepository.save(release);

        auditLogService.safeRecord(
                AuditEventType.RELEASE_UPLOADED,
                uploadedBy, "RELEASE", release.getId(), null,
                "version=" + version + " file=" + originalName + " size=" + file.getSize(),
                null
        );
        log.info("[Release] 업로드 완료: v{} ({})", version, originalName);
        return toResponse(release);
    }

    @Transactional(readOnly = true)
    public List<ReleaseVersionResponse> listAll() {
        return releaseVersionRepository.findAllByOrderByUploadedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReleaseVersionResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * 특정 버전을 ACTIVE 상태로 전환한다.
     * 기존 ACTIVE 버전은 PREVIOUS로 변경된다.
     */
    @Transactional
    public ReleaseVersionResponse activate(Long releaseId, ActivateReleaseRequest request) {
        ReleaseVersion target = findOrThrow(releaseId);
        if (target.getStatus() == ReleaseStatus.ACTIVE) {
            throw new IllegalStateException("이미 활성화된 버전입니다: " + target.getVersion());
        }
        if (target.getStatus() == ReleaseStatus.DEPRECATED) {
            throw new IllegalStateException("폐기된 버전은 활성화할 수 없습니다: " + target.getVersion());
        }

        // 현재 ACTIVE 버전을 PREVIOUS로 전환
        String fromVersion = null;
        releaseVersionRepository.findByStatus(ReleaseStatus.ACTIVE).ifPresent(current -> {
            current.markPrevious();
            releaseVersionRepository.save(current);
        });
        // fromVersion 별도 조회 (람다 외부 변수 제약 우회)
        ReleaseVersion prev = releaseVersionRepository.findByStatus(ReleaseStatus.PREVIOUS).orElse(null);
        if (prev != null) {
            fromVersion = prev.getVersion();
        }

        target.activate();
        releaseVersionRepository.save(target);
        writeCurrentVersionMarker(target.getVersion());

        DeploymentHistory history = new DeploymentHistory(
                target, DeploymentAction.ACTIVATED,
                fromVersion, target.getVersion(),
                request.actorUserId(), request.note()
        );
        deploymentHistoryRepository.save(history);

        auditLogService.safeRecord(
                AuditEventType.RELEASE_ACTIVATED,
                request.actorUserId(), "RELEASE", releaseId, null,
                "version=" + target.getVersion() + " from=" + fromVersion,
                null
        );
        log.info("[Release] 버전 활성화: v{} (이전: v{})", target.getVersion(), fromVersion);
        return toResponse(target);
    }

    /**
     * 이전 버전(PREVIOUS)으로 롤백한다.
     */
    @Transactional
    public ReleaseVersionResponse rollback(RollbackRequest request) {
        ReleaseVersion rollbackTarget = releaseVersionRepository
                .findTopByStatusOrderByActivatedAtDesc(ReleaseStatus.PREVIOUS)
                .orElseThrow(() -> new IllegalStateException("롤백 가능한 이전 버전이 없습니다."));

        ReleaseVersion currentActive = releaseVersionRepository
                .findByStatus(ReleaseStatus.ACTIVE).orElse(null);
        String fromVersion = currentActive != null ? currentActive.getVersion() : null;

        if (currentActive != null) {
            currentActive.markPrevious();
            releaseVersionRepository.save(currentActive);
        }

        rollbackTarget.activate();
        releaseVersionRepository.save(rollbackTarget);
        writeCurrentVersionMarker(rollbackTarget.getVersion());

        DeploymentHistory history = new DeploymentHistory(
                rollbackTarget, DeploymentAction.ROLLED_BACK,
                fromVersion, rollbackTarget.getVersion(),
                request.actorUserId(), request.note()
        );
        deploymentHistoryRepository.save(history);

        auditLogService.safeRecord(
                AuditEventType.RELEASE_ROLLED_BACK,
                request.actorUserId(), "RELEASE", rollbackTarget.getId(), null,
                "rollbackTo=" + rollbackTarget.getVersion() + " from=" + fromVersion,
                null
        );
        log.info("[Release] 롤백 실행: v{} -> v{}", fromVersion, rollbackTarget.getVersion());
        return toResponse(rollbackTarget);
    }

    /**
     * UPLOADED 또는 DEPRECATED 상태의 릴리즈 파일을 삭제한다.
     * ACTIVE/PREVIOUS 상태는 삭제 불가.
     */
    @Transactional
    public void delete(Long releaseId, Long actorUserId) throws IOException {
        ReleaseVersion release = findOrThrow(releaseId);
        if (release.getStatus() == ReleaseStatus.ACTIVE
                || release.getStatus() == ReleaseStatus.PREVIOUS) {
            throw new IllegalStateException("운영 중인 버전은 삭제할 수 없습니다: " + release.getVersion());
        }

        Path filePath = Paths.get(release.getFilePath());
        Files.deleteIfExists(filePath);
        // 버전 디렉토리가 비었으면 함께 삭제
        Path versionDir = filePath.getParent();
        if (versionDir != null && Files.isDirectory(versionDir)) {
            try (var stream = Files.list(versionDir)) {
                if (stream.findAny().isEmpty()) {
                    Files.delete(versionDir);
                }
            }
        }

        auditLogService.safeRecord(
                AuditEventType.RELEASE_DELETED,
                actorUserId, "RELEASE", releaseId, null,
                "version=" + release.getVersion(),
                null
        );
        releaseVersionRepository.delete(release);
        log.info("[Release] 삭제 완료: v{}", release.getVersion());
    }

    @Transactional(readOnly = true)
    public List<DeploymentHistoryResponse> getHistory() {
        return deploymentHistoryRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toHistoryResponse).toList();
    }

    // ---- 내부 유틸 ----

    private ReleaseVersion findOrThrow(Long id) {
        return releaseVersionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("릴리즈를 찾을 수 없습니다: " + id));
    }

    /**
     * 파일을 저장하면서 SHA-256 체크섬을 동시에 계산한다.
     */
    private String saveFileWithChecksum(InputStream inputStream, Path targetPath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                Files.copy(dis, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JVM 표준 — 발생하지 않음
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return null;
        }
    }

    /**
     * releases/current-version.txt 에 현재 활성 버전을 기록한다.
     * 외부 배포 스크립트가 이 파일을 참조해 서비스를 재시작할 수 있다.
     */
    private void writeCurrentVersionMarker(String version) {
        try {
            Path marker = Paths.get(releasesDir, "current-version.txt");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, version);
        } catch (IOException e) {
            log.warn("[Release] current-version.txt 기록 실패: {}", e.getMessage());
        }
    }

    private ReleaseVersionResponse toResponse(ReleaseVersion r) {
        return new ReleaseVersionResponse(
                r.getId(), r.getVersion(), r.getFileName(),
                r.getFileSize(), r.getChecksum(),
                r.getStatus().name(), r.getDescription(),
                r.getUploadedBy(), r.getUploadedAt(), r.getActivatedAt()
        );
    }

    private DeploymentHistoryResponse toHistoryResponse(DeploymentHistory h) {
        return new DeploymentHistoryResponse(
                h.getId(), h.getRelease().getId(),
                h.getAction().name(),
                h.getFromVersion(), h.getToVersion(),
                h.getActorUserId(), h.getNote(), h.getCreatedAt()
        );
    }
}
