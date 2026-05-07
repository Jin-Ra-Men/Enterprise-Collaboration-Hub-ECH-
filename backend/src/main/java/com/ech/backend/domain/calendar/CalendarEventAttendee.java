package com.ech.backend.domain.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_event_attendees")
public class CalendarEventAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_type", nullable = false, length = 20)
    private CalendarAttendeeType attendeeType;

    /** INTERNAL 일 때만 채움. */
    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 320)
    private String email;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected CalendarEventAttendee() {
    }

    public CalendarEventAttendee(
            CalendarEvent calendarEvent,
            CalendarAttendeeType attendeeType,
            String employeeNo,
            String displayName,
            String email,
            int sortOrder
    ) {
        this.calendarEvent = calendarEvent;
        this.attendeeType = attendeeType;
        this.employeeNo = employeeNo;
        this.displayName = displayName;
        this.email = email;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public CalendarEvent getCalendarEvent() {
        return calendarEvent;
    }

    public CalendarAttendeeType getAttendeeType() {
        return attendeeType;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
