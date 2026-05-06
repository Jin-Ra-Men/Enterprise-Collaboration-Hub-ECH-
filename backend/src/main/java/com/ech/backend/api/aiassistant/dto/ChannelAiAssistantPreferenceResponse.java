package com.ech.backend.api.aiassistant.dto;

/** Channel-level proactive opt-in. DM channels never allow proactive observation (policy). */
public record ChannelAiAssistantPreferenceResponse(
        long channelId,
        boolean proactiveOptIn,
        /** True when channel is DM — UI must not offer proactive toggle. */
        boolean dmProactiveBlocked
) {
}
