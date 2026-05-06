package com.ech.backend.api.aigateway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic PII masking applied inside the AI gateway before any outbound LLM call (future).
 * Conservative patterns only — does not guarantee completeness (legal DPIA still applies).
 */
public final class AiGatewayPiiMasker {

    /** YYMMDD + hyphen + gender digit + 6 digits (Korea resident registration-style fragment). */
    private static final Pattern RRN_LIKE = Pattern.compile("\\d{6}\\s*-\\s*[1-4]\\d{6}");

    /** 16-digit PAN-style grouping with separator between each quad (space or hyphen). */
    private static final Pattern CARD_16 = Pattern.compile("\\d{4}(?:[\\s-]\\d{4}){3}");

    private AiGatewayPiiMasker() {
    }

    public record MaskResult(String maskedText, int redactionCount) {
    }

    public static MaskResult mask(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isEmpty()) {
            return new MaskResult("", 0);
        }
        int count = 0;
        Matcher rrnMatcher = RRN_LIKE.matcher(rawPrompt);
        StringBuffer stage = new StringBuffer();
        while (rrnMatcher.find()) {
            count++;
            rrnMatcher.appendReplacement(stage, Matcher.quoteReplacement("[REDACTED_ID]"));
        }
        rrnMatcher.appendTail(stage);
        String intermediate = stage.toString();

        Matcher cardMatcher = CARD_16.matcher(intermediate);
        stage = new StringBuffer();
        while (cardMatcher.find()) {
            count++;
            cardMatcher.appendReplacement(stage, Matcher.quoteReplacement("[REDACTED_PAYMENT]"));
        }
        cardMatcher.appendTail(stage);
        return new MaskResult(stage.toString(), count);
    }
}
