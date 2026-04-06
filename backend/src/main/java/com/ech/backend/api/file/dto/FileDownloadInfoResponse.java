package com.ech.backend.api.file.dto;

/**
 * 다운로드 정책 1차: 채널 멤버에게 스토리지 키·표시용 경로만 전달.
 * 추후 NAS/S3 사전 서명 URL로 확장.
 */
public record FileDownloadInfoResponse(
        Long fileId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String storageKey,
        String downloadHint,
        boolean hasPreview,
        Long previewSizeBytes
) {
}
