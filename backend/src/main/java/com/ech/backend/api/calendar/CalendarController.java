package com.ech.backend.api.calendar;

import com.ech.backend.api.calendar.dto.CalendarConflictCheckResponse;
import com.ech.backend.api.calendar.dto.CalendarEventResponse;
import com.ech.backend.api.calendar.dto.CalendarImportResponse;
import com.ech.backend.api.calendar.dto.CalendarShareResponse;
import com.ech.backend.api.calendar.dto.CalendarSuggestionResponse;
import com.ech.backend.api.calendar.dto.ReplaceCalendarEventAttendeesRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarEventRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarShareRequest;
import com.ech.backend.api.calendar.dto.CreateCalendarSuggestionRequest;
import com.ech.backend.api.calendar.dto.UpdateCalendarEventRequest;
import com.ech.backend.domain.calendar.CalendarSuggestionStatus;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.exception.UnauthorizedException;
import com.ech.backend.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping(value = "/api/calendar/export.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> exportIcs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        requireAuth(principal);
        byte[] body = calendarService.exportIcs(principal, employeeNo, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cstalk-calendar.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(body);
    }

    @PostMapping(value = "/api/calendar/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CalendarImportResponse> importIcs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String employeeNo,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        requireAuth(principal);
        return ApiResponse.success(
                calendarService.importIcs(principal, employeeNo, file.getBytes()));
    }

    @GetMapping("/api/calendar/events/conflicts")
    public ApiResponse<CalendarConflictCheckResponse> checkConflicts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String employeeNo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endsAt,
            @RequestParam(required = false) Long excludeEventId
    ) {
        requireAuth(principal);
        return ApiResponse.success(
                calendarService.checkConflicts(principal, employeeNo, startsAt, endsAt, excludeEventId));
    }

    @GetMapping("/api/calendar/events/{eventId}")
    public ApiResponse<CalendarEventResponse> getEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long eventId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.getEvent(principal, eventId, employeeNo));
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

    @GetMapping("/api/calendar/suggestions")
    public ApiResponse<List<CalendarSuggestionResponse>> listSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String employeeNo,
            @RequestParam(required = false) String status
    ) {
        requireAuth(principal);
        CalendarSuggestionStatus st = null;
        if (status != null && !status.isBlank()) {
            st = CalendarSuggestionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        }
        return ApiResponse.success(calendarService.listSuggestions(principal, employeeNo, st));
    }

    @PostMapping("/api/calendar/suggestions")
    public ApiResponse<CalendarSuggestionResponse> createSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCalendarSuggestionRequest request
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.createSuggestion(principal, request));
    }

    @PostMapping("/api/calendar/suggestions/{suggestionId}/confirm")
    public ApiResponse<CalendarEventResponse> confirmSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.confirmSuggestion(principal, suggestionId, employeeNo));
    }

    @PostMapping("/api/calendar/suggestions/{suggestionId}/dismiss")
    public ApiResponse<Void> dismissSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId,
            @RequestParam String employeeNo
    ) {
        requireAuth(principal);
        calendarService.dismissSuggestion(principal, suggestionId, employeeNo);
        return ApiResponse.success(null);
    }

    @PutMapping("/api/calendar/events/{eventId}/attendees")
    public ApiResponse<CalendarEventResponse> replaceAttendees(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long eventId,
            @RequestParam String employeeNo,
            @Valid @RequestBody ReplaceCalendarEventAttendeesRequest request
    ) {
        requireAuth(principal);
        return ApiResponse.success(calendarService.replaceAttendees(principal, eventId, employeeNo, request));
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
