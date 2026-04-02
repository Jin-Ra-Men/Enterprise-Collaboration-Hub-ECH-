# CHANGELOG

프로젝트 변경 이력을 기록합니다.

## 2026-04-02

### Changed
- **좌측 사이드바 프로필**: `sidebar-slip`·`aside.sidebar`에 `flex`/`min-height:0`/`max-height`로 높이 체인 보강, 하단 **프로필**이 뷰포트 밖으로 밀리지 않도록 조정
- **햄버거(채널 메뉴)**: `member-panel-scroll`로 알림~멤버~하단 액션을 **한 스크롤**로 통합(멤버 `ul` 단독 스크롤 제거)
- **업무·칸반 모달**: 좌우 2열 그리드 제거 → **세로 한 열**(업무 항목 위, 칸반 아래), 모달 본문 전체 스크롤, `max-width` 720px
- **스크롤·반응형(설치형/Electron 대비)**: `html`/`body` flex 체인, `#mainApp` `max-height: 100dvh`. 좌측 **사이드바 본문**(`sidebar-main`)에 세로 스크롤 — 채널·DM·관리자까지 한 열에서 스크롤로 도달. **퀵 레일**(`quick-rail-scroll`) 스크롤 보강. **업무·칸반 모달**: `work-hub-panel-body`로 폼·목록·보드 구조 유지
- 우측 **햄버거(멤버) 패널** 메뉴 순서: 알림 끄기/켜기 → 첨부파일 → 업무/칸반 → 채널(DM) 이름 변경 → **멤버 목록** 구역 제목 → 멤버 리스트 → 구성원 추가 → 채팅방 나가기 → 채널 폐쇄
- 일반 알림을 끈 채널/DM: 사이드바·퀵 레일 항목에 **벨+슬래시** 아이콘 표시(`notifyMutedBellSvg`). 음소거 토글 시 `lastSidebarChannelsSnapshot`으로 목록만 재렌더
- 채팅방 **알림 끄기**: **`pushNewMessageToast`(일반 신규 메시지 토스트)만** 억제. **멘션 토스트**(`pushMentionToast`)는 음소거와 무관하게 항상 표시. **미읽음 배지**(`unreadCount`)는 기존과 동일하게 유지(음소거와 무관). 햄버거 버튼 `title` 문구 보강

### Added
- 채팅방별 **알림 끄기/켜기**(로컬만): 사용자별 `localStorage` 키 `ech_notify_muted_channels_{employeeNo}`에 채널 ID 배열 저장. 음소거 시 **다른 채의 일반 신규 메시지 토스트**만 끔(멘션·미읽음 배지는 유지). 채널·DM·퀵 레일 항목 **우클릭** 메뉴(알림 끄기/켜기, 채팅방 나가기), 상단 햄버거(멤버 패널) **알림 끄기/켜기** 버튼
- 업무–칸반 연결: `kanban_cards.work_item_id`, `work_items.in_use`(소프트 삭제). 카드 생성 시 `workItemId` 필수(채널 일치 검증).
- 업무 API: `GET /api/work-items/sidebar/by-assigned-cards`(내가 카드 담당인 업무 목록), `POST /api/work-items/{id}/restore`, `DELETE /api/work-items/{id}?hard=true`(완전 삭제·연결 카드 제거).
- 프론트: 신규 카드에 연결 업무 선택, 업무 비활성 시 회색/취소선, 상세에서 복원·완전 삭제. 사이드바 `내 업무 항목`으로 전환.

### Changed
- 업무·칸반 하단 **저장** 성공 시 `modalWorkHub`를 자동으로 닫지 않음(닫기 버튼·오버레이로 닫음)
- 칸반 카드 드래그앤드롭: 같은 컬럼뿐 아니라 **컬럼 간(좌우) 이동** 가능. 드래그 소스는 보드 전역 `.kanban-card-dragging` 및 `data-drag-source-column-id`로 식별
- 칸반 카드가 이동한 컬럼에 맞춰 `status`를 동기화(기본 3컬럼: 1번째 `OPEN`, 2번째 `IN_PROGRESS`, 3번째 `DONE`, 그 외 컬럼명 휴리스틱). 저장 시 `PUT /api/kanban/cards/{id}`에 `columnId`와 함께 `status` 전달, 신규 카드 `POST`에도 대상 컬럼 기준 `status` 반영
- 업무·칸반 모달 저장 성공 시 사이드바 **내 업무 항목**(`GET .../work-items/sidebar/by-assigned-cards`)을 즉시 다시 불러 담당 칸반 반영(새로고침 불필요)
- 사이드바 **내 업무 항목** 클릭 시 채팅 채널 전환 없이 업무·칸반 모달만 표시(`workHubScopedChannelId`·`getWorkHubChannelId()`로 로드/저장 채널 분리, 모달 닫기 시 스코프 해제)
- `DELETE /api/work-items/{id}` 기본 동작을 소프트 삭제(`in_use=false`)로 변경.
- 칸반 카드·상세 모달의 `담당 없음` 안내 글자 크기를 담당 칩(11px)보다 한 단계 작게 맞춤(10px, 보조색)
- 칸반 담당 자동완성: 채널 멤버 후보에 **본인 포함**(이미 배정된 사번만 제외), 제안 행에서 이름과 조직·직급을 폰트 크기·색으로 구분
- 사이드바 빈 상태 문구(내 업무 항목·미확인 멘션) 폰트를 헤더 대비 작게 조정, 다크 기본 테마의 보조 텍스트(`--text-secondary`/`--text-muted`) 대비 보강, `.muted` 보조 텍스트 클래스 정의
- 칸반 카드 상세 모달에서 담당 자동완성 **↑↓·Enter** 키보드 탐색이 동작하도록 Work Hub와 동일 로직 연동

### Fixed
- `work_items.in_use` 컬럼: 기존 행이 있는 DB에서 ddl-auto가 `NOT NULL`만 추가하며 실패하던 문제를 `@ColumnDefault("true")`로 완화(PostgreSQL 기본값 포함 ADD COLUMN)
- 칸반 담당 해제 저장: 보드 재조회 후 카드 탐색 실패·pending 맵 키 불일치로 `removeFinal`이 비어 `DELETE /api/kanban/cards/{id}/assignees/...`가 호출되지 않던 문제를 보정(`findWorkHubKanbanCardById`, `kanbanPendingAssigneeMapGet`, 카드 미탐색 시 스냅샷 해제 폴백)
- 칸반 담당 `DELETE`: 자식만 `assigneeRepository.delete` 할 때 `KanbanCard.assignees` 컬렉션이 1차 캐시에 남아 응답 `assigneeEmployeeNos`에 제거 대상이 그대로 노출되던 문제를 보정(부모 컬렉션에서 제거·orphanRemoval, 폴백 삭제)

## 2026-04-01

