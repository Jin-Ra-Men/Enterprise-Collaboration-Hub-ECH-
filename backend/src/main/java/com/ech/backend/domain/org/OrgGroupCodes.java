package com.ech.backend.domain.org;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable org group_code values: ASCII-only (A–Z, 0–9, underscore).
 * Fingerprints are full 32-char MD5 hex (unchained semantics); pretty codes embed an 8- or 12-char prefix of that hash for uniqueness.
 */
public final class OrgGroupCodes {

    private OrgGroupCodes() {}

    public static String fingerprintCompany(String companyCodeNormalized, String companyDisplayName) {
        return md5Hex("COMPANY;" + companyCodeNormalized + ";" + companyDisplayName);
    }

    public static String fingerprintDivision(String companyFingerprint32, String divisionDisplayName) {
        return md5Hex("DIVISION;" + companyFingerprint32 + ";" + divisionDisplayName);
    }

    public static String fingerprintTeam(String divisionFingerprint32, String teamDisplayName) {
        return md5Hex("TEAM;" + divisionFingerprint32 + ";" + teamDisplayName);
    }

    public static String fingerprintJobLevel(String jobRankTrimmed) {
        return md5Hex("JOB_LEVEL;" + jobRankTrimmed);
    }

    public static String fingerprintDutyTitle(String dutyTitleTrimmed) {
        return md5Hex("DUTY_TITLE;" + dutyTitleTrimmed);
    }

    public static String prettyCompany(String companyCodeNormalized, String companyFingerprint32) {
        return "COMP_" + slugSegment(companyCodeNormalized, 12) + "_" + fp8(companyFingerprint32);
    }

    public static String prettyDivision(String companyFingerprint32, String divisionFingerprint32) {
        return "DIV_" + fp8(companyFingerprint32) + "_" + fp8(divisionFingerprint32);
    }

    public static String prettyTeam(String divisionFingerprint32, String teamFingerprint32) {
        return "TEAM_" + fp8(divisionFingerprint32) + "_" + fp8(teamFingerprint32);
    }

    public static String prettyJobLevel(String jobFingerprint32) {
        return "JOB_" + fp12(jobFingerprint32);
    }

    public static String prettyDutyTitle(String dutyFingerprint32) {
        return "DUT_" + fp12(dutyFingerprint32);
    }

    private static String slugSegment(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return "GEN";
        }
        String upper = raw.trim().toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upper.length() && sb.length() < maxLen; i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "GEN" : sb.toString();
    }

    private static String fp8(String fingerprint32) {
        return fingerprint32.substring(0, 8).toUpperCase();
    }

    private static String fp12(String fingerprint32) {
        return fingerprint32.substring(0, 12).toUpperCase();
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
