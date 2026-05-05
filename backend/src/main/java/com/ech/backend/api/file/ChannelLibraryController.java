package com.ech.backend.api.file;

import com.ech.backend.api.file.dto.ChannelFileResponse;
import com.ech.backend.api.file.dto.ChannelLibraryFolderResponse;
import com.ech.backend.api.file.dto.CreateChannelLibraryFolderRequest;
import com.ech.backend.api.file.dto.RenameChannelLibraryFolderRequest;
import com.ech.backend.api.file.dto.UpdateChannelFileLibraryRequest;
import com.ech.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels/{channelId}/library")
public class ChannelLibraryController {

    private final ChannelLibraryService channelLibraryService;

    public ChannelLibraryController(ChannelLibraryService channelLibraryService) {
        this.channelLibraryService = channelLibraryService;
    }

    @GetMapping("/folders")
    public ApiResponse<List<ChannelLibraryFolderResponse>> listFolders(
            @PathVariable Long channelId,
            @RequestParam String employeeNo
    ) {
        return ApiResponse.success(channelLibraryService.listFolders(channelId, employeeNo));
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelLibraryFolderResponse> createFolder(
            @PathVariable Long channelId,
            @RequestParam String employeeNo,
            @Valid @RequestBody CreateChannelLibraryFolderRequest body
    ) {
        return ApiResponse.success(channelLibraryService.createFolder(channelId, employeeNo, body));
    }

    @PatchMapping("/folders/{folderId}")
    public ApiResponse<ChannelLibraryFolderResponse> renameFolder(
            @PathVariable Long channelId,
            @PathVariable Long folderId,
            @RequestParam String employeeNo,
            @Valid @RequestBody RenameChannelLibraryFolderRequest body
    ) {
        return ApiResponse.success(channelLibraryService.renameFolder(channelId, folderId, employeeNo, body));
    }

    @DeleteMapping("/folders/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(
            @PathVariable Long channelId,
            @PathVariable Long folderId,
            @RequestParam String employeeNo
    ) {
        channelLibraryService.deleteFolder(channelId, folderId, employeeNo);
    }

    /**
     * 채널 첨부 파일의 자료실 메타(폴더·핀·설명·태그) 갱신.
     */
    @PatchMapping("/files/{fileId}")
    public ApiResponse<ChannelFileResponse> updateFileLibrary(
            @PathVariable Long channelId,
            @PathVariable Long fileId,
            @RequestParam String employeeNo,
            @Valid @RequestBody UpdateChannelFileLibraryRequest body
    ) {
        return ApiResponse.success(channelLibraryService.updateFileLibrary(channelId, fileId, employeeNo, body));
    }
}
