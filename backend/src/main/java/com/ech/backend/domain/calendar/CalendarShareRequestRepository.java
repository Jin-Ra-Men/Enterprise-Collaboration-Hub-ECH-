package com.ech.backend.domain.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarShareRequestRepository extends JpaRepository<CalendarShareRequest, Long> {

    List<CalendarShareRequest> findByRecipientEmployeeNoAndStatusOrderByCreatedAtDesc(
            String recipientEmployeeNo,
            CalendarShareStatus status
    );

    List<CalendarShareRequest> findByRecipientEmployeeNoAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            String recipientEmployeeNo,
            CalendarShareStatus status,
            OffsetDateTime expiresAfter
    );

    List<CalendarShareRequest> findBySenderEmployeeNoOrderByCreatedAtDesc(String senderEmployeeNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CalendarShareRequest s
            SET s.status = :expired, s.updatedAt = :now
            WHERE s.status = :pending AND s.expiresAt < :cutoff
            """)
    int expirePendingBefore(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("pending") CalendarShareStatus pending,
            @Param("expired") CalendarShareStatus expired,
            @Param("now") OffsetDateTime now
    );
}
