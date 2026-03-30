package com.ech.backend.domain.channel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code channel_members.user_id} 가 아직 {@code users.id}(정수 PK)를 담는 레거시 스키마인지,
 * 아니면 {@code users.employee_no}(문자) FK로 이관된 스키마인지 런타임에 판별한다.
 *
 * <p>레거시 상태에서는 JPA가 {@code cm.user.employeeNo} 조건으로 생성하는 JPQL/SQL이
 * DB 타입 불일치(bigint vs varchar)로 실패할 수 있어, JDBC 보조 경로를 쓴다.</p>
 */
@Component
public class ChannelMemberUserIdColumnInspector {

    private final boolean legacyUserIdReferencesUserPrimaryKey;

    public ChannelMemberUserIdColumnInspector(JdbcTemplate jdbcTemplate) {
        boolean legacy = false;
        try {
            String dataType = jdbcTemplate.queryForObject(
                    """
                            SELECT c.data_type
                            FROM information_schema.columns c
                            WHERE c.table_schema = current_schema()
                              AND c.table_name = 'channel_members'
                              AND c.column_name = 'user_id'
                            """,
                    String.class
            );
            legacy = isIntegerLikeUserReferenceColumn(dataType);
        } catch (Exception ignored) {
            legacy = false;
        }
        this.legacyUserIdReferencesUserPrimaryKey = legacy;
    }

    /**
     * {@code true} 이면 {@code channel_members.user_id} 는 {@code users.id} 와 조인해야 한다.
     */
    public boolean isLegacyUserIdReferencesUserPrimaryKey() {
        return legacyUserIdReferencesUserPrimaryKey;
    }

    private static boolean isIntegerLikeUserReferenceColumn(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return false;
        }
        String t = dataType.trim().toLowerCase();
        return "bigint".equals(t)
                || "int8".equals(t)
                || "integer".equals(t)
                || "int4".equals(t)
                || "smallint".equals(t)
                || "int2".equals(t);
    }
}
