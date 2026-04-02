package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 채널/DM 진입 시 최신 루트 메시지까지 읽음 처리(미읽음 배지 초기화) 요청. */
public record MarkChannelReadCaughtUpRequest(
        @NotBlank @Size(max = 50) String employeeNo
) {
}
