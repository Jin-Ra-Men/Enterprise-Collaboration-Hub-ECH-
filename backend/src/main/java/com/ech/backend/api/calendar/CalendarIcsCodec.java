package com.ech.backend.api.calendar;

import com.ech.backend.domain.calendar.CalendarEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal RFC 5545 subset for CSTalk calendar interchange (Phase 6-5).
 * Export uses UTC DATE-TIME with {@code Z}. Import accepts UTC/off TZID/floating when paired with TZID.
 */
public final class CalendarIcsCodec {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter COMPACT_LOCAL = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss");

    private CalendarIcsCodec() {
    }

    public record ParsedVEvent(String summary, String description, OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    private record PropLine(Map<String, String> params, String rawValue) {
    }

    public static String buildUtf8(List<CalendarEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR").append(CRLF);
        sb.append("VERSION:2.0").append(CRLF);
        sb.append("PRODID:-//CSTalk//ECH Calendar//KO").append(CRLF);
        sb.append("CALSCALE:GREGORIAN").append(CRLF);
        sb.append("METHOD:PUBLISH").append(CRLF);
        OffsetDateTime stamp = OffsetDateTime.now(ZoneOffset.UTC);
        for (CalendarEvent e : events) {
            if (!e.isInUse()) {
                continue;
            }
            sb.append("BEGIN:VEVENT").append(CRLF);
            appendFolded(sb, "UID", "ech-cal-" + e.getId() + "-" + sanitizeUidFragment(e.getOwnerEmployeeNo()) + "@cstalk");
            appendFolded(sb, "DTSTAMP", formatUtcCompact(stamp));
            appendFolded(sb, "DTSTART", formatUtcCompact(e.getStartsAt()));
            appendFolded(sb, "DTEND", formatUtcCompact(e.getEndsAt()));
            appendFolded(sb, "SUMMARY", escapeIcsText(e.getTitle()));
            if (e.getDescription() != null && !e.getDescription().isBlank()) {
                appendFolded(sb, "DESCRIPTION", escapeIcsText(e.getDescription()));
            }
            sb.append("END:VEVENT").append(CRLF);
        }
        sb.append("END:VCALENDAR").append(CRLF);
        return sb.toString();
    }

    private static String sanitizeUidFragment(String employeeNo) {
        String s = employeeNo == null ? "user" : employeeNo.replaceAll("[^a-zA-Z0-9._-]", "_");
        return s.isBlank() ? "user" : s;
    }

    private static String formatUtcCompact(OffsetDateTime odt) {
        return odt.withOffsetSameInstant(ZoneOffset.UTC).format(COMPACT_LOCAL) + "Z";
    }

    private static void appendFolded(StringBuilder sb, String name, String value) {
        String line = name + ":" + value;
        sb.append(foldPropertyLine(line)).append(CRLF);
    }

    /** RFC 5545 folding (octet-oriented, UTF-8 safe segments). */
    static String foldPropertyLine(String line) {
        byte[] raw = line.getBytes(StandardCharsets.UTF_8);
        if (raw.length <= MAX_LINE_OCTETS) {
            return line;
        }
        StringBuilder out = new StringBuilder();
        int pos = 0;
        boolean first = true;
        while (pos < raw.length) {
            int budget = first ? MAX_LINE_OCTETS : MAX_LINE_OCTETS - 1;
            int end = Math.min(raw.length, pos + budget);
            while (end > pos && !isUtf8LeadByte(raw[end])) {
                end--;
            }
            if (end <= pos) {
                end = Math.min(raw.length, pos + budget);
            }
            if (!first) {
                out.append(CRLF).append(' ');
            }
            out.append(new String(raw, pos, end - pos, StandardCharsets.UTF_8));
            pos = end;
            first = false;
        }
        return out.toString();
    }

    private static boolean isUtf8LeadByte(byte b) {
        int x = b & 0xff;
        return (x & 0x80) == 0 || (x & 0xc0) == 0xc0;
    }

    private static String escapeIcsText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n");
    }

    public static List<ParsedVEvent> parse(byte[] rawBytes) {
        byte[] bytes = stripBom(rawBytes);
        String text = new String(bytes, StandardCharsets.UTF_8);
        String unfolded = unfold(text);
        List<ParsedVEvent> out = new ArrayList<>();
        int idx = 0;
        while (true) {
            int begin = indexOfIgnoreCase(unfolded, "BEGIN:VEVENT", idx);
            if (begin < 0) {
                break;
            }
            int end = indexOfIgnoreCase(unfolded, "END:VEVENT", begin);
            if (end < 0) {
                break;
            }
            String block = unfolded.substring(begin, end);
            idx = end + "END:VEVENT".length();
            try {
                ParsedVEvent ev = parseVeventBlock(block);
                if (ev != null) {
                    out.add(ev);
                }
            } catch (RuntimeException ignored) {
                // Skip malformed component
            }
        }
        return out;
    }

