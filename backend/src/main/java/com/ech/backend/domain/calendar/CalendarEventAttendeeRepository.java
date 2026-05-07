package com.ech.backend.domain.calendar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventAttendeeRepository extends JpaRepository<CalendarEventAttendee, Long> {

    List<CalendarEventAttendee> findByCalendarEventIdOrderBySortOrderAsc(Long calendarEventId);

    void deleteByCalendarEventId(Long calendarEventId);
}
