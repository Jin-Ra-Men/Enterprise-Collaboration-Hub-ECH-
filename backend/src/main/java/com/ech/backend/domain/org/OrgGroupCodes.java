package com.ech.backend.domain.org;

/**
 * Human-readable org group_code generator.
 */
public final class OrgGroupCodes {

    public static final String ORG_ROOT_CODE = "ORGROOT";
    public static final String JOB_LEVEL_PARENT_CODE = "0_JobLevel";
    public static final String JOB_POSITION_PARENT_CODE = "0_JobPosition";
    public static final String JOB_TITLE_PARENT_CODE = "0_JobTitle";

    private OrgGroupCodes() {}

    public static String companyCode(String companyCodeNormalized) {
        return compact("C", slugSegment(companyCodeNormalized, 20));
    }

    public static String divisionCode(String companyCodeNormalized, String divisionDisplayName) {
        return compact("D", slugSegment(companyCodeNormalized, 8), slugSegment(divisionDisplayName, 20));
    }

    public static String teamCode(String divisionCode, String teamDisplayName) {
        return compact("T", tailSegment(divisionCode, 10), slugSegment(teamDisplayName, 18));
    }

    public static String jobLevelCode(String jobLevelDisplayName) {
        String mapped = switch (normalize(jobLevelDisplayName)) {
            case "대표이사" -> "0_L100";
            case "사장" -> "0_L110";
            case "부사장" -> "0_L120";
            case "전무" -> "0_L130";
            case "상무" -> "0_L140";
            case "이사" -> "0_L150";
            case "부장" -> "0_L200";
            case "차장" -> "0_L300";
            case "과장" -> "0_L400";
            case "대리" -> "0_L500";
            case "사원" -> "0_L600";
            case "인턴" -> "0_L700";
            default -> null;
        };
        return mapped != null ? mapped : compact("0L", slugSegment(jobLevelDisplayName, 20));
    }

    public static String jobPositionCode(String jobPositionDisplayName) {
        String mapped = switch (normalize(jobPositionDisplayName)) {
            case "대표이사" -> "0_P100";
            case "사장" -> "0_P110";
            case "부사장" -> "0_P120";
            default -> null;
        };
        return mapped != null ? mapped : compact("0P", slugSegment(jobPositionDisplayName, 20));
    }

    public static String jobTitleCode(String jobTitleDisplayName) {
        String mapped = switch (normalize(jobTitleDisplayName)) {
            case "대표이사" -> "0_T100";
            case "사장" -> "0_T110";
            case "부사장" -> "0_T120";
            case "팀장" -> "0_T200";
            case "팀원" -> "0_T300";
            default -> null;
        };
        return mapped != null ? mapped : compact("0T", slugSegment(jobTitleDisplayName, 20));
    }

    private static String compact(String... segments) {
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (s == null || s.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('_');
            }
            sb.append(s);
        }
        if (sb.isEmpty()) {
            return "ORG_UNKNOWN";
        }
        if (sb.length() <= 32) {
            return sb.toString();
        }
        return sb.substring(0, 32);
    }

    private static String tailSegment(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return "BASE";
        }
        String normalized = raw.replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.isBlank()) {
            return "BASE";
        }
        return normalized.length() <= maxLen
                ? normalized
                : normalized.substring(normalized.length() - maxLen);
    }

    private static String slugSegment(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return "GEN";
        }
        String upper = raw.trim().toUpperCase();
        String alias = aliasFor(upper);
        if (alias != null) {
            return alias.length() <= maxLen ? alias : alias.substring(0, maxLen);
        }
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < upper.length() && ascii.length() < maxLen; i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                ascii.append(c);
            }
        }
        if (!ascii.isEmpty()) {
            return ascii.toString();
        }
        int h = Math.abs(upper.hashCode());
        return "N" + Integer.toString(h, 36).toUpperCase();
    }

    private static String aliasFor(String normalizedUpperText) {
        if ("운영본부".equals(normalizedUpperText)) return "OPS_HQ";
        if ("품질본부".equals(normalizedUpperText)) return "QA_HQ";
        if ("기술본부".equals(normalizedUpperText)) return "INFRA";
        if ("경영지원본부".equals(normalizedUpperText)) return "MGMT_HQ";
        if ("영업본부".equals(normalizedUpperText)) return "SALES_HQ";
        if ("기획본부".equals(normalizedUpperText)) return "PLAN_HQ";
        if ("감사본부".equals(normalizedUpperText)) return "AUDIT_HQ";
        if ("미지정 본부".equals(normalizedUpperText) || "미지정본부".equals(normalizedUpperText)) return "RETIREDEPT";
        if ("IT운영팀".equals(normalizedUpperText)) return "ITOPS";
        if ("테스트팀".equals(normalizedUpperText)) return "QA_TEAM";
        if ("개발1팀".equals(normalizedUpperText)) return "DEV_TEAM1";
        if ("개발2팀".equals(normalizedUpperText)) return "DEV_TEAM2";
        if ("인사총무팀".equals(normalizedUpperText)) return "HR_TEAM";
        if ("영업1팀".equals(normalizedUpperText)) return "SALES_TEAM1";
        if ("기획전략팀".equals(normalizedUpperText)) return "PLAN_TEAM";
        if ("보안감사팀".equals(normalizedUpperText)) return "SEC_AUDIT";
        if ("미지정 팀".equals(normalizedUpperText) || "미지정팀".equals(normalizedUpperText)) return "RETIRETEAM";
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