### Fixed
- 상세 편집 모달의 설명 `textarea` 스타일을 보강해 입력 영역 인지성을 개선(높이/테두리/포커스/리사이즈)
- 칸반 담당자 자동완성은 검색어 입력 시에만 노출되도록 변경(빈 입력 상태 자동 노출 제거)
- 칸반 카드 상세에서 담당 해제 후 저장 시 해제값이 유실되던 문제를 보정(유효 담당자 기준 diff로 add/remove 맵 재계산)
- 칸반 카드 상세의 담당자 칩 라벨이 사번으로만 노출되던 문제를 수정해 채널 멤버 이름 우선 표기
- 업무/칸반 저장 직전 카드별 담당 추가·해제 연산을 정규화해, 해제 후 저장 시 동일 담당자가 다시 바인딩되던 케이스를 보정
- 업무/칸반 저장 시 담당자 변경 계산 전에 채널 칸반 보드를 재조회해 기준 상태를 동기화하고, 담당 해제 누락 재발 가능성을 추가 완화
- 작은 해상도에서 칸반 컬럼/카드가 패널 경계를 벗어나던 문제를 반응형 그리드(자동 줄바꿈)와 모달 폭 제한으로 보정
- 업무/칸반 메인 패널에서 업무 카드/칸반 컬럼이 div를 넘어서던 문제를 `min-width:0`, `overflow-x:hidden`, `overflow-wrap:anywhere`, 모바일 1열 전환으로 추가 보정
- 업무/칸반의 칸반 컬럼 레이아웃을 줄바꿈 방식에서 가로 스크롤 방식으로 전환해 `할 일/진행 중/완료`가 한 줄에서 유지되도록 보정(작은 화면에서 `완료`가 아래로 내려가는 현상 수정)
- 칸반 담당 해제 저장: API 사번과 UI 사번이 대소문자·공백만 달라도 해제 diff가 비어 DELETE가 생략되던 문제를 정규화·대소문자 무시 매칭·서버 캐논 사번으로 DELETE 하도록 보정
- 칸반 카드 상세 모달에서 담당자 추가/해제를 지원하고, 변경분이 임시 저장 맵(담당 추가/해제)에 반영되도록 확장
- 채널 이름 변경 API(`PUT /api/channels/{channelId}/name`) 경로 연동을 보강해 404(요청 경로 없음) 오류가 발생하던 케이스를 완화
- 칸반 드래그앤드롭에 드롭 위치 가이드 라인(`kanban-drop-before`)을 추가해 이동 예상 위치를 시각적으로 명확화
- 업무 항목/칸반 카드 클릭 시 상세 편집 모달을 열고 제목/설명/상태(컬럼)을 임시 반영 후 저장 시 일괄 반영되도록 확장
- 관리자 위임 처리에서 불필요한 멤버 레코드 재생성을 제거해 위임 시 FK 제약 오류를 회피
- 채널 폐쇄 처리에서 멤버/읽음 상태만 정리해 사용자 제약 위반 없이 채널 가시성을 제거하도록 보정
- 공통 다이얼로그 상단 모서리/구분선 스타일을 정리해 헤더 각짐과 섹션 라인 노출을 제거
- 공통 다이얼로그 스타일을 고급형으로 개선(아이콘 배지, 그라데이션 헤더, 그림자, 등장 애니메이션, danger 톤 버튼/배지)
- 프론트 공통 다이얼로그 적용 범위를 확장해 `app.js` 전역의 기본 `alert/confirm/prompt` 호출을 `uiAlert/uiConfirm/uiPrompt` 기반 중앙 모달 UX로 통일
- 업무 허브 레이아웃: 칸반 카드 추가 시 우측 패널이 고정 높이로 남던 문제를 수정하고, 좌/우 패널 높이가 긴 쪽 기준으로 함께 늘어나도록 보정
- 채널/DM 상단 액션: 개별 아이콘 노출 대신 우측 햄버거 메뉴로 통합하고, 같은 패널에서 멤버 목록을 함께 보이도록 변경
- 멤버 배지 명칭: `개설자` 표기를 `관리자`로 변경
- 관리자 위임 UX: 사번 직접 입력 대신 멤버 목록 우클릭 메뉴에서 위임하도록 변경
- 공통 알림 UX: 주요 `alert/confirm` 호출을 공통 다이얼로그(`uiAlert/uiConfirm/uiPrompt`) 기반으로 전환
- DM 나가기: 마지막 멤버가 나가도 DM 채널 엔티티를 삭제하지 않도록 바꿔 `users.employee_no` 제약 오류를 회피
- 1:1 DM 재사용: 기존 DM 탐색 시 canonical 이름 매칭을 추가해 중복 생성 가능성을 완화
- 담당 자동완성 드롭다운 항목 좌측/우측 패딩을 추가해 텍스트가 경계에 붙어 보이던 UI 문제를 수정
- 칸반 카드 `↑` 버튼 기반 순서 변경을 제거하고, 카드 세로 드래그앤드롭으로 임시 순서 변경(저장 시 `sortOrder` 반영)으로 전환
- 업무 허브 저장 UX를 보강해 저장 버튼 클릭 시 확인창을 띄우고, 저장 성공 시 모달을 자동으로 닫도록 수정
- 칸반 담당자 저장 순서를 `해제 -> 추가`로 조정해 담당자 삭제 후 저장 시 재바인딩되는 케이스를 완화
- 업무/칸반 임시 렌더: 신규 항목을 별도 예정 리스트가 아닌 실제 목록/컬럼에 즉시 표시하고 저장 시 서버 반영
- 칸반 카드 이동 UX: 컬럼 변경 즉시 해당 컬럼으로 임시 이동되도록 렌더 보정
- 칸반 카드 레이아웃: 카드 리스트가 컬럼 배경을 벗어나지 않도록 컬럼 overflow/리스트 스크롤 보강
- 칸반 카드 정렬: 신규 카드는 기존 카드 아래에 붙도록 정렬 순서 조정
- 칸반 카드 순서변경: 카드별 `↑/↓` 버튼으로 임시 순서 변경 후 저장 시 sortOrder 반영
- 칸반 담당 임시저장: 담당 추가/삭제를 즉시 API 대신 임시 반영 후 저장 시 일괄 반영
- 담당 자동완성 간격: 검색 입력과 자동완성 항목 간 시각적 구분(항목 분리선/패딩) 보강
- 업무 허브 임시표시: 신규 업무/카드가 `예정` 별도 리스트가 아니라 실제 목록/칸반 컬럼에 즉시 반영된 것처럼 렌더되도록 수정(저장은 하단 버튼 시점)
- 칸반 카드 이동: 임시 컬럼 변경값 렌더 기준을 컬럼명 매칭에서 `card.columnId` 기준으로 바꿔 상태(할 일/진행 중/완료) 이동이 즉시 반영되도록 수정
- 칸반 담당 변경: 추가/해제를 즉시 API 호출하지 않고 임시 반영 후 저장 버튼에서 일괄 반영되도록 수정
- 담당 자동완성 라벨: `이름 + 사번`에서 `이름 + 부서 + 직급` 표기로 변경
- 신규 카드 추가 버튼 위치: `채널 멤버 검색` 입력 옆 가로 배치로 조정(CSS 줄바꿈/세로 깨짐 방지)
- 업무 허브 저장 정책: 업무/칸반의 `✕` 삭제도 즉시 API 호출하지 않고 임시 상태로 보관했다가 하단 **저장** 클릭 시에만 반영되도록 수정(닫기 시 미저장 변경 폐기)
- 업무 허브 입력 흐름: `업무 추가`/`카드 추가` 버튼으로 각각 임시 목록에 적재 후 하단 **저장**에서만 서버 반영되도록 보강
- 칸반 카드 담당: 검색 후보 클릭 핸들러보다 **담당 해제(✕)** 를 먼저 처리하고, `data-assignee-emp`·서버 사번 trim으로 해제 실패 완화
- 구성원 추가 팝업: 하단 선택 태그의 `✕`로 취소할 때 조직도 우측 목록 버튼이 `제외`에서 `추가`로 즉시 되돌아오지 않던 문제 수정
- DM 1:1 중복 생성: 레거시/이름 편차가 있어도 동일 2인 조합이면 기존 DM 채널을 재사용하도록 보강
- DM 나가기: 멤버가 1명 남은 DM은 채널/메시지 이력을 유지하고 시스템 메시지 저장을 생략해 `users.employee_no` 제약 오류를 회피
- DM 사이드바 라벨: 상대가 나가 1인 DM이 되어도 기존 `description` 라벨을 폴백으로 유지(`DM`으로 퇴화 방지)

### Added
- 업무 허브: 업무 항목·칸반 카드 **삭제(✕)**, 신규·상태·컬럼 이동은 **저장** 버튼으로 일괄 반영, `work-hub-panel`이 목록 높이에 맞게 확장(모달 본문 스크롤)
- API: `DELETE /api/work-items/{workItemId}?actorEmployeeNo=...` (채널 멤버)
- 채널 운영 API: 그룹 DM 이름 변경(`PUT /api/channels/{id}/dm-name`), 관리자 위임(`POST /api/channels/{id}/delegate-manager`), 채팅방 나가기(`POST /api/channels/{id}/leave`), 채널 폐쇄(`DELETE /api/channels/{id}`)
- API: `GET /api/kanban/cards/assigned?employeeNo=...`로 내 담당 칸반 카드(채널 연동 보드) 요약 목록 조회 추가
- 좌측 사이드바에 `내 담당 칸반` 섹션을 추가해 채널/DM 목록과 같은 영역에서 담당 카드 확인·클릭 이동 지원

### Changed
- 채널 헤더 액션: DM(3인 이상) 이름 변경, 채팅방 나가기, 관리자 전용 위임/채널 폐쇄 버튼 추가
- 업무 허브: 좌/우 `work-hub-panel`을 동일 2열 비율과 stretch 레이아웃으로 통일해 높이가 함께 연동되도록 조정
- 업무 허브: `.work-hub-body`에 `grid-auto-rows: 1fr`, 패널에 `height: 100%`를 적용해 좌/우 중 긴 쪽 높이에 맞춰 반대편도 동일 높이로 동기화
- 업무 허브: 위 설정(`grid-auto-rows: 1fr`/`height: 100%`)이 콘텐츠 확장을 막아 패널 이탈이 생겨 제거하고, `align-items: stretch` + 패널 `align-self: stretch`로 자연 확장/동기화 방식으로 재조정
- 칸반: `DELETE /api/kanban/cards/{cardId}`를 `MEMBER`+`actorEmployeeNo`로 호출하도록 정리(채널 보드는 멤버 삭제 가능, 서비스에서 `assertCanMutateCard`)
- 칸반 담당: 전사 사용자 검색 대신 **해당 채널 멤버**만 후보로 표시·백엔드에서 채널 연동 보드는 담당 사번이 채널 멤버인지 검증
- 통합 검색: `WORK_ITEM`/`KANBAN_CARD` 결과 클릭 시 해당 채널에서 `업무·칸반` 모달을 열고 항목 강조(`SearchResultItem.relatedChannelId` 추가)
- 업무 허브: 업무 상태 라벨 한글화, 업무 추가 행 한 줄 레이아웃, 담당 자동완성 **↑↓·Enter** 키 지원(드롭다운 열린 채 미선택 Enter 시 폼 제출 방지)
- `docs/FEATURE_SPEC.md`·`docs/HANDOVER.md`·`README.md`: 위 검색·담당·허브 동작 반영

### Added
- 칸반 담당 자동완성: 빈 검색·포커스 시 사용자 목록, 입력 시 `GET /api/users/search`로 후보 표시(디바운스), 기존 카드·신규 카드 폼 공통
- 카드 생성 시 담당: `POST .../cards` body `assigneeEmployeeNos`로 생성과 동시에 담당 지정, 프론트는 신규 카드 폼에서 담당 칩·검색으로 선택
- 채널 `업무 · 칸반` 모달: 칸반 카드별 담당자 표시·검색 추가(`GET /api/users/search`)·해제(✕), 백엔드 담당 API를 채널 보드에서 `MEMBER`가 쓰도록 `assertCanMutateCard`와 동일 정책으로 정렬

### Fixed
- 칸반 카드 미표시: `findAllForBoardWithAssignees`가 `JOIN FETCH assignees`만 사용해 INNER JOIN처럼 동작, 담당자 없는 신규 카드가 보드 조회 결과에서 빠지던 문제 수정 — `column`은 `JOIN FETCH`, `assignees`·담당 `user`는 `LEFT JOIN FETCH`로 변경
- 칸반 보드 조회: `findAllForBoardWithAssignees`의 `SELECT DISTINCT` + `ORDER BY` 조합이 PostgreSQL에서 `InvalidDataAccessResourceUsageException`을 유발하던 문제 수정 — JPQL에서 정렬 제거 후 `KanbanService.getBoard`에서 컬럼별 카드 `sortOrder`로 정렬
- 채널 연동 칸반: 카드 생성/이동 API가 `MANAGER` 전용이어서 일반 멤버(`MEMBER`)가 헤더 `📋` 흐름에서 403이 나던 문제 수정 — `MEMBER` 인증으로 완화하고, 채널 보드는 채널 멤버십·워크스페이스 전용 보드는 `MANAGER` 이상으로 서비스 레이어에서 구분

## 2026-03-31

### Changed
- 에러 기록 정책 정리: `ERRORS.md`에서 JDK/JAVA_HOME 같은 환경성 단독 이슈를 제거하고, 소스 수정 후 테스트·빌드·런타임 검증 과정의 코드/서비스 오류 중심으로 기록 기준을 명확화

### Added
- 채널 연동 업무 허브 UI: 채널 헤더 `📋` 버튼으로 `업무 · 칸반` 모달을 열고, 같은 채널의 업무 목록/칸반 보드를 한 화면에서 조회·생성·상태 변경할 수 있도록 연동
- 채널 업무 API 확장: `GET /api/channels/{channelId}/work-items`, `POST /api/channels/{channelId}/work-items`, `PUT /api/work-items/{workItemId}`로 채널 단위 업무 조회/생성/수정 흐름 추가
- 채널 기본 칸반 보드 API: `GET /api/kanban/channels/{channelId}/board?employeeNo=...` 호출 시 채널 멤버 검증 후 보드를 조회하고, 없으면 `할 일/진행 중/완료` 기본 컬럼으로 자동 생성

