package com.ech.backend.domain.calendar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarSuggestionRepository extends JpaRepository<CalendarSuggestion, Long> {

    List<CalendarSuggestion> findByOwnerEmployeeNoAndStatusOrderByCreatedAtDesc(
            String ownerEmployeeNo,
            CalendarSuggestionStatus status
    );
}
