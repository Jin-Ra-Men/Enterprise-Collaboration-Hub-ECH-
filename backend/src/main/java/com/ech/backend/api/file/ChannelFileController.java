package com.ech.backend.api.file;

import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.CreateChannelFileMetadataRequest;
import com.ech.backend.api.file.dto.FileDownloadInfoResponse;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels/{channelId}/files")
public class ChannelFileController {

    private final ChannelFileService channelFileService;

    public ChannelFileController(ChannelFileService channelFileService) {
        this.channelFileService = channelFileService;
    }

    @GetMapping
    public ApiResponse<List<ChannelFileResponse>> list(
            @PathVariable Long channelId,
            @RequestParam Long userId
    ) {
        return ApiResponse.success(channelFileService.listFiles(channelId, userId));
    }

    @PostMapping
    public ApiResponse<ChannelFileResponse> register(
            @PathVariable Long channelId,
            @Valid @RequestBody CreateChannelFileMetadataRequest request
    ) {
        return ApiResponse.success(channelFileService.registerMetadata(channelId, request));
    }

    @GetMapping("/{fileId}/download-info")
    public ApiResponse<FileDownloadInfoResponse> downloadInfo(
            @PathVariable Long channelId,
            @PathVariable Long fileId,
            @RequestParam Long userId
    ) {
        return ApiResponse.success(channelFileService.getDownloadInfo(channelId, fileId, userId));
    }
}