### Fixed
- 멘션 토스트 문구 단순화: `보낸 사람:`/`위치:` 라벨을 제거하고 발신자·위치 텍스트만 굵게 강조해 더 빠르게 읽히도록 정리
- 멘션 토스트 가독성 개선: `임보안 · 채널명` 형태 대신 `보낸 사람`과 `위치(DM/채널)`를 분리해 어디서 멘션됐는지 즉시 식별 가능하도록 문구 구조 개선
- 멘션 토스트 크기 확대: 카드 최소 너비/패딩/본문 폰트를 키워 DM·채널 멘션 내용이 답답하게 보이던 문제 완화
- 검색 모달 위치 재조정: 검색 타입/검색어/검색 버튼을 하단 footer가 아닌 `"검색 결과"` 제목 바로 아래 툴바로 이동하고, 버튼 텍스트가 세로로 쪼개지지 않도록 `nowrap` 및 최소 너비를 적용
- 검색 모달 레이아웃 안정화: 검색 타입/검색어/검색 버튼을 헤더에서 제거하고 결과 리스트 하단 footer로 이동해 제목 줄 깨짐 및 헤더 영역 레이아웃 붕괴 현상 수정
- 통합 검색 타입 선택 유지: 검색 submit 시 `searchTypeSelect` 값을 강제로 `ALL`로 초기화하던 버그 수정(`MESSAGES`/`COMMENTS`/`CHANNELS`/`FILES` 타입 혼합 노출 방지)
- 메시지 검색 범위 정리: `MESSAGES` 검색에서 `COMMENT_*`, `REPLY_*`, `FILE_*` 메시지와 비루트 메시지를 제외해 댓글/파일 첨부 메시지가 섞이던 문제 수정
- DM 검색 결과 표기 개선: 컨텍스트 채널명이 내부 키(`__dm__...`)로 보이던 문제를 표시용 설명(상대방 이름 요약) 우선 노출로 개선
- DM 검색 결과 표시명 자연화: 1:1 DM은 상대 이름, 다자간 DM은 `대표이름 외 N명` 형식으로 표시해 내부 식별자 노출을 방지
- 채널명 검색 정합성 개선: `CHANNELS` 타입 검색을 채널 `description`까지 포함하던 조건에서 채널 `name`만 매칭하도록 조정
- 프론트 검색 결과 안전 필터 추가: 타입별(`MESSAGES/COMMENTS/CHANNELS/FILES/...`) 허용 아이템만 렌더해 혼합 결과 노출을 차단
- 검색 결과 카드 중복 본문 정리: 제목과 미리보기 문자열이 같은 경우 preview 라인을 숨겨 중복 표시 제거
- 검색 결과 클릭 동작 정정: `MESSAGE` 타입은 항상 채널 이동 + 메시지 포커싱만 수행하고, 스레드 모달 오픈은 `COMMENT` 타입에서만 수행
- 댓글/메시지 검색 결과 본문 정리: FILE payload(JSON) 본문은 raw JSON 대신 `첨부파일: {원본파일명}`으로 변환해 표시
- 레이아웃 미세 정렬 보정: `ECH` 워크스페이스 헤더와 채널 헤더 높이를 `--layout-header-height`로 통일, 좌하단 사용자 바와 메시지 입력영역 높이를 `--layout-footer-height`로 맞춰 상·하단 경계선 정렬 개선
- 검색 UX 개선: 검색 결과 모달 헤더에 검색어 입력창/검색 버튼을 추가해 팝업 내부에서 바로 재검색 가능하도록 변경(Enter 키 지원, 좌측 검색창과 값 동기화)
- 검색 모달 헤더 정렬 조정: 검색 타입 셀렉트를 검색어 입력창 왼쪽으로 이동
- 검색 결과 타입별 라인 정렬 보정: `search-item-type` 폭 고정 및 배지 높이 통일로 채널/파일/메시지/댓글 항목의 본문 시작선이 일관되게 정렬되도록 개선
- 검색 모달 제목 레이아웃 보정: `#searchModal .modal-header`를 그리드로 분리하고 제목 `white-space: nowrap` 적용해 `"검색 결과"` 타이틀 글자 밀림/줄바꿈 현상 수정
- 댓글 검색 결과 정확도 개선: `COMMENT` 클릭 시 채널 이동 후 타임라인 포커스 대신 스레드 모달(`openThreadModal`)을 열고 대상 댓글/답글(`thread-msg-{id}`) 위치로 스크롤·강조
- 스레드 모달 원글 로드 보강: 타임라인 캐시에 없는 원글은 `GET /api/channels/{channelId}/messages/{messageId}?employeeNo=...` 단건 API로 조회해 모달 렌더를 보장
- 통합 검색 댓글 결과에 `threadRootMessageId`를 포함해 프론트가 정확한 스레드 루트 기준으로 이동할 수 있게 개선
- 검색 결과 인터랙션: 메시지/댓글 결과 클릭 시 해당 채널·메시지 포커스 이동, 파일 결과 클릭 시 다운로드, 이미지 파일은 확대 미리보기+다운로드 모달로 분기
- 통합 검색 확장: 검색 타입에 `COMMENTS`/`CHANNELS` 추가, 댓글(`COMMENT_*`) 본문 및 채널명/설명 검색 지원, 프론트 검색 필터(댓글/채널명) 연동
- Realtime 서버: `listen(PORT, HOST)`로 기본 `SOCKET_HOST=0.0.0.0` 바인딩(포트는 `SOCKET_PORT`), LAN 등 비-localhost 접속 시 연결 거절 완화
- 프론트: `connect_error`·프레즌스 `fetch` 실패 시 콘솔/토스트에 **Realtime 미기동(npm run dev)** 안내 문구 보강
- 프론트 실시간·API 베이스 URL: `localhost`/`127.0.0.1` 고정 제거 → 페이지 호스트·프로토콜 기준으로 자동(`API`는 `origin`, 소켓은 동일 호스트 `:3001`), `ech-realtime-url` 메타·`ech_realtime_url` localStorage로 덮어쓰기 가능. 소켓 `connect_error` 시 우하단 안내 토스트 1회 표시
- Realtime `saveMessage`: `channel_members.user_id`가 `users.id`(bigint)인 DB에서 사번만으로는 멤버십이 맞지 않아 소켓 전송이 실패하던 문제 — `employee_no` 조인·텍스트 비교 병행
- 메시지 전송: 소켓 실패 후 API 폴백 **성공 시** 불필요한 시스템 안내 문구 제거, ACK 타임아웃 8초로 완화
- 타임라인 쿼리에 `parentMessage.sender` fetch 추가 → `replyToSenderName` 누락·「원글에게 답장」표시 완화
- 댓글 개수 집계: 루트 직계 COMMENT·REPLY 및 **댓글에 달린 REPLY**까지 포함해 `threadCommentCount`가 0으로만 나오던 경우 보완
- 스레드 모달에서는 메인용 「N개의 댓글」요약 버튼을 숨겨(1개 댓글일 때) 중복/혼란을 방지
- 댓글 전송 시(스레드 모달) 채팅창이 아래로 점프하지 않도록 스크롤 위치를 유지하도록 수정
- 스레드 모달 댓글 입력창: 가로 폭을 최대화하도록 CSS 조정
- 채팅방 멤버 패널: 조직/직급 값이 비어있을 때 직위/직책 fallback으로 최소 정보 노출
- 멘션 토스트: Realtime 서버에서 멘션 수신자를 채널 멤버로 필터할 때 DB 타입 차이(bigint vs employee_no)로 인해 누락되던 문제 수정
- Realtime 멘션 수신자 필터: `channel_members.user_id`가 `users.id(bigint)`로 저장된 경우에도 `employee_no`로 정규화해 정확히 토스트 전송
- 채널 멤버의 조직/직급 표시: `org_group_members.member_group_type` 대소문자 불일치에 대비해 memberGroupType 비교를 case-insensitive로 보강
- 채팅 전송: 소켓 실패 후 API 폴백으로 메시지는 저장되는데도 `message:error`가 중복 토스트로 남던 문제를 억제
- 채팅 발화 구분: 시스템 메시지/날짜선 바로 뒤에서 같은 사용자가 말해도 새 발화(avatar/time 묶음)가 시작되도록 realtime 렌더 경계 처리 보강
- 채팅방 멤버 패널: `department/jobLevel`에 placeholder(예: `TEAM`) 값이 내려오는 경우 비워서 frontend fallback(직위/직책 표시)되게 처리
- 채널 멤버 조직/직급 조회: `member_group_type` 비교를 `TRIM+LOWER`로 강화하고, JPA 누락 시 `org_group_members + org_groups` JDBC fallback 조회로 보강
- 채팅방 멤버 패널 UI: 조직/직급 텍스트를 `조직/직급: ...` 형식으로 명확히 노출하고 스타일(색/굵기/블록) 가시성을 강화
- 채팅방 멤버 패널 UI: 멤버 행에 조직/직급을 버튼 외부의 별도 고정 라인(`member-org-line`)으로도 렌더해 브라우저/테마 스타일 영향 없이 항상 노출
- 채팅 내역 메시지 헤더(아바타/이름 옆): 현재 채널 멤버 조직/직급 캐시를 붙여 발신자 이름 아래 보조 라인(`msg-sender-sub`)으로 노출
- 채팅 내역 로드 오류 수정: `createMessageRowElement`에서 `emp` 선언 전 참조로 발생하던 런타임 오류를 변수 선언 순서 정정으로 해결
- 멤버 패널 문구 정리: `조직/직급: ...` 접두를 제거하고 값만 표시
- 채팅 발신자 헤더: 이름 아래 줄이 아니라 이름 옆(같은 줄)으로 조직/직급(`msg-sender-sub`) 배치
- 멤버 패널: 조직/직급 중복 표시 제거(`member-org-line`만 유지), 직위/직책(`member-position-txt`/`member-duty-txt`)은 조직/직급 라인 다음에 표시하도록 순서 조정
- 채널 진입/갱신 시 조직/직급 라벨 안정화: `loadChannelMembers` 완료 후 기존 메시지 DOM의 발신자 라벨을 재동기화(`syncSenderOrgLabelsInMessageList`)하고, `selectChannel`에서 멤버 로드 완료를 기다려 표시가 나왔다/안나왔다 반복되는 현상 완화
- 채널 구성원 추가 권한: `POST /api/channels/{channelId}/members`에서 PUBLIC/PRIVATE는 개설자만 허용, DM은 개설자 여부와 무관하게 허용
- 우측 멤버 패널: 채널 개설자에게 `개설자` 배지 노출, 구성원 추가 버튼은 권한 없을 때 숨김(채널 진입 시 기본 숨김 후 멤버 로드 시 규칙 적용)
- 날짜 경계 발화 보정: 날짜가 바뀌면 발신자가 같아도 새 발화로 처리되도록 `shouldShowAvatarForMessage`/`shouldShowMessageTime`에 날짜 비교 조건 추가
- Realtime 멤버십/멘션 필터 SQL: `users.id = channel_members.user_id` 비교를 `::text` 기반으로 통일해 `varchar/bigint` 혼합 스키마에서 `42883` 오류로 소켓 전송 지연·멘션 토스트 누락이 나던 문제 수정
- 멘션 입력 UX: 자동완성 선택 시 입력창에는 `@임보안`만 보이고, 전송 시에만 `@{emp|임보안}` 토큰으로 변환

