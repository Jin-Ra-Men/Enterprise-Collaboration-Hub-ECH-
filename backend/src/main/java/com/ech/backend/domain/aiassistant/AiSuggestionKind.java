package com.ech.backend.domain.aiassistant;

/** Kind of queued suggestion (payload_json interpreted per kind). */
public enum AiSuggestionKind {
    GENERIC,
    WORK_ITEM_HINT,
    CALENDAR_HINT,
    /** Scheduled digest batch row (dedupe per calendar day in Seoul). */
    DIGEST_SUMMARY
}
