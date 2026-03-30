package com.ech.backend.domain.channel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자 참조 컬럼이 아직 {@code users.id}(정수 PK)만 담는 레거시인지,
 * {@code users.employee_no} FK로 이관된 스키마인지 런타임에 판별한다.
 *
 * <p>대상: {@code channel_members.user_id}, {@code messages.sender_id} 등.
 * 레거시에서는 JPA JPQL이 bigint=varchar 조인·조건 SQL을 만들어 PostgreSQL에서 실패할 수 있다.</p>
 */
@Component
public class ChannelMemberUserIdColumnInspector {

    private final boolean legacyChannelMemberUserIdReferencesPk;
    private final boolean legacyMessageSenderReferencesPk;

    public ChannelMemberUserIdColumnInspector(JdbcTemplate jdbcTemplate) {
        this.legacyChannelMemberUserIdReferencesPk = detectIntegerFkColumn(
                jdbcTemplate, "channel_members", "user_id");
        this.legacyMessageSenderReferencesPk = detectIntegerFkColumn(
                jdbcTemplate, "messages", "sender_id");
    }

    /**
     * {@code true} 이면 {@code channel_members.user_id} 는 {@code users.id} 와 조인해야 한다.
     */
    public boolean isLegacyUserIdReferencesUserPrimaryKey() {
        return legacyChannelMemberUserIdReferencesPk;
    }

    /**
     * {@code true} 이면 {@code messages.sender_id} 는 {@code users.id} 와 조인해야 한다.
     */
    public boolean isLegacyMessageSenderReferencesUserPrimaryKey() {
        return legacyMessageSenderReferencesPk;
    }

    private static boolean detectIntegerFkColumn(JdbcTemplate jdbc, String tableName, String columnName) {
        try {
            /*
             * current_schema()만 쓰면 search_path·앱 스키마와 실제 테이블 스키마가 어긋날 때
             * 컬럼을 못 찾아 "레거시 아님"으로 떨어지고, JPA가 bigint=varchar SQL을 생성한다.
             * public 우선, 그다음 기타 사용자 스키마에서 첫 매칭만 본다.
             */
            var types = jdbc.query(
                    """
                            SELECT c.data_type
                            FROM information_schema.columns c
                            WHERE lower(c.table_name) = lower(?)
                              AND lower(c.column_name) = lower(?)
                              AND c.table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
                            ORDER BY CASE WHEN c.table_schema = 'public' THEN 0 ELSE 1 END,
                                     c.table_schema
                            LIMIT 1
                            """,
                    (rs, rowNum) -> rs.getString(1),
                    tableName,
                    columnName
            );
            if (types.isEmpty()) {
                return false;
            }
            return isIntegerLikeUserReferenceColumn(types.get(0));
        } catch (Exception ignored) {
            return false;
        }
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