### Added
- 채팅 타임라인: 원글 하단에 **댓글 N개 + 마지막 댓글 시각** 요약(클릭 시 스레드 모달), 답글 모드 시 **입력창 위 답장 미리보기 바**(대상·내용 스니펫·닫기)
- 타임라인 **REPLY** 행: 답글 대상을 **「표시명에게 답장」+ 원문 스니펫** 카드로 표시(`replyToSenderName` API 필드)
- 사이드바 채널/DM 목록 영역에 **미확인 멘션 목록** 섹션 추가: 멘션 토스트 수신 시(현재 보고 있는 채널 제외) 항목 누적, 항목 클릭 시 해당 채널 이동 + 멘션 메시지 강조 스크롤 + 목록에서 확인 처리

### Fixed
- 채팅 메시지 로드: `GET .../messages/timeline`이 404(구버전 백엔드 등)일 때 `GET .../messages`로 자동 폴백해 채팅을 읽을 수 있게 함
- `MessageController`: `GET /timeline` 매핑을 `/{parentMessageId}/replies` 앞에 배치해 경로 매칭 안정화

## 2026-03-30

### Changed
- **@멘션**: 자동완성을 전사 `GET /api/users/search` 대신 **현재 채널 `members`만** 필터; 토스트 `#mentionToastStack`을 `body` 직계로 옮겨 `.app-layout overflow:hidden` 클리핑 방지, `z-index: 1200`
- **Realtime**: `mention:notify`를 `io.sockets.sockets.get(sid).emit`으로 전송, 사번 키 `trim` 정규화

### Added
- **@멘션**: 본문 `@{사번|표시명}`, 채널 멤버 검증 후 `mention:notify`(Realtime `message:send`·Java `MentionNotificationService`·`POST /internal/notify-mentions`), 프론트 우하단 토스트·클릭 시 채널 이동·렌더 `@이름` 강조

### Fixed
- 워크스페이스 **ECH** 옆 미동작 `⌄` 버튼 제거(혼란 방지)
- 멘션 토스트 미표시: 현재 채널 `message:new` 수신 시 내 멘션을 폴백 토스트로 처리해 `mention:notify` 누락/지연에도 알림 표시
- 멘션 토스트 가독성: 폰트/패딩/최대 너비를 확장
- 관리자 `error_logs` 조회: `path`가 `bytea`로 저장된 DB에서 `lower()` 검색이 실패하던 문제를 `convert_from` 기반 변환으로 수정
- 관리자 `error_logs` 조회: `from/to/errorCode` 미지정(null) 시 Postgres 파라미터 타입 추론 문제를 `COALESCE`로 완화
- 로그인 실패(`POST /api/auth/login`)와 `/favicon.ico` 같은 프론트 404는 불필요한 `error_logs` 적재에서 제외
- 채널/DM 섹션 접기 시 화살표 **▾↔▸** 전환(`syncSectionToggleChevron`·`section-toggle-chevron`)
- 스레드 타임라인 통합 테스트 안정화: `isReply` 직렬화 불일치에 대비해 `messageType(REPLY_*)` 기반으로 REPLY/ROOT 분기 판정

### Added
- 퀵 레일 **DM**에도 사이드바와 동일 **프레즌스 점**(`dmSidebarLeadingHtml`, `refreshPresenceDots`)

### Changed
- **퀵 레일 위치**: `#quickContainer`를 `mainApp` 형제에서 **사이드바 내부** `.sidebar-body`로 이동 — **ECH 워크스페이스 헤더 전폭 최상단**, 퀵은 **검색~채널/DM 목록과 같은 세로 구간**만 차지(프로필 바 제외). 펼침 너비 **324px**(64+260).
- **퀵 목록 로직**: 미읽음만 표시 → **미읽음 우선 정렬 후 최근 대화**까지 최대 15개(`QUICK_RAIL_MAX_ITEMS`), 배지는 미읽음에만.
- **사이드바 접힘**: 너비 0 대신 **64px**(퀵 레일만, 워크스페이스·목록·프로필 숨김).

### Added
- 좌측 하단 본인 프레즌스 클릭 → **온라인 / 자리비움** 선택 팝업·`presence:set` 전송; 창 포커스 복귀 시 `AWAY` 선택 유지 후 재전송
- DM 사이드바 프레즌스: `GET /api/channels` 요약에 `dmPeerEmployeeNos`(조회자 제외 멤버 사번 배열), 프론트 DM 줄에 `presence-dot`·`refreshPresenceDots` 연동; 좌측 하단 본인 상태 줄은 `● 온라인` 본문 중복 제거(CSS `::before`만)·실제 프레즌스와 라벨 동기화(`refreshSidebarUserStatusLine`)
- **채널/DM 미읽음 배지**: `GET /api/channels` 요약에 `unreadCount`(멤버별 `channel_read_states` 이후 **루트 메시지** 건수), `MessageRepository.countRootMessagesAfter`; 프론트 사이드바 빨간 원형 숫자(99+ 상한), 채팅 로드·실시간 `message:new`/`channel:system`(열람 중)·API 전송 폴백 시 `PUT .../read-state`, 타 채널 메시지는 디바운스 목록 갱신·윈도우 포커스 시 갱신

### Fixed
- **프레즌스 상태 메뉴 미표시/미적용**: 메뉴를 `user-info` 밖(`sidebar-user-presence-host`)으로 옮겨 잘림 방지; 목록에서 항목 선택 시만 적용(토글 아님); 옵션 `stopPropagation`·`data-presence-status` 읽기; 바깥 닫기는 bubble
- **멀티 창·계정 프레즌스 어긋남**: `presence:set` 직후 해당 소켓에 `presence:snapshot`으로 서버 전체 상태 전달, 로그인 시 프레즌스 맵 초기화, 연결 시 `presence:set`→`GET /presence` 순서 정리, 창 포커스·`visibilitychange` 시 스냅샷+`presence:set` 재동기화(`scheduleSidebarAndPresenceSync`) — `realtime/src/server.js`, `frontend/app.js`
- **실시간 채팅 날짜 구분선·같은 분 묶음**: `appendMessageRealtime`가 `lastElementChild`만 보다 시스템 메시지 직후에는 직전 채팅 행을 못 찾아 날짜 변경 시 구분선 미표시·아바타/시간 묶음이 어긋나던 문제 — `findLastChatRowIn`/`lastTimelineDateKey`로 판단; `channel:system`은 `createdAt` 있을 때 구분선 정합(`appendSystemMsg`)
- **DM 사이드바 표시명**: `GET /api/channels` 요약의 `description`이 DM 생성 시 한쪽 기준으로만 저장되어 상대 계정에선 자기 이름만 보이던 문제 — `channelType=DM`일 때 조회 중인 `employeeNo`를 제외한 멤버 표시명을 요약 `description`으로 계산 (`getMyChannels`)
- `AuditLogService.safeRecord`: 내부 `this.record()` 호출로 `@Transactional(REQUIRES_NEW)`가 무시되어 감사 INSERT 실패(또는 기타 예외) 시 **호출자 트랜잭션이 rollback-only**로 남고 `UnexpectedRollbackException`이 나던 문제 — `@Lazy` 자기 프록시로 `record()`만 별도 트랜잭션에 두고 `safeRecord`에서는 트랜잭션 미부여(멤버 내보내기 등에서 감사 실패가 본 요청을 깨지 않도록)

### Changed
- `docs/sql/migrate_user_refs_id_to_employee_no.sql`: PostgreSQL에서 Hibernate 등이 생성한 임의 이름 FK가 남은 채 `DROP COLUMN` 하면 실패할 수 있어, 본문 전에 `users`/`users.id`를 참조하는 대상 컬럼 FK를 `DO` 블록으로 선제 제거
- `docs/HANDOVER.md`: `GET /api/channels` DM 요약 `description`(조회자 제외 멤버 표시명) 동작을 API 목록에 명시

### Fixed
- **로컬 PostgreSQL 레거시 스키마**: `channel_members.user_id`가 아직 `bigint`(`users.id`)인 DB에서 `GET /api/channels` JPQL이 `bigint = varchar`로 깨지던 문제 — `information_schema`로 컬럼 타입을 검사하고, 레거시일 때만 `users.id`로 조인하는 JDBC 보조 쿼리로 목록 조회 (`ChannelMemberUserIdColumnInspector`)
- 채팅방 메시지·스레드 답글 로드: 레거시 DB처럼 `messages.sender_id` 또는 `channel_members.user_id`가 여전히 `bigint`(`users.id`)인 경우, 채널 메시지 목록·스레드 답글·멤버십 확인에서 JPA `JOIN`/`existsBy…EmployeeNo`가 `bigint = varchar`로 실패하던 문제 — Inspector로 `sender_id`/멤버 `user_id` 정수 FK 여부를 검사하고, 레거시일 때만 `users.id` 조인 JDBC로 `MessageResponse`를 조회·멤버십을 확인하도록 `MessageService` 보강
- 레거시 FK 감지 안정화: `ChannelMemberUserIdColumnInspector`가 `information_schema`에서 `current_schema()` 한정으로만 컬럼 타입을 찾다 테이블이 `public` 등 다른 스키마에만 있을 때 레거시를 놓치던 경우 — `public` 우선·대소문자 무관 매칭으로 스키마를 넓혀 `bigint`/`employee_no` 불일치 JDBC 분기가 켜지도록 수정
- 채널 생성(`POST /api/channels`): 생성자 조회를 요청 본문 `createdByEmployeeNo`가 아니라 **JWT(로그인 계정) 사원번호**로만 수행해, 세션/본문 불일치 시 「생성자를 찾을 수 없습니다」가 나던 문제 방지. `createdByEmployeeNo`는 선택 필드(하위 호환)로 완화
- JWT에 DB 사용자 id(`uid` 클레임) 포함, `UserPrincipal`에 `userId` 추가. 채널 생성·`/api/auth/me`·테마·검색 등에서 **uid 우선 → 사원번호 → 레거시(숫자-only subject를 DB id로 간주)** 순으로 사용자를 식별해, 구형 토큰/숫자 subject와 사번 불일치로 「생성자를 찾을 수 없습니다」가 반복되던 케이스 완화
- 500 「서버 내부 오류」 완화: JWT `uid`를 문자열·숫자 모두 파싱, `getThemePreference`·`/api/auth/me`의 빈 사번 방어, 채널 단건 조회 시 `createdBy`·멤버 `user` **JOIN FETCH**, `toResponse` 생성자 사번 null-safe, 빈 사원번호는 `IllegalArgumentException`(400)으로 처리, `DataIntegrityViolationException`·`LazyInitializationException` 전용 예외 응답 추가
- 500 원인 추적: 전역 핸들러에서 미처리 예외 **ERROR 로그+스택**, `app.expose-error-detail`(기본 true, `EXPOSE_ERROR_DETAIL`로 끔) 시 응답 메시지에 예외 요약 첨부, `HttpMessageNotReadableException`→400, JWT 필터는 유효 Bearer 시 **항상** SecurityContext 설정·role null 방어. 프론트 초기화 시 `/api/auth/me` 비정상 응답을 `console.error`로 출력

