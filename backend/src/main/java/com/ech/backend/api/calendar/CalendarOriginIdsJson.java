package com.ech.backend.api.calendar;

import java.util.ArrayList;
import java.util.List;

final class CalendarOriginIdsJson {

    static final int MAX_IDS = 20;

    private CalendarOriginIdsJson() {
    }

    static String serialize(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    static List<Long> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            return List.of();
        }
        String inner = s.substring(1, s.length() - 1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(Long.parseLong(p));
            }
        }
        return List.copyOf(out);
    }

    static List<Long> normalizeIncoming(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            if (!out.contains(id)) {
                out.add(id);
            }
            if (out.size() >= MAX_IDS) {
                break;
            }
        }
        return List.copyOf(out);
    }
}
