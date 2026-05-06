package com.ech.backend.api.aiassistant.insight;

import java.time.OffsetDateTime;

/**
 * Parsed LLM JSON for proactive conversation insight (calendar vs workflow).
 *
 * @param kind classified intent
 * @param confidence model self-score 0..1
 * @param ambiguous when true, server must not auto-create suggestions
 * @param title short title for calendar or inbox hint
 * @param description optional detail
 * @param startsAt resolved start (Asia/Seoul interpretation done by model as ISO +09:00)
 * @param endsAt resolved end
 * @param workflowReason short rationale for WORKFLOW
 */
public record ConversationInsightLlmResult(
        ConversationInsightKind kind,
        double confidence,
        boolean ambiguous,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String workflowReason
) {
}