### Added
- 멤버 내보내기 시 **SYSTEM** 메시지를 DB에 저장하고 Node에 `channel:system` 브로드캐스트(내부 HTTP `/internal/broadcast-channel-system`)해 채널에 있는 모든 클라이언트가 동일 시스템 문구를 보게 함; 프론트는 API 목록·소켓 모두 `messageType=SYSTEM`/`channel:system` 처리 및 `messageId` 중복 제거
- **채널 참여**(`POST .../members`) 시에도 동일하게 `SYSTEM` 본문(「이름」님이 채널에 참여했습니다)·`channel:system` 브로드캐스트; 구성원 추가 UI 확인 후 `loadMessages`로 동기화
- 채널 **개설자** 전용 구성원 내보내기: `DELETE /api/channels/{channelId}/members?targetEmployeeNo=`(JWT=개설자), `channel_members`·`channel_read_states` 정리, 감사 `CHANNEL_MEMBER_REMOVED`; 프론트 멤버 패널에「내보내기」버튼
- 채팅 이미지 첨부 UX: FILE 메시지 JSON에 `contentType` 저장, 프론트에서 이미지 인라인 표시·라이트박스 확대·확대 화면 다운로드, 작성 중 이미지 미리보기 썸네일
- 채팅창에서 클립보드 이미지 붙여넣기(Ctrl+V): `viewChat` 캡처 단계 `paste`로 처리, 기존 `pendingFile`/업로드·전송 흐름 재사용(모달 열림 시 제외)

### Changed
- `docs/FEATURE_SPEC.md` 전반: 채널·메시지·읽음·파일·칸반·업무·프레즌스·실시간 소켓 서술을 `employeeNo`/`actorEmployeeNo`/`createdByEmployeeNo` 등 현재 API DTO와 일치하도록 정리
- `docs/HANDOVER.md` 6절 API 목록·채널/식별자/프론트 메모를 `employeeNo`·`dmPeerEmployeeNos`·메시지 `senderId`(사번) 등 현재 구현과 일치하도록 정리
- 사용자 프로필 API: `GET /api/users/profile?employeeNo=` 추가(기존 `userId` 쿼리·경로형 유지), 프론트 프로필·멤버 패널을 사번 기준으로 조회
- Realtime: 프레즌스 키를 `userId`에서 `employeeNo`로 전환(`presence:set`/`presence:update`/GET `/presence` 응답), `message:send`의 `senderId`를 사원번호 문자열로 검증·저장
- 프론트 `app.js`: 프레즌스·메시지 발신·채널 멤버 추가·관리자 릴리즈/설정 요청을 `employeeNo` 계약에 맞춤; 메시지/멤버 UI `data-employee-no` 사용

## 2026-03-27

### Fixed
- 비핵심 API 전환: 칸반/릴리즈/설정/보존정책 DTO와 서비스 입력 식별자를 `employeeNo` 기반으로 확장하고, 관련 응답 필드(`createdBy`, `assignee`, `uploadedBy`, `updatedBy`)를 사번 기준으로 정합화
- API 계약 2차 전환: 채널/메시지/파일/읽음상태 핵심 경로의 요청 식별자를 `employeeNo` 중심으로 전환하고, 관련 서비스/리포지토리 멤버십 체크를 `employee_no` 기준으로 정합화
- 인증 토큰 principal: `userId` 제거 후 `employeeNo`를 JWT subject로 사용하도록 변경, 테마 설정 조회/수정도 사번 기준으로 처리
- 통합 테스트 정합: `AuthApiTest`/`ChannelApiTest`/`JwtUtilTest`를 `employeeNo` 계약 변경에 맞춰 수정
- 사용자 참조 FK 전환: `channels/channel_members/messages/channel_files/channel_read_states` 및 `kanban/work` 사용자 연관 `@JoinColumn`이 `users.id` 대신 `users.employee_no`를 참조하도록 매핑 변경
- Realtime 메시지 저장: `senderId(users.id)` 입력을 내부에서 `employee_no`로 변환해 `channel_members.user_id` 멤버십 검사/`messages.sender_id` 저장이 employee_no 기준으로 동작하도록 수정
- DB 스키마 초안: 사용자 참조 컬럼(`created_by/user_id/sender_id/uploaded_by/actor_user_id`) 정의를 `users.id` FK에서 `users.employee_no` FK로 정합화
- DM/채널 메시지 전송 안정화: 실시간 소켓 ACK 실패·지연 또는 소켓 미연결 시 `POST /api/channels/{channelId}/messages` API로 자동 폴백해 메시지 전송이 끊기지 않도록 보강
- DM 생성 안정화: `DataInitializer`가 특정 이름 1개가 아니라 `channels.channel_type`에 걸린 모든 CHECK 제약을 탐지/교체해 `DM` 허용 제약(`PUBLIC/PRIVATE/DM`)을 일관되게 재구성하도록 보강
- 조직도 팝업(멤버 피커): 다크(검정) 테마에서 상단 검색 유형 셀렉트·검색 입력 글자색이 어두워 가독성이 떨어지던 문제 수정(`--text-primary`·배경·placeholder 명시)

## 2026-03-27

### Changed
- 채널 생성: 오래된 DB 제약(`channels_channel_type_check`)이 DM을 허용하지 않아 DM 생성이 실패하던 문제를 기동 시 자동 보정하도록 `DataInitializer`에 제약 재생성 로직 추가
- Git: Windows에서 경로 공백 시 커밋 메시지 인코딩이 깨지는 문제를 방지하기 위해 `tools/git-editor-reword-msg.sh`·`tools/fix-theme-commit-messages.sh`(Git Bash UTF-8 reword) 사용 절차를 문서화하고, `main` 최근 5개 커밋 메시지를 재작성한 뒤 원격에 반영

### Added
- SQL 마이그레이션: `migrate_channels_allow_dm_type.sql` (`channels.channel_type` 체크 제약에 `DM` 포함)
- SQL 마이그레이션: `migrate_user_refs_id_to_employee_no.sql` (`users.id` 참조 컬럼 데이터를 `employee_no` 기반으로 이관)

## 2026-03-26 (7차)

### Changed
- 테마 설정 UI를 로그인 사용자 라인(로그아웃 옆 톱니바퀴) 팝업 방식으로 변경하고, 선택 즉시 적용되도록 수정
- 사용자별 테마를 DB(`users.theme_preference`)에 저장하도록 변경 (`PUT /api/auth/me/theme`, `GET /api/auth/me`/로그인 응답에 `themePreference` 포함)
- 감사로그: `safeRecord`를 `REQUIRES_NEW` 트랜잭션으로 실행해 read-only 요청 흐름(첨부파일 다운로드/다운로드정보 조회)에서도 INSERT 실패 없이 기록되도록 수정
- `users`에서 회사·본부·팀·직무 중복 컬럼 제거(조직은 `org_groups`/`org_group_members` 단일 출처)
- `org_group_members` FK를 `users.id` 대신 **`users.employee_no`** 로 변경
- 조직 그룹 타입: `JOB_LEVEL`(직급), **`JOB_POSITION`**(직위), **`JOB_TITLE`**(직책) — `DUTY_TITLE` 제거(마이그레이션 스크립트로 이행 가능)
- API/프론트: `jobRank`/`dutyTitle` → `jobLevel`/`jobPosition`/`jobTitle` (`department`는 TEAM 표시명 유지)
- org code 생성 규칙을 난수/해시 기반에서 가독 코드 기반으로 변경 (`ORGROOT`, `0_JobLevel`, `0_L100` 등)
- `JOB_LEVEL`/`JOB_POSITION`/`JOB_TITLE` 그룹에 `member_of_group_code`/`group_path` 계층을 부여하도록 동기화 로직 변경
- company/division/team 코드를 더 짧은 형태로 축약 (`ORG`, `EXT`, `INFRA`, `OPS_ITOPS` 등)
- 팀 코드 중복 표기 정리 (`QA_QA`/`PLAN_PLAN` -> `QA_QA_TEAM`/`PLAN_PLAN_TEAM`)
- 팀 코드 생성 시 본부 코드와 중복되는 접두는 제거하여 단일화 (`PLAN_PLAN_TEAM` -> `PLAN_TEAM`, `QA_QA_TEAM` -> `QA_TEAM`)
- 조직도 팝업 멤버 검색: 검색어가 있으면 "선택된 회사" 내 전체 팀 구성원을 대상으로 필터링 (팀 선택 의존 제거)
- 조직도 트리: 부서 인원수 표시(`ech-tree-count`) 제거
- 조직도 팝업: 구성원 선택 결과(내가 고른 사용자) 표시 및 선택 취소 버튼(X) 추가
- 조직도 팝업: 선택 사용자 목록을 본문에서 하단 `선택 완료` 버튼 라인으로 이동하고 별도 제목 텍스트 제거
- 채팅: 시스템 메시지(`msg-system`)가 사이에 끼면 다음 사용자 메시지를 새 발화로 처리(아바타/이름 재노출)

### Added
- PostgreSQL 마이그레이션: `migrate_users_drop_org_columns.sql`, `migrate_org_group_members_user_id_to_employee_no.sql`, `migrate_org_duty_title_to_job_title.sql`
- PostgreSQL 마이그레이션: `migrate_users_add_theme_preference.sql`

## 2026-03-26 (3차)

### Changed
- 조직도 회사 셀렉트·트리: `company_key`만 묶지 않고 **`company_key` + `company_name` 조합**별 옵션 및 `GET /organization?companyKey=&companyName=` 필터(빈 `companyName`은 회사명 미입력 행만)

## 2026-03-26 (4차)

### Added
- `org_groups` / `org_group_members` DDL·백필 스크립트(`docs/sql/create_org_groups.sql`, `create_org_group_members.sql`, `backfill_org_groups_from_users.sql`, `backfill_org_group_members_from_users.sql`)

### Changed
- 조직도/조직도 회사 셀렉트: `company_key/company_name` 기반에서 `org_groups` 기반으로 전환 및 `GET /api/user-directory/organization?companyGroupCode=` 계약 적용
- 사용자 검색(department): `users.department` 대신 `org_group_members(TEAM)` + `org_groups.display_name` 조인 기반으로 변경
- 로그인/채널 멤버 응답의 `department` 값을 `org_group_members(TEAM)`의 팀 표시명으로 주입
- `OrgSyncService.syncUsers()`: 외부 사용자 동기화 시 `org_groups`/`org_group_members`까지 함께 upsert하도록 확장
- 프론트 `frontend/app.js`의 조직도 회사 셀렉트/트리 요청 파라미터를 `companyGroupCode`로 수정
- `UserDirectoryApiTest`를 H2 제약(ON CONFLICT 미지원) 하에서 org 테이블을 직접 시드하도록 보강

## 2026-03-26 (5차)

### Changed
- `users.company_key` -> `users.company_code`로 컬럼/코드 명칭 교체
- `org_groups` 부모관계를 `parent_group_id/company_group_id`에서 `member_of_group_code`로 단순화
- `org_group_members.group_id`를 `org_group_members.group_code`로 교체

