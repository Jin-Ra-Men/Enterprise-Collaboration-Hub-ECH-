package com.ech.backend.api.file;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.ChannelLibraryFolderResponse;
import com.ech.backend.api.file.dto.CreateChannelLibraryFolderRequest;
import com.ech.backend.api.file.dto.RenameChannelLibraryFolderRequest;
import com.ech.backend.api.file.dto.UpdateChannelFileLibraryRequest;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFile;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.file.ChannelLibraryFolder;
import com.ech.backend.domain.file.ChannelLibraryFolderRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChannelLibraryService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelLibraryFolderRepository folderRepository;
    private final ChannelFileRepository channelFileRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ChannelFileService channelFileService;

    public ChannelLibraryService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelLibraryFolderRepository folderRepository,
            ChannelFileRepository channelFileRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ChannelFileService channelFileService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.folderRepository = folderRepository;
        this.channelFileRepository = channelFileRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.channelFileService = channelFileService;
    }

    public List<ChannelLibraryFolderResponse> listFolders(Long channelId, String employeeNo) {
        requireMember(channelId, employeeNo);
        return folderRepository.findByChannel_IdOrderBySortOrderAscNameAsc(channelId).stream()
                .map(this::toFolderResponse)
                .toList();
    }

    @Transactional
    public ChannelLibraryFolderResponse createFolder(Long channelId, String employeeNo,
                                                      CreateChannelLibraryFolderRequest request) {
        User actor = requireMember(channelId, employeeNo);
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        String name = sanitizeFolderName(request.name());
        int nextOrder = folderRepository.findByChannel_IdOrderBySortOrderAscNameAsc(channelId).stream()
                .mapToInt(ChannelLibraryFolder::getSortOrder)
                .max()
                .orElse(-1) + 1;
        ChannelLibraryFolder saved = folderRepository.save(new ChannelLibraryFolder(channel, name, nextOrder));
        auditLogService.safeRecord(
                AuditEventType.CHANNEL_LIBRARY_FOLDER_CREATED,
                actor.getId(),
                "CHANNEL_LIBRARY_FOLDER",
                saved.getId(),
                channel.getWorkspaceKey(),
                "channelId=" + channelId + " name=" + name,
                null
        );
        return toFolderResponse(saved);
    }

    @Transactional
    public ChannelLibraryFolderResponse renameFolder(Long channelId, Long folderId, String employeeNo,
                                                     RenameChannelLibraryFolderRequest request) {
        User actor = requireMember(channelId, employeeNo);
        ChannelLibraryFolder folder = folderRepository.findByIdAndChannel_Id(folderId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        folder.rename(request.name());
        auditLogService.safeRecord(
                AuditEventType.CHANNEL_LIBRARY_FOLDER_UPDATED,
                actor.getId(),
                "CHANNEL_LIBRARY_FOLDER",
                folderId,
                folder.getChannel().getWorkspaceKey(),
                "channelId=" + channelId + " rename=" + sanitizeFolderName(request.name()),
                null
        );
        return toFolderResponse(folder);
    }

    @Transactional
    public void deleteFolder(Long channelId, Long folderId, String employeeNo) {
        User actor = requireMember(channelId, employeeNo);
        ChannelLibraryFolder folder = folderRepository.findByIdAndChannel_Id(folderId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        channelFileRepository.detachFilesFromLibraryFolder(folderId);
        folderRepository.delete(folder);
        auditLogService.safeRecord(
                AuditEventType.CHANNEL_LIBRARY_FOLDER_DELETED,
                actor.getId(),
                "CHANNEL_LIBRARY_FOLDER",
                folderId,
                folder.getChannel().getWorkspaceKey(),
                "channelId=" + channelId,
                null
        );
    }

    @Transactional
    public ChannelFileResponse updateFileLibrary(Long channelId, Long fileId, String employeeNo,
                                                 UpdateChannelFileLibraryRequest request) {
        User actor = requireMember(channelId, employeeNo);
        ChannelFile file = channelFileRepository.findByIdAndChannel_Id(fileId, channelId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));

        if (Boolean.TRUE.equals(request.detachFolder())) {
            file.setLibraryFolder(null);
        } else if (request.folderId() != null) {
            ChannelLibraryFolder folder = folderRepository.findByIdAndChannel_Id(request.folderId(), channelId)
                    .orElseThrow(() -> new IllegalArgumentException("자료실 폴더를 찾을 수 없습니다."));
            file.setLibraryFolder(folder);
        }

        if (request.pinned() != null) {
            file.setLibraryPinned(request.pinned());
        }
        if (request.caption() != null) {
            String c = request.caption().trim();
            file.setLibraryCaption(c.isEmpty() ? null : c);
        }
        if (request.tags() != null) {
            String t = request.tags().trim();
            file.setLibraryTags(t.isEmpty() ? null : t);
        }

        channelFileRepository.save(file);

        auditLogService.safeRecord(
                AuditEventType.CHANNEL_FILE_LIBRARY_UPDATED,
                actor.getId(),
                "FILE",
                fileId,
                file.getChannel().getWorkspaceKey(),
                "channelId=" + channelId + " library meta",
                null
        );

        ChannelFile refreshed = channelFileRepository.findByIdAndChannel_Id(fileId, channelId)
                .orElseThrow(() -> new IllegalStateException("파일 갱신 후 조회 실패"));
        return channelFileService.toChannelFileResponse(refreshed);
    }

    private ChannelLibraryFolderResponse toFolderResponse(ChannelLibraryFolder f) {
        return new ChannelLibraryFolderResponse(
                f.getId(),
                f.getChannel().getId(),
                f.getName(),
                f.getSortOrder(),
                f.getCreatedAt()
        );
    }

    private User requireMember(Long channelId, String employeeNo) {
        channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, employeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 접근할 수 있습니다.");
        }
        return user;
    }

    private static String sanitizeFolderName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("폴더 이름이 비어 있습니다.");
        }
        String s = raw.trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
