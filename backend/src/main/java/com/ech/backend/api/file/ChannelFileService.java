package com.ech.backend.api.file;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.CreateChannelFileMetadataRequest;
import com.ech.backend.api.file.dto.FileDownloadInfoResponse;
import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFile;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.settings.AppSettingKey;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ChannelFileService {

    private static final int LIST_PAGE_SIZE = 100;

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final ChannelFileRepository channelFileRepository;
    private final AuditLogService auditLogService;
    private final AppSettingsService appSettingsService;

    public ChannelFileService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            ChannelFileRepository channelFileRepository,
            AuditLogService auditLogService,
            AppSettingsService appSettingsService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.channelFileRepository = channelFileRepository;
        this.auditLogService = auditLogService;
        this.appSettingsService = appSettingsService;
    }

    public List<ChannelFileResponse> listFiles(Long channelId, Long requesterUserId) {
        requireChannelMember(channelId, requesterUserId);
        return channelFileRepository
                .findByChannel_IdOrderByCreatedAtDesc(channelId, PageRequest.of(0, LIST_PAGE_SIZE))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 실제 파일을 디스크에 저장하고 메타데이터를 DB에 등록한다.
     *
     * <p>저장 경로 구조:
     * <pre>{basedir}/channels/{workspaceKey}_ch{channelId}_{nameSlug}/{YYYY}/{MM}/{UUID}_{sanitizedFilename}</pre>
     *
     * <p>저장 경로는 DB의 {@code file.storage.base-dir} 설정 값을 우선 사용하며,
     * 없을 경우 {@code application.yml}의 {@code app.file-storage-dir} 기본값을 사용한다.
     */
    @Transactional
    public ChannelFileResponse uploadFile(Long channelId, Long uploaderUserId,
                                          MultipartFile file) throws IOException {
        validateFileSize(file);

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User uploader = userRepository.findById(uploaderUserId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        requireChannelMember(channelId, uploaderUserId);

        String originalName = sanitizeOriginalFilename(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");

        // 저장 경로: {basedir}/channels/{workspaceKey}_ch{id}_{slug}/{YYYY}/{MM}/
        String baseDir = appSettingsService.getFileStorageDir();
        LocalDate now = LocalDate.now();
        String channelFolder = buildChannelFolderSegment(channel);
        Path dirPath = Paths.get(baseDir, "channels", channelFolder,
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()));
        Files.createDirectories(dirPath);

        // 파일명: {UUID}_{originalFilename} — 중복 방지
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + originalName;
        Path targetPath = dirPath.resolve(storedName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // storageKey: 베이스 디렉토리를 제외한 상대 경로
        String relativeKey = Paths.get("channels", channelFolder,
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()),
                storedName).toString().replace('\\', '/');

        ChannelFile saved = channelFileRepository.save(new ChannelFile(
                channel, uploader, originalName,
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize(), relativeKey
        ));

        auditLogService.safeRecord(
                AuditEventType.FILE_UPLOADED,
                uploaderUserId, "FILE", saved.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " filename=" + originalName + " size=" + file.getSize(),
                null
        );
        return toResponse(saved);
    }

    /**
     * 파일을 실제로 다운로드한다.
     * 저장된 storageKey를 이용해 현재 설정된 basedir에서 파일을 찾는다.
     */
    public ResponseEntity<Resource> downloadFile(Long channelId, Long fileId,
                                                  Long requesterUserId) throws IOException {
        requireChannelMember(channelId, requesterUserId);
        ChannelFile meta = channelFileRepository.findByIdAndChannel_Id(fileId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));

        String baseDir = appSettingsService.getFileStorageDir();
        Path basePath = Paths.get(baseDir).normalize().toAbsolutePath();
        Path filePath = basePath.resolve(meta.getStorageKey().replace('/', File.separatorChar)).normalize();

        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("잘못된 스토리지 경로입니다.");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new NotFoundException("파일이 스토리지에 존재하지 않습니다: " + meta.getStorageKey());
        }

        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(meta.getOriginalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        MediaType mediaType = resolveDownloadMediaType(meta.getContentType());

        auditLogService.safeRecord(
                AuditEventType.FILE_DOWNLOAD_INFO_ACCESSED,
                requesterUserId, "FILE", fileId, null,
                "channelId=" + channelId + " fileId=" + fileId, null
        );

        long contentLen = Files.size(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(mediaType)
                .contentLength(contentLen)
                .body(resource);
    }

    private static MediaType resolveDownloadMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(raw.trim());
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public FileDownloadInfoResponse getDownloadInfo(Long channelId, Long fileId,
                                                     Long requesterUserId) {
        requireChannelMember(channelId, requesterUserId);
        ChannelFile file = channelFileRepository
                .findByIdAndChannel_Id(fileId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        auditLogService.safeRecord(
                AuditEventType.FILE_DOWNLOAD_INFO_ACCESSED,
                requesterUserId, "FILE", file.getId(), null,
                "channelId=" + channelId + " fileId=" + fileId, null
        );
        String baseDir = appSettingsService.getFileStorageDir();
        return new FileDownloadInfoResponse(
                file.getId(), file.getOriginalFilename(), file.getContentType(),
                file.getSizeBytes(), file.getStorageKey(),
                "저장 경로: " + baseDir + " | 상대 키: " + file.getStorageKey()
        );
    }

    @Transactional
    public ChannelFileResponse registerMetadata(Long channelId,
                                                 CreateChannelFileMetadataRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User uploader = userRepository.findById(request.uploadedByUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        requireChannelMember(channelId, request.uploadedByUserId());

        String safeName = sanitizeOriginalFilename(request.originalFilename());
        String safeKey = request.storageKey().trim();
        if (safeKey.isEmpty()) {
            throw new IllegalArgumentException("storageKey가 비어 있습니다.");
        }

        ChannelFile saved = channelFileRepository.save(new ChannelFile(
                channel, uploader, safeName,
                request.contentType().trim(), request.sizeBytes(),
                safeKey.length() > 1024 ? safeKey.substring(0, 1024) : safeKey
        ));
        auditLogService.safeRecord(
                AuditEventType.FILE_UPLOADED,
                uploader.getId(), "FILE", saved.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " filename=" + safeName, null
        );
        return toResponse(saved);
    }

    // ── private helpers ──────────────────────────────────────────

    private void validateFileSize(MultipartFile file) {
        String maxMbStr = appSettingsService.get(AppSettingKey.FILE_MAX_SIZE_MB, "100");
        long maxBytes;
        try {
            maxBytes = Long.parseLong(maxMbStr.trim()) * 1024L * 1024L;
        } catch (NumberFormatException e) {
            maxBytes = 100L * 1024 * 1024;
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "파일 크기 초과. 최대 허용: " + maxMbStr + "MB, 현재: "
                            + (file.getSize() / 1024 / 1024) + "MB");
        }
    }

    private void requireChannelMember(Long channelId, Long userId) {
        channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new IllegalArgumentException("채널 멤버만 파일에 접근할 수 있습니다.");
        }
    }

    private static String sanitizeOriginalFilename(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("파일명이 비어 있습니다.");
        String normalized = trimmed.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (base.contains("..") || base.isEmpty()) {
            throw new IllegalArgumentException("유효하지 않은 파일명입니다.");
        }
        // 경로 특수문자 제거
        base = base.replaceAll("[<>:\"/|?*]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    private ChannelFileResponse toResponse(ChannelFile file) {
        return new ChannelFileResponse(
                file.getId(), file.getChannel().getId(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getName(),
                file.getOriginalFilename(),
                file.getContentType(), file.getSizeBytes(),
                file.getStorageKey(), file.getCreatedAt()
        );
    }

    /**
     * 디스크 폴더명에 워크스페이스·채널 ID·채널명 슬러그를 넣어 탐색기에서도 채널을 식별하기 쉽게 한다.
     */
    private static String buildChannelFolderSegment(Channel channel) {
        String ws = channel.getWorkspaceKey() != null ? channel.getWorkspaceKey().trim() : "WS";
        ws = ws.replaceAll("[<>:\"/\\\\|?*\\s]", "_");
        if (ws.length() > 32) {
            ws = ws.substring(0, 32);
        }
        if (ws.isEmpty()) {
            ws = "WS";
        }
        String slug = slugifyChannelName(channel.getName());
        return ws + "_ch" + channel.getId() + "_" + slug;
    }

    private static String slugifyChannelName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String s = name.trim().replaceAll("[<>:\"/\\\\|?*]", "_").replaceAll("\\s+", "_");
        if (s.isEmpty()) {
            return "unnamed";
        }
        return s.length() > 48 ? s.substring(0, 48) : s;
    }
}