## 2026-03-26 (6차)

### Changed
- `org_groups.group_code`: 순수 MD5(hex) 대신 **ASCII pretty 코드**(COMP/DIV/TEAM/JOB/DUT + MD5 접두)로 통일(`OrgGroupCodes`, `OrgSyncService`, org 백필 SQL)

## 2026-03-26 (2차)

### Added
- `GET /api/user-directory/organization-filters` — ACTIVE 사용자 기준 `company_key` 그룹별 대표 `company_name`으로 조직도 팝업 회사 셀렉트 옵션 생성
- 조직도 팝업 HTML/CSS를 그룹웨어 `ui_window`(제목바·툴바·body_root·푸터) 형태로 정렬

### Changed
- 회사 셀렉트 고정 라벨(코비젼 등) 제거 → API로 DB 반영 라벨 사용
- `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`, `README.md`

## 2026-03-26

### Added
- `docs/sql/migrate_users_add_org_columns.sql`: `users`에 `company_name` / `division_name` / `team_name` 컬럼 추가(기존 DB용 idempotent `ALTER`)
- `docs/sql/backfill_users_org_hierarchy.sql`: 시드와 동일한 사번별 조직 값 백필(외부 계정·미등록 사번 보조 로직 포함)
- `docs/sql/update_users_org_from_seed.sql`: `seed_test_users.sql` 과 동일한 사번별 회사/본부/팀만 `UPDATE` (다른 컬럼 미변경)
- `users.company_key`(조직도 회사 필터) 및 `GET /api/user-directory/organization?companyKey=` — `ORGROOT`/미지정 시 전체, `GENERAL`/`EXTERNAL`/`COVIM365` 등으로 ACTIVE 사용자 제한
- `docs/sql/migrate_users_company_key.sql`, `docs/sql/update_users_company_key_from_seed.sql`
- 프론트 `modalAddMemberPicker`: 회사 셀렉트 + AXTree 스타일 좌측 조직도

### Changed
- `docs/DB_SCHEMA.md`: `users` 명세에 조직 3컬럼·`company_key` 반영
- `docs/sql/seed_test_users.sql`, `docs/sql/postgresql_schema_draft.sql`: `company_key` 포함
- `ExternalOrgUser` / 조직 동기화 UPSERT: `company_key` 반영
- `README.md`, `docs/HANDOVER.md`, `docs/ENVIRONMENT_SETUP.md`, `docs/FEATURE_SPEC.md`: 마이그레이션·백필·회사 필터 절차 안내

## 2026-03-25 (17차)

### Fixed
- `ChannelType` enum에 `DM` 추가 — `ChannelService` DM 생성 분기(`channelType == DM` + `dmPeerUserIds`)가 컴파일·실행되도록 정합 (`./gradlew test` 통과)

### Changed
- `frontend/app.js`: 미사용 `createFileAttachmentRow` / `appendUploadedFileMessage` 제거 — 첨부 메시지 행은 `createFileAttachmentRowFromMsg`·`loadMessages` 경로만 사용

## 2026-03-25 (18차)

### Changed
- 프론트: 채널/DM 생성 시 구성원 선택을 `+` 버튼 기반 팝업(`modalAddMemberPicker`)으로 통일
- 프론트: 멤버 추가 팝업에서 검색 결과가 현재 컨텍스트(채널 생성/DM 생성/기존 채널 추가)에 맞는 선택 목록에 반영되도록 수정

## 2026-03-25 (19차)

### Changed
- 프론트: `modalAddMemberPicker` UI를 스크린샷 형태(좌측 조직도 트리 + 우측 검색/결과)로 재구성
- 백엔드: 조직도 생성 시 `division_name`/`team_name`이 비어 있으면 `users.department` 문자열에서 본부/부서를 유추하도록 보완

## 2026-03-25 (20차)

### Changed
- 프론트: `modalAddMemberPicker` 팝업 크기 확대 및 좌측 조직도/우측 부서원 영역 레이아웃 고정
- 프론트: 검색 UI를 팝업 상단 1줄로 단일화(좌/우 박스 내부 검색 제거)

## 2026-03-25 (16차)

### Changed
- 파랑 테마를 `화이트 베이스 + 블루 포인트`로 조정(앱/사이드바/채팅/입력 배경을 밝은 톤으로 변경, 주요 버튼·액센트만 파란색 유지)

## 2026-03-25 (15차)

### Fixed
- Windows/PowerShell에서 한글 커밋 메시지가 깨지던 `chore` 커밋을 UTF-8 메시지 파일 + `git commit --amend -F` 로 수정 후 원격 반영(`--force-with-lease`)
- `.gitignore`에 `fix_msg_utf8.txt` 추가(에디터가 생성하는 임시 커밋 메시지 파일 추적 방지)

## 2026-03-25 (14차)

### Added
- 프론트: **테마 선택** — 검정(기본 다크)·하양(라이트)·파랑(다크 블루/시안), `localStorage`(`ech_theme`) + `data-theme` 연동, 로그인·사이드바에 칩 UI

### Changed
- `styles.css`: 테마별 CSS 변수 세트(`html[data-theme="light"]` / `blue`), 그라데이션·사이드바·포커스 링을 변수로 통일

### Docs
- `docs/FEATURE_SPEC.md`, `README.md`, `docs/HANDOVER.md`

## 2026-03-25 (13차)

### Added
- 백엔드: 사용자·조직 트리용 `company_name` / `division_name` / `team_name` 및 `GET /api/user-directory/organization` 응답 `{ companies: [...] }` (회사→본부→팀→사용자)
- 프론트: 채팅 날짜 구분선, 첨부를 일반 메시지 행과 동일 레이아웃으로 표시, 구성원 추가·채널·DM용 **검색+조직도 통합 피커** (`.picker-unified`)
- 프론트: 모달·관리자·검색·조직도·첨부 블록까지 이어지는 **다크 refined** 테마 (`styles.css` CSS 변수)

### Changed
- `docs/sql/seed_test_users.sql`, `postgresql_schema_draft.sql`: 회사/본부/팀 샘플·컬럼 반영
- `UserDirectoryApiTest`: `$.data.companies` 기준 검증

### Docs
- `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`, `README.md` — 조직 API·UI 반영

## 2026-03-25 (12차)

### Added
- `users.job_rank`(직위), `users.duty_title`(직책) 컬럼 및 JPA·프로필/검색/채널 멤버 API(`jobRank`, `dutyTitle`) 반영
- 조직 동기화(TEST)·`seed_test_users.sql`에 직위·직책 샘플 값 반영

### Changed
- 프론트: 프로필 모달에 직위(항상 행, 값 없으면 `-`), 직책은 값이 있을 때만 표시
- 프론트: 채널 멤버 패널에 부서·직위 한 줄 요약, 직책은 있을 때만 추가 줄
- 프론트: 채팅 메시지·멤버 패널에서 프레즌스 점을 아바타 네모 우측 하단에 배치(`.msg-avatar-wrap`, `.member-avatar-wrap`)

### Changed (에이전트 룰)
- `core-rules.mdc` Git 섹션에 **작업 완료 시 커밋·푸시(자동)** 절차 통합(기존 별도 `auto-commit-push.mdc` 내용 흡수 후 해당 파일 제거)

## 2026-03-25 (11차)

### Added
- 프론트엔드: 프로필 모달 `DM 보내기` 버튼(기존 DM 만들기와 동일하게 `POST /api/channels` + 멤버 추가 후 해당 DM으로 전환)
- 프론트엔드: 구성원 검색 옆 `조직도` 버튼 + 트리형 조직도 팝업(체크박스 다중 선택 후 한 번에 추가)
- 프론트엔드: 채널 헤더에 `구성원 추가` 기능(채널 생성 이후 멤버 추가 모달)
- 프론트엔드: 채널 헤더에 `첨부파일 모아보기` 모달
- 프론트엔드: 파일 업로드 완료 시 채팅 본문에 첨부 카드(파일명/크기/다운로드) 메시지 표시

### Changed
- 프론트엔드: 프로필 모달에서 역할·계정 상태 항목 제거(동료 정보는 이름·사원번호·이메일·부서 중심)
- 백엔드: `ChannelMemberResponse`에 `department` 추가
- 프론트엔드: 멤버 리스트에서 `MANAGER/MEMBER` 대신 조직 정보(부서) 소형 텍스트 노출
- 프론트엔드: 로그아웃 버튼 클릭 시 즉시 로그아웃하지 않고 확인 대화상자 표시

## 2026-03-25 (10차)

### Fixed
- Spring Boot 기본 `/**` 정적 리소스 매핑이 `/api/**` 를 가로채 `NoResourceFoundException`(api/users/profile, api/user-directory/organization 등)이 나던 문제 수정
- `spring.web.resources.add-mappings: false` + `FrontendResourceConfig` 로 `index.html` / `styles.css` / `app.js` 만 명시 노출, `/` → `/index.html` 리다이렉트

## 2026-03-25 (9차)

### Added
- 백엔드: `GET /api/user-directory/organization` — 조직도용 부서별 사용자 목록(정적 리소스와 `/api/users/*` 충돌 회피)

### Changed
- 백엔드: `GET /api/users/organization` 제거(프론트는 `user-directory` 경로 사용)
- 백엔드: 채널 파일 다운로드 — `FileSystemResource`·실제 파일 크기·유효하지 않은 Content-Type 시 `application/octet-stream`, 미존재 시 `NotFoundException`(404), 스토리지 base 이탈 경로 차단
- 프론트엔드: 조직도 요청 URL을 `/api/user-directory/organization`으로 변경

## 2026-03-25 (8차)

### Added
- 백엔드: `GET /api/users/profile?userId=` — 프로필 조회(쿼리형, 프론트 기본 연동)

### Changed
- 프론트엔드: 프로필 요청을 경로형 `/{userId}/profile` 대신 쿼리형으로 전환(404 회피)

## 2026-03-25 (7차)

### Added
- 백엔드: `GET /api/users/organization` — ACTIVE 사용자를 부서별로 묶어 조직도 선택 UI에 사용
- 백엔드: `GET /api/users/{userId}/profile` — 동료 프로필 조회(멤버 권한)
- 프론트엔드: 채팅 화면 **첨부 파일** 목록(접기/펼치기) + JWT 기반 다운로드
- 프론트엔드: 메시지 발신자·멤버 패널에서 **프로필 모달**, **프레즌스 점** 표시(소켓 `presence:update` + `GET /presence` 스냅샷)
- 프론트엔드: 채널/DM 만들기 시 **조직도에서 선택**(부서별 접기)

### Changed
- 백엔드: `ChannelFileResponse`에 `uploaderName` 추가, 채널 파일 디스크 경로를 `channels/{workspaceKey}_ch{channelId}_{slug}/yyyy/mm/` 형태로 저장(기존 `channels/{channelId}/...` 경로의 `storageKey`는 그대로 다운로드 가능)
- 백엔드: `GET /api/users/search` — 키워드에 부서 부분 일치, 숫자만 입력 시 사용자 ID 일치 포함

