package com.ech.backend.api.calendar;

import com.ech.backend.api.calendar.dto.CalendarEventResponse;
import com.ech.backend.api.calendar.dto.CalendarShareResponse;
import com.ech.backend.api.calendar.dto.CreateCalendarEventRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarShareRequest;
import com.ech.backend.api.calendar.dto.UpdateCalendarEventRequest;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/api/calendar/events")
    public ApiResponse<List<CalendarEventResponse>> listEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.listEvents(principal, employeeNo, from, to));
    }

    @PostMapping("/api/calendar/events")
    public ApiResponse<CalendarEventResponse> createEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCalendarEventRequest request
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.createEvent(principal, request));
    }

    @PutMapping("/api/calendar/events/{eventId}")
    public ApiResponse<CalendarEventResponse> updateEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long eventId,
            @RequestParam String employeeNo,
            @Valid @RequestBody UpdateCalendarEventRequest request
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.updateEvent(principal, eventId, employeeNo, request));
    }

    @DeleteMapping("/api/calendar/events/{eventId}")
    public ApiResponse<Void> deleteEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long eventId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        calendarService.deleteEvent(principal, eventId, employeeNo);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/channels/{channelId}/calendar/shares")
    public ApiResponse<CalendarShareResponse> createShare(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long channelId,
            @Valid @RequestBody CreateCalendarShareRequest request
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.createShare(principal, channelId, request));
    }

    @GetMapping("/api/calendar/shares/incoming")
    public ApiResponse<List<CalendarShareResponse>> listIncomingPending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.listIncomingPending(principal, employeeNo));
    }

    @GetMapping("/api/calendar/shares/outgoing")
    public ApiResponse<List<CalendarShareResponse>> listOutgoing(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.listOutgoing(principal, employeeNo));
    }

    @PostMapping("/api/calendar/shares/{shareId}/accept")
    public ApiResponse<CalendarEventResponse> acceptShare(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long shareId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.acceptShare(principal, shareId, employeeNo));
    }

    @PostMapping("/api/calendar/shares/{shareId}/decline")
    public ApiResponse<Void> declineShare(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long shareId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        calendarService.declineShare(principal, shareId, employeeNo);
        return ApiResponse.success(null);
    }

    private static void requireAuth(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
