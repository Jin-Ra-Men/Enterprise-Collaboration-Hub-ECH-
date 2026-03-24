package com.ech.backend.common.api;

public record ApiError(
        String code,
        String message
) {
}
