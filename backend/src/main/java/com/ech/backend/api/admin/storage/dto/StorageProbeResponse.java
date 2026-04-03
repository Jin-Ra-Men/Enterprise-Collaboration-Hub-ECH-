package com.ech.backend.api.admin.storage.dto;

/**
 * 관리자용 첨부 저장소 접근 진단 응답.
 */
public record StorageProbeResponse(
        String resolvedPath,
        boolean writable,
        boolean uncPath,
        String detail
) {
}
