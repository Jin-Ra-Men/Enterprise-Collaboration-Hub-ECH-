package com.ech.backend.api.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChannelLibraryFolderRequest(
        @NotBlank @Size(max = 200) String name
) {
}
