package com.ech.backend.api.file;

import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.CreateChannelFileMetadataRequest;
import com.ech.backend.api.file.dto.FileDownloadInfoResponse;
import com.ech.backend.api.file.dto.FileUploadPolicyResponse;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(channelFileService.listFiles(channelId, employeeNo));
    }

    @GetMapping("/upload-policy")
    public ApiResponse<FileUploadPolicyResponse> uploadPolicy() {
        return ApiResponse.success(channelFileService.getUploadPolicy());
    }

    /**
     * 실제 파일을 업로드한다 (multipart/form-data).
     * 파일은 스토리지 경로에 channels/{workspaceKey}_ch{channelId}_{nameSlug}/{YYYY}/{MM}/{UUID}_{filename} 구조로 저장된다.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelFileResponse> upload(
            @PathVariable Long channelId,
            @RequestParam String employeeNo,
            @RequestParam MultipartFile file,
            @RequestParam(value = "preview", required = false) MultipartFile preview,
            @RequestParam(required = false) Long parentMessageId,
            @RequestParam(required = false) String threadKind
    ) throws IOException {
        return ApiResponse.success(
                channelFileService.uploadFile(channelId, employeeNo, file, preview, parentMessageId, threadKind));
    }

    /**
     * 파일을 실제로 다운로드한다. {@code variant=original}(기본) | {@code variant=preview}(미리보기·압축본).
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long channelId,
            @PathVariable Long fileId,
            @RequestParam String employeeNo,
            @RequestParam(required = false, defaultValue = "original") String variant
    ) throws IOException {
        return channelFileService.downloadFile(channelId, fileId, employeeNo, variant);
    }

    /** 썸네일·인라인 이미지(미리보기가 있으면 그 파일, 없으면 원본). */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable Long channelId,
            @PathVariable Long fileId,
            @RequestParam String employeeNo
    ) throws IOException {
        return channelFileService.servePreview(channelId, fileId, employeeNo);
    }

    /** 메타데이터만 등록 (하위 호환용). */
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
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(channelFileService.getDownloadInfo(channelId, fileId, employeeNo));
    }
}