## 2026-03-25 (6차)

### Changed
- 프론트엔드: 채팅 시각 — **24시간제 HH:mm** (`fmtTime`), 오전/오후 문구 제거. 시각을 패널 오른쪽 끝이 아니라 **본문 직후** 인라인 배치(`.msg-content-row` 블록 + 인라인 `span`)

## 2026-03-25 (5차)

### Changed
- 프론트엔드: 채팅 메시지 시각 표시 규칙 — 동일 발신자·동일 분(로컬) 연속 메시지는 **마지막 줄에만** 시각 표시, 분이 바뀌면 줄마다 시각 표시(Slack 유사). `msg-content-row` 레이아웃으로 본문·시간 정렬

## 2026-03-25 (4차)

### Changed
- `docs/HANDOVER.md` — 로컬·개발 환경 서버 재시작 절(구성요소, 기동/중지 순서, PowerShell 예시, 접속 확인) 최하단 추가

## 2026-03-25

### Added
- 백엔드: `GET /api/channels?userId=` — 사용자가 참여한 채널 목록 조회 API 추가
- 백엔드: `GET /api/channels/{id}/messages?userId=&limit=` — 채널 메시지 내역 조회 API 추가
- 백엔드: `ChannelSummaryResponse` DTO 신규 생성
- 백엔드: `ChannelMemberResponse`에 `name` 필드 추가
- 백엔드: `MessageResponse`에 `senderName` 필드 추가

### Changed
- 백엔드: `ChannelController.createChannel()` `@RequireRole` MANAGER → MEMBER (모든 사용자 채널 생성 허용)
- 백엔드: `ChannelController.joinChannel()` `@RequireRole` MANAGER → MEMBER
- 백엔드: `UserSearchController` `@RequireRole` MANAGER → MEMBER (모든 사용자 동료 검색 허용)
- 프론트엔드: `index.html` Slack 스타일 레이아웃으로 완전 재작성
- 프론트엔드: `app.js` 완전 재작성 (채널 사이드바, 메시지 히스토리, 실시간 소켓, 파일 업로드 통합, 채널/DM 생성 모달)
- 프론트엔드: `styles.css` Slack 스타일 완전 재작성

### Fixed
- 메시지 보내기 — 채널 선택 후 소켓을 통해 정상 전송
- 배포관리 탭 — 일반 사용자에게 노출되지 않도록 사이드바 ADMIN 전용 섹션으로 이동
- 첨부파일 — 현재 채팅방에서만 업로드 (채널 ID 수동 입력 제거)
- 연속 메시지(같은 발신자) UI — `.msg-continued`에 `padding-left`와 `msg-spacer`가 겹쳐 이중 들여쓰기 되던 문제 수정 (`styles.css`), `senderId` 숫자 정규화 및 채널 전환 시 `lastSenderId` 초기화 (`app.js`)
- 리얼타임 — `saveMessage` 전 `channel_members` 멤버십 검사, 비멤버 시 `NOT_CHANNEL_MEMBER` (`db.js`, `server.js`)
- 운영 참고 — 개발용 직접 INSERT 메시지 정리 예시 `docs/sql/cleanup_dev_messages.sql` 추가, `FEATURE_SPEC.md` 실시간 절 보완

## 2026-03-24 (2차)

### Fixed
- `UnauthorizedException` 추가 및 `AuthService` 로그인 실패 시 401 반환 처리
- `NotFoundException` 추가 및 `ChannelService.getChannel()` 채널 미존재 시 404 반환 처리
- `GlobalExceptionHandler`에 401/404 핸들러 추가
- `SecurityConfig`에 `authenticationEntryPoint(HttpStatusEntryPoint(UNAUTHORIZED))` 추가 — JWT 미제공/만료 시 403 대신 401 반환
- `ChannelApiTest` JSON 필드명 오타 수정 (`"type"` → `"channelType"`)

## 2026-03-24

### Added
- 초기 프로젝트 스캐폴드 생성 (`backend`, `realtime`, `frontend`)
- 헬스체크 API 추가 (`/api/health`)
- 실시간 메시지 기본 이벤트(`channel:join`, `message:send`, `message:new`) 구현
- 스캐폴드 상세 문서 추가 (`docs/PROJECT_SCAFFOLD.md`)
- 기본 Cursor 룰 반영: 한글 응답, 변경점 기록, 에러 기록
- 에러 기록 파일 추가 (`ERRORS.md`)
- 기능/조건 기준 문서 추가 (`docs/PROJECT_REQUIREMENTS.md`)
- 기본 룰 파일명 변경 및 통합 룰 적용 (`.cursor/rules/core-rules.mdc`)
- 개발 로드맵 문서 추가 (`docs/ROADMAP.md`)
- 기능명세서 템플릿 문서 추가 (`docs/FEATURE_SPEC.md`)
- 인수인계 템플릿 문서 추가 (`docs/HANDOVER.md`)
- PostgreSQL 스키마 초안 SQL 추가 (`docs/sql/postgresql_schema_draft.sql`)
- 채널별 읽음 포인터 API (`GET/PUT /api/channels/{channelId}/read-state`) 및 `channel_read_states` 도메인/스키마 초안
- 채널 파일 메타데이터 API(`channel_files`, 목록/등록/download-info) 및 스키마 초안
- Realtime Presence 소켓 단위 추적·전원 연결 종료 시 OFFLINE 정리, 메시지 본문 길이 상한·Socket 버퍼 상한, `pg` Pool 타임아웃 옵션
- Frontend 데모 메시지 DOM 상한(200건), Backend Hikari 풀 환경변수 연동
- 칸반 보드 도메인(`kanban_*` 테이블, `/api/kanban/*` CRUD·담당·이력 API)
- 메시지 기반 업무 항목(`work_items`, `POST/GET /api/messages/{id}/work-items`, `GET /api/work-items/{id}`)
- 로컬 테스트용 사용자·부서 시드 SQL(`docs/sql/seed_test_users.sql`, 관리자·다양한 부서/역할/INACTIVE·부서NULL 포함) 및 `ENVIRONMENT_SETUP` 안내
- 조직 동기화 인터페이스 API(`OrgUserProvider`, `TEST` 제공자, `GET/POST /api/admin/org-sync/users*`, 사용자 상태 변경 API) 추가
- RBAC 어노테이션/인터셉터(`@RequireRole`, `RoleGuardInterceptor`) 및 `docs/RBAC_MATRIX.md` 추가
- 운영 오류 로그(`error_logs` 테이블, 전역 예외 저장, 관리자 조회 API `/api/admin/error-logs`) 추가

