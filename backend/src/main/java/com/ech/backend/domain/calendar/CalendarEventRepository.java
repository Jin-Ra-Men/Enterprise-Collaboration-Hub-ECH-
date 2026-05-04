package com.ech.backend.domain.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("""
            SELECT e FROM CalendarEvent e
            WHERE e.ownerEmployeeNo = :owner
              AND e.inUse = true
              AND e.startsAt < :rangeEnd
              AND e.endsAt > :rangeStart
            ORDER BY e.startsAt ASC
            """)
    List<CalendarEvent> findActiveForOwnerInRange(
            @Param("owner") String ownerEmployeeNo,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd
    );
}
