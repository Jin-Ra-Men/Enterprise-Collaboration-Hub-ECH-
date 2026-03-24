package com.ech.backend.api.file.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChannelFileMetadataRequest(
        @NotNull Long uploadedByUserId,
        @NotBlank @Size(max = 500) String originalFilename,
        @NotBlank @Size(max = 255) String contentType,
        @NotNull @Min(1) @Max(536_870_912L) Long sizeBytes,
        @NotBlank @Size(max = 1024) String storageKey
) {
}
