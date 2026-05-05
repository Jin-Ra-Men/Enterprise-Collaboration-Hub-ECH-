package com.ech.backend.api.file.dto;

import jakarta.validation.constraints.Size;

/**
 * 부분 갱신: null 필드는 변경하지 않습니다. {@code detachFolder == true}이면 폴더 배치만 해제합니다.
 */
public record UpdateChannelFileLibraryRequest(
        Boolean pinned,
        @Size(max = 2000) String caption,
        @Size(max = 500) String tags,
        Long folderId,
        Boolean detachFolder
) {
}
