-- 일정 참석자(내부 사번·외부 표시명). PostgreSQL.
-- calendar_events 가 존재한 뒤 실행. Hibernate ddl-auto=update 도 동일 스키마를 생성할 수 있음.

CREATE TABLE IF NOT EXISTS calendar_event_attendees (
    id BIGSERIAL PRIMARY KEY,
    calendar_event_id BIGINT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    attendee_type VARCHAR(20) NOT NULL CHECK (attendee_type IN ('INTERNAL', 'EXTERNAL')),
    employee_no VARCHAR(50),
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calendar_event_attendees_event ON calendar_event_attendees(calendar_event_id);
