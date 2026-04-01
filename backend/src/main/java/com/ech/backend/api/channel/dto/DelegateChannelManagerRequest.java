package com.ech.backend.api.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DelegateChannelManagerRequest(
        @NotBlank @Size(max = 50) String targetEmployeeNo
) {
}
