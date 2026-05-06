package com.ech.backend.api.aigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Gateway ingress body. Prompt text stays inside the gateway layer; audit stores lengths and ids only.
 *
 * @param citedMessageIds Optional provenance for hallucination mitigation; requires {@code channelId} when non-empty.
 */
public record AiGatewayChatRequest(
        @NotBlank @Size(max = 64) String purpose,
        /** Optional; when blank the JWT subject employee number is used. */
        @Size(max = 50) String employeeNo,
        /** When set, request is treated as channel-contextual (metadata only in audit). */
        Long channelId,
        @NotBlank @Size(max = 8000) String prompt,
        @Size(max = 20) List<Long> citedMessageIds
) {
    public AiGatewayChatRequest {
        if (citedMessageIds == null) {
            citedMessageIds = List.of();
        }
    }
}
