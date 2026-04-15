package com.ech.backend.api.file.dto;

public record FileUploadPolicyResponse(
        long maxFileSizeBytes,
        long maxFileSizeMb
) {
}
