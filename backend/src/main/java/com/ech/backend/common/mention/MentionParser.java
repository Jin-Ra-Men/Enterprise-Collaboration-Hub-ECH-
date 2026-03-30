package com.ech.backend.common.mention;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 채팅 본문의 멘션 토큰 `@{employeeNo|표시명}` 또는 `@{employeeNo}` 파싱. */
public final class MentionParser {

    private static final Pattern TOKEN = Pattern.compile("@\\{([^}]*)\\}");
    private static final int MAX_TOKENS = 20;

    private MentionParser() {}

    public static Set<String> collectEmployeeNos(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find() && out.size() < MAX_TOKENS) {
            String inner = m.group(1);
            int pipe = inner.indexOf('|');
            String emp = (pipe >= 0 ? inner.substring(0, pipe) : inner).trim();
            if (!emp.isEmpty()) {
                out.add(emp);
            }
        }
        return out;
    }

    /** 알림 미리보기: 토큰을 @이름 형태로 줄임. */
    public static String previewForToast(String body, int maxLen) {
        if (body == null) {
            return "";
        }
        String s = body
                .replaceAll("@\\{([^}|]+)\\|([^}]+)\\}", "@$2")
                .replaceAll("@\\{([^}]+)\\}", "@$1");
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "…";
    }
}
