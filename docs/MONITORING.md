# ECH 모니터링 및 알람 기준

## 목차
1. [모니터링 대상 및 지표](#모니터링-대상-및-지표)
2. [헬스체크 엔드포인트](#헬스체크-엔드포인트)
3. [알람 임계치 기준](#알람-임계치-기준)
4. [에러 로그 조회](#에러-로그-조회)
5. [감사 로그 조회](#감사-로그-조회)
6. [DB 커넥션 풀 모니터링](#db-커넥션-풀-모니터링)
7. [부하 테스트 기준](#부하-테스트-기준)
8. [장애 대응 절차](#장애-대응-절차)

---

## 모니터링 대상 및 지표

### Backend (Spring Boot)

| 지표 | 설명 | 임계치 |
|------|------|--------|
| HTTP 응답 시간 (p95) | 95%ile 요청 처리 시간 | < 2,000ms |
| HTTP 에러율 | 4xx/5xx 응답 비율 | < 5% |
| JVM 힙 사용률 | GC 압박 여부 | < 80% |
| DB 커넥션 풀 대기 | Hikari waiting count | < 3 |
| DB 커넥션 풀 사용률 | active / max | < 80% |

### Realtime 서버 (Node.js)

| 지표 | 설명 | 임계치 |
|------|------|--------|
| 동시 WebSocket 연결 수 | socketIdToEmployeeNo.size (presence 등록 소켓) | < 5,000 |
| 메시지 처리 지연 | socket → DB → broadcast | < 500ms |
| DB Pool 대기 | `getPoolStats().waiting` | < 5 |
| 프로세스 메모리 | RSS | < 512MB |

### PostgreSQL

| 지표 | 설명 | 임계치 |
|------|------|--------|
| 연결 수 | `pg_stat_activity` count | < 최대 연결 수의 80% |
| 슬로우 쿼리 | 실행 시간 기준 | > 1,000ms 기록 |
| 디스크 사용량 | `pg_database_size()` | 용량의 80% 초과 시 경고 |
| Checkpoint 경고 | `pg_stat_bgwriter` | checkpoint_req_count 증가 시 |

---

## 헬스체크 엔드포인트

### Backend

```http
GET /api/auth/me
Authorization: Bearer {token}
```

정상: `200 OK`  
이상: `401`, `500` → 즉시 알람

### Realtime

```http
GET http://localhost:3001/health
```

정상 응답:
```json
{
  "status": "ok",
  "service": "cstalk-realtime",
  "db": "ok",
  "pool": { "total": 3, "idle": 2, "waiting": 0 },
  "connections": 42
}
```

이상 응답: `db: "error"` → 즉시 알람

### DB 직접 확인

```sql
-- 슬로우 쿼리 (5초 이상 실행 중인 쿼리)
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active'
  AND (now() - query_start) > interval '5 seconds';

-- 커넥션 수 확인
SELECT count(*), state
FROM pg_stat_activity
WHERE datname = 'ech'
GROUP BY state;
```

---

## 알람 임계치 기준

### 긴급 (즉시 대응 필요)

| 조건 | 조치 |
|------|------|
| Backend 프로세스 다운 | 즉시 재시작 후 원인 분석 |
| Realtime 서버 다운 | 즉시 재시작, 클라이언트 재연결 확인 |
| DB 연결 실패 | DB 상태 확인, 커넥션 풀 재설정 |
| JWT Secret 변경 됨 | 모든 클라이언트 재로그인 필요 |

### 경고 (30분 내 조치)

| 조건 | 조치 |
|------|------|
| 에러 로그 분당 10건 이상 | 로그 원인 분석 |
| DB 커넥션 풀 80% 이상 | `DB_POOL_MAX` 상향 검토 |
| HTTP 5xx 에러율 5% 초과 | 에러 로그 및 원인 추적 |
| JVM 힙 80% 이상 | GC 튜닝 또는 메모리 증설 검토 |

### 정보 (일별 확인)

| 조건 | 조치 |
|------|------|
| 슬로우 쿼리 발생 | 인덱스 추가 또는 쿼리 개선 검토 |
| 파일 스토리지 80% 사용 | 보존 정책 점검 또는 스토리지 증설 |
| 감사 로그 테이블 크기 급증 | 보존 정책 확인 |

---

## 에러 로그 조회

에러 로그는 `error_logs` 테이블에 저장된다.

### 관리자 API 조회

```http
GET /api/admin/error-logs?page=0&size=50&level=ERROR
Authorization: Bearer {admin_token}
```

### DB 직접 조회

```sql
-- 최근 1시간 에러 로그
SELECT level, message, stack_trace, created_at
FROM error_logs
WHERE created_at > NOW() - INTERVAL '1 hour'
  AND level = 'ERROR'
ORDER BY created_at DESC
LIMIT 100;

-- 에러 빈도 집계 (시간별)
SELECT DATE_TRUNC('hour', created_at) AS hour,
       level,
       COUNT(*) AS cnt
FROM error_logs
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY 1, 2
ORDER BY 1 DESC;
```

---

## 감사 로그 조회

보안 감사 및 이상 행동 탐지에 활용한다.

### 관리자 API 조회

```http
GET /api/admin/audit-logs?from=2026-01-01&to=2026-01-31&action=FILE_UPLOADED
Authorization: Bearer {admin_token}
```

### 이상 행동 탐지 쿼리

```sql
-- 1시간 내 동일 사용자의 비정상적인 파일 다운로드
SELECT actor_user_id, COUNT(*) AS cnt
FROM audit_logs
WHERE event_type = 'FILE_DOWNLOAD_INFO_ACCESSED'
  AND created_at > NOW() - INTERVAL '1 hour'
GROUP BY actor_user_id
HAVING COUNT(*) > 50
ORDER BY cnt DESC;

-- 실패한 로그인 시도 집계
SELECT meta_json->>'loginId' AS login_id, COUNT(*) AS attempts
FROM audit_logs
WHERE event_type = 'LOGIN_FAILED'
  AND created_at > NOW() - INTERVAL '30 minutes'
GROUP BY 1
ORDER BY 2 DESC;
```

---

## DB 커넥션 풀 모니터링

### Realtime 서버 (Node.js pg Pool)

헬스체크 엔드포인트에서 Pool 통계를 확인한다:

```bash
curl http://localhost:3001/health
# 응답 중 "pool": { "total": N, "idle": N, "waiting": N }
```

`waiting` 값이 지속적으로 증가하면 `DB_POOL_MAX` 설정 상향을 검토한다.

### Backend (HikariCP)

Spring Boot Actuator를 활성화하면 HikariCP 메트릭을 확인할 수 있다:

```yaml
# application.yml에 추가
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  metrics:
    enable:
      hikaricp: true
```

```bash
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

---

## 부하 테스트 기준

k6 부하 테스트 스크립트는 `tools/k6/` 디렉토리에 있다.

### 기준치 (운영 환경 목표)

| 시나리오 | 목표 응답 시간 | 에러율 | 동시 사용자 |
|---------|--------------|--------|------------|
| 로그인 | p95 < 1,500ms | < 1% | 50 VU |
| 통합 검색 | p95 < 1,000ms | < 1% | 20 VU |
| 메시지 목록 | p95 < 500ms | < 1% | 30 VU |
| 파일 업로드 (10MB) | p95 < 5,000ms | < 2% | 10 VU |

### 실행 방법

```bash
# k6 설치 필요: https://grafana.com/docs/k6/latest/get-started/installation/
k6 run --env BASE_URL=http://localhost:8080 tools/k6/load-test.js
k6 run --env BASE_URL=http://localhost:8080 tools/k6/message-stress-test.js
```

---

## 장애 대응 절차

### Backend 응답 없음

1. 헬스체크: `curl http://localhost:8080/api/auth/me`
2. 로그 확인: `journalctl -u cstalk-backend -n 200 --no-pager`
3. JVM 힙 덤프 (OOM 의심): `jmap -dump:live,format=b,file=heap.hprof <pid>`
4. 프로세스 재시작
5. 이상 에러 로그 기록

### Realtime 서버 응답 없음

1. 헬스체크: `curl http://localhost:3001/health`
2. PM2 로그: `pm2 logs cstalk-realtime --lines 100`
3. 재시작: `pm2 restart cstalk-realtime`
4. 클라이언트 자동 재연결 여부 확인 (최대 15초 간격으로 자동 재시도)

### DB 연결 실패

1. PostgreSQL 상태 확인: `systemctl status postgresql`
2. 최대 연결 수 확인: `SELECT count(*) FROM pg_stat_activity;`
3. 연결 수 초과 시 `pg_terminate_backend(pid)`로 유휴 연결 강제 종료
4. 필요시 `max_connections` 상향 후 재시작

### 파일 스토리지 용량 부족

1. 현재 사용량 확인
2. 관리자 UI `⚙️ 설정` 탭에서 `file.storage.base-dir` 변경
3. 기존 파일 새 경로로 이동 (`robocopy` 또는 `rsync`)
4. 보존 정책 활성화로 오래된 파일 자동 아카이빙
