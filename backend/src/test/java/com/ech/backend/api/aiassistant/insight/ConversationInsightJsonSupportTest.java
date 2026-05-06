package com.ech.backend.api.aiassistant.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConversationInsightJsonSupportTest {

    @Test
    void stripCodeFence_removesFence() {
        assertThat(ConversationInsightJsonSupport.stripCodeFence("```json\n{\"a\":1}\n```"))
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void parseLlmJson_parsesCalendarExample() {
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 17, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime end = start.plusHours(1);
        String raw =
                "{\"suggestionKind\":\"CALENDAR\",\"confidence\":0.9,\"ambiguous\":false,"
                        + "\"title\":\"회의\",\"description\":null,"
                        + "\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end + "\",\"workflowReason\":null}";
        Optional<ConversationInsightLlmResult> parsed = ConversationInsightJsonSupport.parseLlmJson(raw);
        assertThat(parsed).isPresent();
        ConversationInsightLlmResult r = parsed.get();
        assertThat(r.kind()).isEqualTo(ConversationInsightKind.CALENDAR);
        assertThat(r.confidence()).isEqualTo(0.9);
        assertThat(r.ambiguous()).isFalse();
        assertThat(r.startsAt()).isEqualTo(start);
        assertThat(r.endsAt()).isEqualTo(end);
    }

    @Test
    void parseLlmJson_noneOnInvalid() {
        assertThat(ConversationInsightJsonSupport.parseLlmJson("")).isEmpty();
        assertThat(ConversationInsightJsonSupport.parseLlmJson("not json")).isEmpty();
    }
}