    private static byte[] stripBom(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xff) == 0xef && (raw[1] & 0xff) == 0xbb && (raw[2] & 0xff) == 0xbf) {
            byte[] copy = new byte[raw.length - 3];
            System.arraycopy(raw, 3, copy, 0, copy.length);
            return copy;
        }
        return raw;
    }

    static String unfold(String content) {
        String norm = content.replace("\r\n", "\n").replace("\r", "\n");
        return norm.replaceAll("\n[ \t]", "");
    }

    static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int nlen = needle.length();
        int max = haystack.length() - nlen;
        for (int i = Math.max(0, from); i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, nlen)) {
                return i;
            }
        }
        return -1;
    }

    private static ParsedVEvent parseVeventBlock(String block) {
        if (block.regionMatches(true, 0, "BEGIN:VEVENT", 0, "BEGIN:VEVENT".length())) {
            block = block.substring("BEGIN:VEVENT".length()).trim();
        }
        if (Pattern.compile("(?im)^RRULE:").matcher(block).find()) {
            return null;
        }
        Map<String, PropLine> props = parseProps(block);
        PropLine dtStartLine = props.get("DTSTART");
        if (dtStartLine == null) {
            throw new IllegalArgumentException("DTSTART missing");
        }
        OffsetDateTime start = parseDateTimeProp(dtStartLine, null);
        ZoneId tzFromStart = tzIdFromParams(dtStartLine.params());

        PropLine dtEndLine = props.get("DTEND");
        PropLine durLine = props.get("DURATION");
        OffsetDateTime end;
        if (dtEndLine != null) {
            end = parseDateTimeProp(dtEndLine, tzFromStart);
        } else if (durLine != null) {
            Duration d = parseDuration(durLine.rawValue());
            end = start.plus(d);
        } else {
            throw new IllegalArgumentException("DTEND or DURATION missing");
        }

        PropLine sum = props.get("SUMMARY");
        PropLine desc = props.get("DESCRIPTION");
        String summary = sum != null ? unescapeIcsText(sum.rawValue()) : null;
        String description = desc != null ? unescapeIcsText(desc.rawValue()) : null;
        return new ParsedVEvent(summary, description, start, end);
    }

    private static ZoneId tzIdFromParams(Map<String, String> params) {
        String tzid = params.get("TZID");
        if (tzid == null || tzid.isBlank()) {
            return null;
        }
        tzid = stripQuotes(tzid.trim());
        try {
            return ZoneId.of(tzid);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unknown TZID: " + tzid);
        }
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Map<String, PropLine> parseProps(String block) {
        Map<String, PropLine> map = new LinkedHashMap<>();
        String[] lines = block.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String namePart = line.substring(0, colon);
            String value = line.substring(colon + 1);
            int semi = namePart.indexOf(';');
            String baseName = (semi > 0 ? namePart.substring(0, semi) : namePart).trim().toUpperCase(Locale.ROOT);
            Map<String, String> params = new LinkedHashMap<>();
            if (semi > 0) {
                String rest = namePart.substring(semi + 1);
                for (String p : rest.split(";")) {
                    int eq = p.indexOf('=');
                    if (eq > 0) {
                        params.put(p.substring(0, eq).trim().toUpperCase(Locale.ROOT), p.substring(eq + 1).trim());
                    }
                }
            }
            map.put(baseName, new PropLine(params, value));
        }
        return map;
    }

    private static OffsetDateTime parseDateTimeProp(PropLine line, ZoneId fallbackTz) {
        Map<String, String> p = line.params();
        String valParam = p.getOrDefault("VALUE", "").trim();
        if ("DATE".equalsIgnoreCase(valParam)) {
            throw new IllegalArgumentException("DATE-only events not supported");
        }
        String raw = line.rawValue().trim();
        if (hasUtcOrNumericOffset(raw)) {
            return parseOffsetCompact(raw);
        }
        ZoneId tz = tzIdFromParams(p);
        if (tz == null) {
            tz = fallbackTz;
        }
        if (tz == null) {
            throw new IllegalArgumentException("Floating local DATE-TIME without TZID is not supported");
        }
        LocalDateTime ldt = LocalDateTime.parse(raw, COMPACT_LOCAL);
        return ldt.atZone(tz).toOffsetDateTime();
    }

    private static boolean hasUtcOrNumericOffset(String raw) {
        if (raw.endsWith("Z")) {
            return true;
        }
        int plus = raw.lastIndexOf('+');
        int minus = raw.lastIndexOf('-');
        int signPos = Math.max(plus, minusIdxPastDate(raw, minus));
        return signPos > 14;
    }

    /** Ignore '-' inside compact date (not used for AD years in this subset). */
    private static int minusIdxPastDate(String raw, int minus) {
        return minus > 14 ? minus : -1;
    }

    private static OffsetDateTime parseOffsetCompact(String raw) {
        String s = raw.trim();
        if (s.endsWith("Z")) {
            String core = s.substring(0, s.length() - 1);
            LocalDateTime ldt = LocalDateTime.parse(core, COMPACT_LOCAL);
            return ldt.atOffset(ZoneOffset.UTC);
        }
        int plus = s.lastIndexOf('+');
        int minus = minusIdxPastDate(s, s.lastIndexOf('-'));
        int signPos = Math.max(plus, minus);
        if (signPos <= 14) {
            throw new IllegalArgumentException("Missing timezone suffix: " + raw);
        }
        String core = s.substring(0, signPos);
        String offRaw = s.substring(signPos);
        LocalDateTime ldt = LocalDateTime.parse(core, COMPACT_LOCAL);
        ZoneOffset off = ZoneOffset.of(normalizeIcsOffset(offRaw));
        return ldt.atOffset(off);
    }

    /** Turns +0900 into +09:00 for {@link ZoneOffset#of(String)}. */
    private static String normalizeIcsOffset(String offRaw) {
        if ((offRaw.startsWith("+") || offRaw.startsWith("-")) && offRaw.length() == 6 && !offRaw.contains(":")) {
            return offRaw.charAt(0) + offRaw.substring(1, 3) + ":" + offRaw.substring(3);
        }
        return offRaw;
    }

    private static Duration parseDuration(String raw) {
        String s = raw.trim();
        if (!s.startsWith("P")) {
            throw new IllegalArgumentException("Bad DURATION");
        }
        try {
            return Duration.parse(s);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Bad DURATION");
        }
    }

    private static String unescapeIcsText(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char n = value.charAt(i + 1);
                if (n == 'n' || n == 'N') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                sb.append(n);
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