### Changed
- `.gitignore`에 로컬 Gradle 배포 산출물(`tools/gradle-8.10-bin.zip`, `tools/gradle-8.10/`) 제외 규칙 추가
- `core-rules.mdc` 커밋 규칙 중복 섹션을 통합해 단일 "Git 커밋 규칙"으로 가독성 개선
- `core-rules.mdc`에 "Git 커밋 메시지 규칙" 보강(UTF-8 파일 커밋/수정, 인코딩 설정 유지, 메시지 요약 원칙)
- `core-rules.mdc` 커밋 규칙 보강: 커밋 직후 한글 깨짐 검증, PowerShell `-m`/`commit -F` 인코딩 위험, UTF-8 파일+Git Bash·`tools/rewrite-head-commit-message.sh` 안내, `i18n.commitEncoding` 권장
- `tools/rewrite-head-commit-message.sh` 추가(Git Bash에서 HEAD 메시지를 UTF-8 파일로 안전히 재작성)
- `tools/rebuild-head-with-staged.sh` 추가(스테이징된 트리로 HEAD 커밋만 UTF-8 메시지와 함께 재구성)
- `docs/HANDOVER.md`에서 Git 커밋 전용 절 제거, 개발자·운영 관점 빠른 이해(2-1)로 정리
- `README.md`를 Docker 미사용 기준으로 정리
- 빠른 시작 가이드를 OS별(Spring 실행)로 명확화
- 실시간 서버에서 `Express`/`cors` 의존성 제거, `http + socket.io` 구조로 단순화
- DB 기준을 PostgreSQL 단일 기준으로 정리 (`README.md`, `docs/PROJECT_REQUIREMENTS.md`)
- 변경기록 관리 파일을 `.cursor/rules/CHANGELOG`로 통합
- 커서 룰의 변경 기록 대상 경로를 `.cursor/rules/CHANGELOG`로 변경
- Realtime 서버 정책을 `Express 미사용`으로 명시
- 변경/에러 이력 경로를 `.cursor/rules/CHANGELOG.md`, `.cursor/rules/ERRORS.md`로 명확히 통일
- 문서 참조 경로 정리 (`README.md`, `docs/PROJECT_REQUIREMENTS.md`, `docs/PROJECT_SCAFFOLD.md`, `.cursor/rules/changelog-ai-log.mdc`)
- 프로젝트 모티브를 Slack/Flow/Teams로 명시하고 해당 기준으로 작업 원칙 반영
- `core-rules.mdc`에 커밋 규칙 추가(기능 단위 커밋, 한글 메시지, 내용 기반 메시지, 브랜치 전략 사용자 확인)
- `core-rules.mdc`에 커밋 메시지 형식 규칙 추가(type + 한글 제목, 타입별 기준, 예시)
- 로드맵 기반 작업/완료 시 취소선 처리 규칙 반영 (`.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`, `README.md`)
- Backend 공통 응답 포맷(`ApiResponse`) 및 전역 예외 처리(`GlobalExceptionHandler`) 추가, `HealthController` 응답 포맷 통일
- 로드맵 Phase 1 항목 `기본 에러 처리/공통 응답 포맷 적용` 완료 처리(취소선 반영)
- 개발 완료 후 기능명세서/인수인계서를 지속 상세 업데이트하는 규칙 반영 (`.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`, `docs/ROADMAP.md`, `README.md`)
- 원격 `main` pull 과정에서 `README.md` 충돌 해결 후 로컬 기준 문서 구조를 유지하며 병합 반영
- 기능명세서/인수인계서에 DB 스키마(사용자/채널/메시지) 상세 반영 (`docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`)
- README 문서 링크에 DB 스키마 초안 경로 추가 (`docs/sql/postgresql_schema_draft.sql`)
- 로드맵 Phase 1 항목 `PostgreSQL 스키마 초안 작성` 완료 처리(취소선 반영)
- 채널 도메인 API 1차(`생성/조회/참여`) 구현 및 관련 도메인 엔티티/리포지토리/DTO/서비스/컨트롤러 추가
- 로드맵 Phase 1 항목 `채널 도메인 API 1차 구현` 완료 처리(취소선 반영)
- 기능명세서/인수인계서에 채널 API 상세 반영 (`docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`)
- 로드맵 완료 표기 방식을 취소선에서 체크박스(`[x]`)로 변경 (`docs/ROADMAP.md`, `.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`)
- 로드맵을 단계/세부 작업 단위로 상세 세분화 (Phase별 1차/2차 하위 작업 정의)
- 로드맵 완료 표기 방식을 체크박스 `[x]`에서 `[v]`로 변경
- 로드맵 하위 작업(`1-1-1`, `1-1-2` 등)을 상위 항목 하단으로 들여쓰기 정렬
- 문서 가독성 개선(README 문서 섹션 정리) 및 관리자 버전 업그레이드 관리 요구사항 추가
- 요구사항/로드맵/기능명세/인수인계서에 관리자 릴리즈 업로드(WAR), 버전 전환, 롤백, 감사로그 항목 반영
- Realtime 메시지 저장 연계 구현(`pg` 기반 DB 저장 -> 저장 성공 시 `message:new` 브로드캐스트) 및 프론트 입력 payload(`channelId/senderId`) 정렬
- 로드맵 Phase 1의 `1-5` 하위 항목 완료 처리(`[v]`)
- 기능명세서/인수인계서에 Realtime 메시지 저장 연계 상세 반영
- Java 17 설치 및 `JAVA_HOME`/`Path` 적용, Backend Gradle Wrapper 생성/검증으로 Phase 1의 `1-1`/하위 항목 완료 처리
- 로컬 환경 설정 상세 문서 추가 (`docs/ENVIRONMENT_SETUP.md`) 및 README/HANDOVER/FEATURE_SPEC 연동
- 메시지/스레드 API 추가 (`POST /messages`, `POST /replies`, `GET /replies`) 및 메시지 도메인(`Message`) 구현
- 로드맵 Phase 2의 `2-1`, `2-1-1`, `2-1-2` 완료 처리(`[v]`)
- 기능명세서/인수인계서에 스레드 답글 기능 상세 반영
- 조직도(부서) 기반 사용자 검색 API(`GET /api/users/search`) 구현
- Realtime 사용자 Presence 기능(`presence:set`, `presence:update`, `GET /presence`) 구현
- 로드맵 항목 `2-2-2`, `3-1-3` 완료 처리(`[v]`) 및 관련 문서 반영
- 새 기능 추가 시 `README.md`를 적절한 섹션에 함께 업데이트하도록 문서 규칙 강화 (`core-rules.mdc`, `PROJECT_REQUIREMENTS.md`, `HANDOVER.md`)
- README에 누락된 최신 기능 반영: 조직도 기반 사용자 검색, Presence 기능, 메시지/스레드/사용자검색 API 및 Realtime 이벤트 요약 추가
- 로드맵 Phase 2 항목 `2-2`, `2-2-1` 완료 처리(`[v]`), 기능명세/인수인계/README 동기화
- 로드맵 Phase 2 항목 `2-3`, `2-3-1`, `2-3-2` 완료 처리(`[v]`), 문서/환경 예시 동기화
- 로드맵 Phase 2 항목 `2-4`, `2-4-1`, `2-4-2` 완료 처리(`[v]`), 기능명세/인수인계/README/스키마 초안 동기화
- 로드맵 Phase 2 항목 `2-5`, `2-5-1`, `2-5-2` 완료 처리(`[v]`), 기능명세/인수인계/README/스키마 초안 동기화
- 로드맵 Phase 3 항목 `3-1`, `3-1-1`, `3-1-2` 완료 처리(`[v]`), 테스트 조직 우선 연동/추후 그룹웨어 전환 기준 문서화
- 로드맵 Phase 3 항목 `3-2`, `3-2-1`, `3-2-2` 완료 처리(`[v]`), 역할-권한 매트릭스 및 API 단 권한 체크 반영
- 로드맵 Phase 3 항목 `3-3-0`(오류 로그 기반) 완료 처리(`[v]`), 3-3-1/3-3-2는 후속
- 감사 이벤트 로그 도메인(`audit_logs` 테이블, `AuditEventType` Enum, `AuditLog` 엔티티/리포지토리) 추가
- `AuditLogService`(이벤트 기록·REQUIRES_NEW 트랜잭션·`safeRecord` 래퍼·검색) 구현
- 관리자 감사 로그 조회 API(`GET /api/admin/audit-logs`, 기간/행위자/이벤트유형/리소스/워크스페이스 필터) 추가
- `ChannelService`, `MessageService`, `ChannelFileService`, `WorkItemService`, `KanbanService`에 감사 로그 연동(`safeRecord` 호출)
- 로드맵 Phase 3 항목 `3-3-1`, `3-3-2` 완료 처리(`[v]`)
- 보존 정책/아카이빙 기능 구현 (Phase 3-4 완료)
  - `retention_policies` 테이블 및 `RetentionResourceType` Enum 추가 (MESSAGES/AUDIT_LOGS/ERROR_LOGS)
  - `messages.archived_at` 컬럼 추가 (소프트 아카이브)
  - `RetentionPolicyService`: 정책 CRUD + 아카이빙 실행 로직
  - `ArchivingScheduler`: 매일 02:00 활성 정책 자동 실행 (`@Scheduled`)
  - `RetentionPolicyController`: 관리자 API (`GET/PUT /api/admin/retention-policies`, `POST /trigger`, `POST /trigger/{resourceType}`)
  - `DataInitializer`: 서버 기동 시 기본 보존 정책 자동 시드
  - `AuditEventType` `RETENTION_POLICY_UPDATED`, `DATA_ARCHIVED` 추가
  - `SchedulingConfig` (`@EnableScheduling`) 추가
  - 로드맵 Phase 3-4 완료 처리(`[v]`)
- 관리자 배포 관리 기능 구현 (Phase 3-5 완료)
  - `release_versions`, `deployment_history` 테이블 추가
  - `ReleaseVersion`, `DeploymentHistory` 엔티티/리포지토리 (`domain/release/`)
  - `ReleaseStatus`, `DeploymentAction` Enum 추가
  - `ReleaseService`: WAR/JAR 업로드(SHA-256 체크섬), 활성화, 롤백, 삭제, 이력 조회
  - `ReleaseController`: 7개 관리자 API (`/api/admin/releases`)
  - `AuditEventType` RELEASE_UPLOADED/ACTIVATED/ROLLED_BACK/DELETED 추가
  - `application.yml` 멀티파트 파일 크기 설정(기본 500MB), `app.releases-dir` 추가
  - 프론트엔드: 탭 네비게이션 + 배포 관리 화면(목록/업로드/활성화/이력)
  - 로드맵 Phase 3-5 완료 처리(`[v]`)
- docs/DB_SCHEMA.md: 전체 DB 구조 상세 명세서 신규 작성
  - DB 기본 정보, 확장 모듈, 전체 18개 테이블 상세 명세
  - 인덱스 전체 목록 (28개), Enum 값 정의, ERD 텍스트, 보존 정책, 시드 데이터, 운영 주의사항
- Phase 4 안정화 및 품질 완료
  - 4-1-1: Spring Boot 통합 테스트 (H2 인메모리, AuthApiTest/ChannelApiTest/SearchApiTest/JwtUtilTest)
  - 4-1-2: GitHub Actions CI 파이프라인 (.github/workflows/ci.yml)
  - 4-2-1: k6 부하 테스트 스크립트 (tools/k6/load-test.js, message-stress-test.js)
  - 4-2-2: HikariCP 튜닝 (min-idle, max-lifetime, keepalive-time), DB Pool 에러 핸들링 강화
  - 4-3-1: realtime pg Pool 에러 핸들링, gracefulShutdown, 재연결 재시도 로직
  - 4-3-2: socket.io 재연결 설정 강화, message:send ACK 콜백 적용, 메시지 rate limit
  - 4-4-1: docs/DEPLOY.md (배포/롤백/체크리스트)
  - 4-4-2: docs/MONITORING.md (알람 임계치/헬스체크/부하 기준/장애 대응)
  - 로드맵 Phase 4 전체 완료 처리([v])
- 파일 스토리지 경로 설정 및 실제 파일 업로드/다운로드 구현
  - app_settings 테이블, AppSetting 엔티티/리포지토리, AppSettingKey 상수 추가
  - AppSettingsService: DB 우선 + yml 폴백 스토리지 경로 조회/변경
  - AppSettingsController: GET/PUT /api/admin/settings (ADMIN 전용)
  - ChannelFileService: 실제 파일 저장 (channels/{id}/{YYYY}/{MM}/{UUID}_{name}), 다운로드
  - ChannelFileController: POST /upload(multipart), GET /{id}/download 추가
  - DataInitializer: file.storage.base-dir=D:/testStorage 자동 시드
  - application.yml: app.file-storage-dir 추가 (기본 D:/testStorage)
  - 프론트엔드: 채널 파일 업로드/목록/다운로드 UI + 관리자 설정 탭
- 통합 검색 기능 구현 (Phase 3-6 완료)
  - PostgreSQL `pg_trgm` 확장 + GIN 인덱스 SQL 추가 (성능 튜닝 기준)
  - `SearchType` Enum(ALL/MESSAGES/FILES/WORK_ITEMS/KANBAN_CARDS), `SearchResultItem`, `SearchResponse` DTO
  - `MessageRepository`, `ChannelFileRepository` 채널 멤버십 기반 검색 쿼리 추가
  - `WorkItemRepository`, `KanbanCardRepository` 키워드 검색 쿼리 추가
  - `SearchService`, `SearchController` (GET /api/search, JWT 인증 필요)
  - 프론트엔드: 헤더 검색바 + 결과 모달(타입 필터, 타입별 배지 색상)
  - 로드맵 Phase 3-6 완료 처리(`[v]`)
- JWT 기반 로그인 인증 구현 (Phase 3-0 신규 추가 및 완료)
  - `SecurityConfig` (Spring Security 정책: Stateless JWT, CORS 허용)
  - `JwtUtil` (토큰 발급/검증), `JwtAuthFilter` (OncePerRequestFilter), `UserPrincipal` (인증 주체)
  - `AuthProvider` 인터페이스 + `TestAuthProvider` (로컬 BCrypt 검증, 그룹웨어 전환 대비)
  - `AuthService`, `AuthController` (`POST /api/auth/login`, `GET /api/auth/me`)
  - `DataInitializer`: 기동 시 비밀번호 미설정 사용자에게 `Test1234!` 자동 적용
  - `User` 엔티티에 `password_hash` 컬럼 추가, `users` DB 스키마 반영
  - `RoleGuardInterceptor` SecurityContext 우선 + X-User-Role 헤더 폴백
  - 프론트엔드: 로그인 화면, JWT sessionStorage 저장, 자동 Authorization 헤더 첨부, 로그아웃

### Removed
- Docker 기반 실행 파일 제거 (`docker-compose.yml`)
- 중복 변경이력 파일 제거 (`docs/CHANGELOG_AI.md`, `CHANGELOG`)
- 루트 에러 파일 제거 (`ERRORS.md`)
- 구 룰 파일 제거 (`.cursor/rules/changelog-ai-log.mdc`)
