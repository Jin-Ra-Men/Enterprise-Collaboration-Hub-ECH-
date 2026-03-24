package com.ech.backend.api.file;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.CreateChannelFileMetadataRequest;
import com.ech.backend.api.file.dto.FileDownloadInfoResponse;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFile;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChannelFileService {

    private static final int LIST_PAGE_SIZE = 100;
    private static final String DOWNLOAD_HINT =
            "현재는 스토리지 키 기준 안내입니다. NAS/S3 연동 시 사전 서명 URL로 교체 예정입니다.";

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final ChannelFileRepository channelFileRepository;
    private final AuditLogService auditLogService;

    public ChannelFileService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            ChannelFileRepository channelFileRepository,
            AuditLogService auditLogService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.channelFileRepository = channelFileRepository;
        this.auditLogService = auditLogService;
    }

    public List<ChannelFileResponse> listFiles(Long channelId, Long requesterUserId) {
        requireChannelMember(channelId, requesterUserId);
        return channelFileRepository
                .findByChannel_IdOrderByCreatedAtDesc(channelId, PageRequest.of(0, LIST_PAGE_SIZE))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FileDownloadInfoResponse getDownloadInfo(Long channelId, Long fileId, Long requesterUserId) {
        requireChannelMember(channelId, requesterUserId);
        ChannelFile file = channelFileRepository
                .findByIdAndChannel_Id(fileId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));
        auditLogService.safeRecord(
                AuditEventType.FILE_DOWNLOAD_INFO_ACCESSED,
                requesterUserId,
                "FILE",
                file.getId(),
                null,
                "channelId=" + channelId + " fileId=" + fileId,
                null
        );

        return new FileDownloadInfoResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStorageKey(),
                DOWNLOAD_HINT
        );
    }

    @Transactional
    public ChannelFileResponse registerMetadata(Long channelId, CreateChannelFileMetadataRequest request) {
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
                channel,
                uploader,
                safeName,
                request.contentType().trim(),
                request.sizeBytes(),
                safeKey.length() > 1024 ? safeKey.substring(0, 1024) : safeKey
        ));

        auditLogService.safeRecord(
                AuditEventType.FILE_UPLOADED,
                uploader.getId(),
                "FILE",
                saved.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " filename=" + safeName,
                null
        );

        return toResponse(saved);
    }

    private void requireChannelMember(Long channelId, Long userId) {
        channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new IllegalArgumentException("채널 멤버만 파일 메타데이터에 접근할 수 있습니다.");
        }
    }

    private static String sanitizeOriginalFilename(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("파일명이 비어 있습니다.");
        }
        String normalized = trimmed.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (base.contains("..") || base.isEmpty()) {
            throw new IllegalArgumentException("유효하지 않은 파일명입니다.");
        }
        return base.length() > 500 ? base.substring(0, 500) : base;
    }

    private ChannelFileResponse toResponse(ChannelFile file) {
        return new ChannelFileResponse(
                file.getId(),
                file.getChannel().getId(),
                file.getUploadedBy().getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStorageKey(),
                file.getCreatedAt()
        );
    }
}
