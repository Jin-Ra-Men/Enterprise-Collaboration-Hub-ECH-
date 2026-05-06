package com.ech.backend.domain.calendar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarSuggestionRepository extends JpaRepository<CalendarSuggestion, Long> {

    List<CalendarSuggestion> findByOwnerEmployeeNoAndStatusOrderByCreatedAtDesc(
            String ownerEmployeeNo,
            CalendarSuggestionStatus status
    );

    /**
     * 동일 출처 메시지에 대한 PENDING AI 제안 중복 방지({@code origin_message_ids}는 {@code [id]} 또는 배열 형태).
     */
    @Query(value = """
            SELECT COUNT(*) FROM calendar_suggestions s
            WHERE s.owner_employee_no = :owner
              AND s.status = 'PENDING'
              AND UPPER(TRIM(s.created_by_actor)) = 'AI_ASSISTANT'
              AND s.origin_message_ids IS NOT NULL
              AND (
                s.origin_message_ids = :exactJson
                OR s.origin_message_ids LIKE CONCAT('[', CAST(:mid AS VARCHAR), ',%')
                OR s.origin_message_ids LIKE CONCAT('%,', CAST(:mid AS VARCHAR), ',%')
                OR s.origin_message_ids LIKE CONCAT('%,', CAST(:mid AS VARCHAR), ']')
              )
            """, nativeQuery = true)
    long countPendingAiTouchingOriginMessage(
            @Param("owner") String owner,
            @Param("exactJson") String exactJson,
            @Param("mid") long mid
    );
}
