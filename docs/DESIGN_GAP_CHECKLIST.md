# 목업 대비 UI 갭 체크리스트

`design/ECH메인`·`design/ECH화면설계 (1)~(9)`의 Stitch 산출물과 실제 앱(`frontend/`) 사이의 **남은 정합 작업**을 추적합니다.  
완료 항목은 `[x]`, 미검증·미작업은 `[ ]`로 표시해 두고, 작업 시 갱신합니다.

**관련**: 구역 ↔ 목업 매핑은 [DESIGN_SYSTEM.md §6](./DESIGN_SYSTEM.md#6-화면설계-19--앱-구역-매핑) 참고.

---

## 1) 검증 방법 (권장)

- **대조 소스**: 각 폴더의 `screen.png`(또는 브라우저에서 `code.html` 열기)와 로컬 앱 동일 플로우를 나란히 비교합니다.
- **테마**: 기본 라이트(`html[data-theme="light"]`) 우선, 다크는 별도 행으로 표시합니다.
- **뷰포트**: 데스크톱(≥1280px), 태블릿(~768px), 좁은 폭(≤420px)에서 레이아웃·스크롤·모달 전환을 확인합니다.
- **상태**: 빈 목록, 로딩, 에러 토스트, 긴 텍스트·다수 항목 등 **목업에 없는 상태**는 별도 UX 판단이 필요함을 메모합니다.

---

## 2) 전역 · 토큰 (ECH메인 / 공통 DESIGN.md)

`DESIGN.md`의 **Chromatic Sanctuary** 원칙과의 정합입니다.

| 항목 | 메모 |
|------|------|
| [x] **No-Line / 고스트 보더**: 섹션 구분이 톤·여백 위주인지, 불필요한 실선 1px가 남았는지 점검 | 사이드바·카드·테이블 경계 — 라이트·다크 공통 토큰 정리(2026-04-12) |
| [x] **타이포 스케일**: Inter, 헤드라인/라벨(대문자·자간) 계열이 목업 대비 일관적인지 | `html[data-theme="light"]` — `--ech-tracking-headline`·`--ech-tracking-label`; 관리자 헤더·모달 제목·설정/인사이트 라벨·로그인 제목 |
| [x] **Primary CTA 그라데이션**: 135° `primary` → `primary_container` 느낌이 버튼에 반영됐는지 | `.btn-primary` — `var(--grad-btn-primary)` + 라이트/다크 섀도(`styles.css`) |
| [x] **글래스 모달**: 떠 있는 패널·모달의 blur·반투명 레이어가 목업과 유사한지 | 주요 `modal-overlay` — 다크에서 모달 본체·스크림 blur 보강 |
| [x] **활성 채널 표시**: 좌측 “필 인디케이터 + 은은한 배경” 패턴 적용 여부 | `.sidebar-item.active` — 다크 인디케이터 글로우 |

---

## 3) 구역별 체크리스트

### ECH메인 (`design/ECH메인/`)

| 앱 위치 | 반영 요지 | 체크 |
|---------|-----------|------|
| `#appShellTopBar` | 글로벌 바·검색·아이콘 정렬 | [x] 다크 글래스·검색 포커스 |
| `#viewWelcome` | 히어로·3열 카드·바로가기·Pro Tip (`.ech-welcome-*`) | [x] 다크 카드 호버·슬레이트 아이콘 랩 |
| `#sidebarColumn` | 3열 유동 레이아웃·사이드바 톤 | [x] 활성 행 글로우(다크) |

### `(1)` ECH Workspace — Work Management

| 앱 위치 | 체크 |
|---------|------|
| `#modalWorkHub` 레이아웃·헤더·닫기 | [x] 다크 패널·셸 보더 · 라이트 `ech-workhub-shell` 글래스·인디고 섀도 |
| `.work-hub-body--split`(넓은 화면) 업무 목록 ↔ 칸반 2열 비율·스크롤 | [x] 구조 유지·다크 `work-hub-panel` · 라이트 패널 No-Line·층 섀도 |
| 업무 탭/칸반 도구 모음이 목업 밀도와 맞는지 | [x] 다크 칸반 컬럼·카드 톤 · 라이트 칸반 컬럼/카드 소프트 섀도 |

### `(2)` 워크스페이스 셸 + 멤버/조직 맥락 · `design/ECH채팅`

| 앱 위치 | 체크 |
|---------|------|
| `#viewChat`(`data-ech-design-ref="ech-chat"`) · `.ech-chat-header` · `.ech-messages-wrap` · `.ech-composer-glass` | [x] 다크 헤더·타임라인·날짜선·글래스 컴포저 |
| `.ech-composer-bar` 입력·첨부·전송 정렬 | [x] 다크 컴포저 바·툴바·포커스 |
| `#appHeaderSearchInput` + 헤더 검색 동작 | [x] 다크 포커스(글로벌 바와 동일 토큰) |
| `#quickContainer` 퀵 레일·미읽음·`#mentionList` 멘션 목록 밀도 | [x] 다크 퀵 레일 인셋·`mention-suggest` 글래스 · 라이트 멘션 제안 패널 글래스·호버 틴트 |
| `#memberPanel` 햄버거 메뉴·액션 버튼 줄 | [x] 다크 멤버 패널(이전 스프린트) |

### `(3)` Enterprise Admin Hub

| 앱 위치 | 체크 |
|---------|------|
| `.ech-region--admin` 공통 패널 헤더·배경 | [x] 다크 패널 헤더 글래스 |
| 사이드바 관리자 섹션(`#adminSection`) 진입·강조 | [x] 다크 섹션 라벨 · `(7)` 톤: `sidebar-section--admin` 구분선·글래스 틴트, Material 아이콘 레일, `syncAdminSidebarActive`로 현재 관리 뷰와 `.active` 동기화(2026-04-09) |
| `#viewReleases` 인사이트 카드·`release-layout`(업로드/목록 2단)·이력 테이블 | [x] 다크 `release-upload-card`·`release-panel--main` |

### `(4)` Stratos Pro 상단 네비

| 앱 위치 | 체크 |
|---------|------|
| `#appShellTopBar`와 `ECH메인` 목업 상단 바 시각적 일치도 | [x] 다크 글로벌 바(동일 토큰) · 라이트 상단 메뉴(`대시보드/프로젝트/팀`) 버튼 톤 반영 |
| `#btnTopNavDashboard` / `#btnTopNavProjects` / `#btnTopNavTeam` 동작(환영·업무 허브·조직도) 및 활성 탭 동기화 | [x] `app.js` 연동(2026-04-09) |
| `#btnTopNavProjects`·환영 업무 카드: 채널 없을 때 기본 채널 자동 선택 후 업무 허브 | [x] `getDefaultChannelForWorkHub`·`openWorkHubFromTopNav`(2026-04-09) |

### `(5)` Organization Directory

| 앱 위치 | 체크 |
|---------|------|
| `#viewOrgManagement` 인사이트 카드 + `org-tab-rail` / `org-tab-main` | [x] 다크 레일·본문·활성 탭 인셋 |
| `#modalOrgChart` · `#modalOrgGroupEdit` | [x] 다크 `.orgchart-member-card` |
| `#modalAddMemberPicker` 조직도 오버레이 | [x] 라이트 — `.ech-orgmap-window` 글래스·16px 라운드·인디고 섀도 (`styles.css`) |

### `(6)` Task & Kanban Management

| 앱 위치 | 체크 |
|---------|------|
| `#channelKanbanBoard` 컬럼·카드·빈 상태 | [x] 다크 칸반 컬럼·카드 · 라이트 No-Line·컬럼 제목 자간 |
| `#modalKanbanCardDetail` | [x] 공통 `.modal` 다크 토큰 |

### `(7)` User Management

| 앱 위치 | 체크 |
|---------|------|
| `#viewUserManagement` 인사이트 카드·테이블·분할 패널 | [x] 다크 인사이트·분할·조직 패널 |
| `#modalAdminUserEdit` | [x] 공통 `.modal` 다크 토큰 |

### `(8)` Enterprise Hub — Settings

| 앱 위치 | 체크 |
|---------|------|
| `#viewSettings` Hero + 좌우 2열(`settings-layout`)·반응형 1열 | [x] 다크 히어로·캔버스·설정 카드 |

### `(9)` 보조 패널 · 프로필형 카드 UI

| 앱 위치 | 체크 |
|---------|------|
| `#modalUserProfile` 히어로·부제·`.profile-dl--cards`·푸터 | [x] 다크 아바타·카드 필드 |
| `#searchModal` 통합 검색 결과 레이아웃·타이포 | [x] 다크 툴바·쿼리 입력 · 라이트 툴바 인셋·쿼리 고스트 포커스·제목 자간 |
| 기타 보조 모달(`#modalThemePicker`, `#modalAppUpdate` 등) | [x] 공통 `.modal`·`.modal-overlay` 다크 토큰 |

---

## 4) 매핑 표에 없는 주요 화면

목업 단일 폴더에 대응되지 않지만 동일 토큰으로 맞출 항목입니다. (배포 `#viewReleases`는 관리자 허브 성격상 `(3)`과 함께 검증.)

| 화면 | ID / 영역 | 체크 |
|------|-----------|------|
| 로그인 | `#loginPage` | [x] 다크 로그인 카드 글래스 |
| 스레드 | `#modalThread` | [x] 공통 모달 다크 토큰 · 라이트 답글 스레드 세로선 고스트 |
| 첨부/이미지 허브 | `#modalFileHub`, `#modalImagePreview` | [x] 다크 토큰 · 라이트 `#modalFileHub` 탭 No-Line / 라이트 이미지 오버레이 블러·닫기 글래스 |
| 테마 선택 | `#modalThemePicker` | [x] 라이트 `.theme-option-btn`·선택 그라데이션 링 |
| 멘션 토스트 | `.mention-toast`·`.mention-toast-notice` | [x] 라이트 글래스·인디고 섀도 |
| 사용자 검색 목록 | `.user-search-results`(모달 내) | [x] 라이트 인셋 고스트·소프트 섀도 |
| 스레드 허브 | `#modalThreadHub` | [x] 공통 모달 다크 토큰 · 라이트 `.thread-hub-row` 카드 톤 |
| 채널/DM 생성 | `#modalCreateChannel`, `#modalCreateDm`, `#modalAddChannelMembers` | [x] 공통 모달 다크 토큰 |
| 업무 항목 상세 | `#modalWorkItemDetail` | [x] 공통 모달 다크 토큰 |
| 공통 다이얼로그 | `#modalAppDialog`, `#modalImageDownloadChoice` | [x] 다크 토큰 · 라이트 `app-dialog` 글래스 헤더·입력 포커스 / 다운로드 선택 버튼 톤 분리 |

---

## 5) 우선순위 가이드 (참고)

- **P0**: 사용자가 매일 보는 영역 — 채팅 `#viewChat`, 사이드바, 글로벌 바, 환영 `#viewWelcome`.
- **P1**: 관리자·설정·사용자·조직·배포 뷰와 핵심 모달(검색, 프로필, 업무·칸반).
- **P2**: 엣지 케이스 모달, 다운로드 선택, 앱 업데이트 안내 등.

---

## 6) 변경 이력 (문서)

- 문서 신설 및 `DESIGN_SYSTEM.md` §7 링크 시 `.cursor/rules/CHANGELOG.md`에 기록합니다.
- 2026-04-10: `(3)`에 `#viewReleases`·`(2)`에 퀵 레일/멘션 행 보강, §4 배포 뷰 검증 안내 추가.
- 2026-04-11: P0 시각 일괄 개선 — 글로벌 바·모달 스크림·멤버 패널·컴포저 포커스·Primary 버튼·환영 카드 보더·사이드바 섹션 라벨(다크 포함). `DESIGN_SYSTEM.md` §8 MCP+Stitch 절차 추가.
- 2026-04-11(2): 관리자 인사이트 카드 호버·조직 탭 레일·통합 검색 모달·보조 버튼·컴포저 전송·환영 히어로 섀도.
- 2026-04-12: 다크 테마 P0 시각 정합 — `styles.css` `html:not([data-theme="light"])`로 채팅·글로벌 바·로그인·모달·관리자·설정·조직·사용자·검색·업무 허브 셸 보강. §2·§3·§4 일부 `[x]` 처리.
- 2026-04-12(2): 다크 P1 — Primary CTA 섀도, `work-hub-panel`·배포 카드·칸반·`mention-suggest`·프로필 카드·퀵 레일·`#adminSection`·조직도 멤버 카드. §3·§4 추가 `[x]`.
- 2026-04-09(2): `(4)` 상단 **프로젝트**·환영 **업무·칸반** — 채널 미선택 시 기본 채널 선택 후 업무 허브(`openWorkHubFromTopNav`).
- 2026-04-09(3): `(3)` `#adminSection` — 아이콘 레일·구역 구분·`showView`와 관리 메뉴 활성 동기화.
- 2026-04-09(4): `(3)` 관리 캔버스 — 사이드바 관리 구역 상단 여백·`.view-admin` 패딩·패널 헤더/힌트/인사이트 그리드 리듬(`styles.css`).
- 2026-04-09: §4 보조 UI — 파일 허브·이미지 프리뷰·테마 피커·멘션 토스트·사용자 검색 목록·공통 다이얼로그·업데이트 안내 라이트 톤 (`styles.css`).

---

## 7) 다음 작업으로 추천되는 순서 (신규 담당자용)

1. **§2 전역 토큰** 한 바퀴(특히 No-Line·CTA·모달 글래스) — 이후 구역 작업 시 재작업을 줄임.  
2. **P0**(`§5`) — `#viewChat`, 사이드바, `#appShellTopBar`, `#viewWelcome`.  
3. **P1** — 관리자 뷰(`#viewUserManagement`, `#viewOrgManagement`, `#viewReleases`, `#viewSettings`) + `#modalWorkHub`, `#modalUserProfile`, `#searchModal`.  
4. **P2** — §4 보조 모달·로그인.  
5. 체크 완료 시 해당 행을 `[x]`로 바꾸고, 스크린샷·PR에 구역 ID를 명시하면 추적이 쉬움.
