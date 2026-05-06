package com.ech.backend.api.aiassistant.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses LLM JSON replies for {@link com.ech.backend.api.aiassistant.ProactiveConversationInsightScheduler}.
 */
public final class ConversationInsightJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConversationInsightJsonSupport() {}

    public static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1).trim();
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    public static Optional<ConversationInsightLlmResult> parseLlmJson(String rawBody) {
        String json = stripCodeFence(rawBody);
        if (json.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }

        ConversationInsightKind kind = parseKind(root.path("suggestionKind").asText("NONE"));
        double confidence = root.path("confidence").asDouble(0);
        boolean ambiguous = root.path("ambiguous").asBoolean(false);
        String title = textOrNull(root, "title");
        String description = textOrNull(root, "description");
        String workflowReason = textOrNull(root, "workflowReason");

        OffsetDateTime startsAt = parseInstant(root.path("startsAt").asText(null));
        OffsetDateTime endsAt = parseInstant(root.path("endsAt").asText(null));

        return Optional.of(new ConversationInsightLlmResult(
                kind,
                confidence,
                ambiguous,
                title,
                description,
                startsAt,
                endsAt,
                workflowReason
        ));
    }

    private static String textOrNull(JsonNode root, String field) {
        if (!root.has(field) || root.get(field).isNull()) {
            return null;
        }
        String s = root.get(field).asText("").trim();
        return s.isEmpty() ? null : s;
    }

    static ConversationInsightKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return ConversationInsightKind.NONE;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "CALENDAR", "SCHEDULE", "MEETING" -> ConversationInsightKind.CALENDAR;
            case "WORKFLOW", "TASK", "WORK_ITEM" -> ConversationInsightKind.WORKFLOW;
            default -> ConversationInsightKind.NONE;
        };
    }

    private static OffsetDateTime parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Truncates to at most {@code maxCodePoints} code points; uses ellipsis as final code point when trimmed.
     */
    public static String truncateToMaxCodePoints(String text, int maxCodePoints) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        if (maxCodePoints <= 0) {
            return "";
        }
        int totalCp = text.codePointCount(0, text.length());
        if (totalCp <= maxCodePoints) {
            return text;
        }
        int keep = Math.max(1, maxCodePoints - 1);
        int endIndex = text.offsetByCodePoints(0, keep);
        return text.substring(0, endIndex) + "…";
    }
}
