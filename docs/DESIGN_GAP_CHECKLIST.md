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
| [ ] **No-Line / 고스트 보더**: 섹션 구분이 톤·여백 위주인지, 불필요한 실선 1px가 남았는지 점검 | 사이드바·카드·테이블 경계 |
| [ ] **타이포 스케일**: Inter, 헤드라인/라벨(대문자·자간) 계열이 목업 대비 일관적인지 | `styles.css` 변수·헤딩 클래스 |
| [ ] **Primary CTA 그라데이션**: 135° `primary` → `primary_container` 느낌이 버튼에 반영됐는지 | `.btn-primary` 등 |
| [ ] **글래스 모달**: 떠 있는 패널·모달의 blur·반투명 레이어가 목업과 유사한지 | 주요 `modal-overlay` |
| [ ] **활성 채널 표시**: 좌측 “필 인디케이터 + 은은한 배경” 패턴 적용 여부 | `.sidebar-list` 선택 행 |

---

## 3) 구역별 체크리스트

### ECH메인 (`design/ECH메인/`)

| 앱 위치 | 반영 요지 | 체크 |
|---------|-----------|------|
| `#appShellTopBar` | 글로벌 바·검색·아이콘 정렬 | [ ] |
| `#viewWelcome` | 히어로·3열 카드·바로가기·Pro Tip (`.ech-welcome-*`) | [ ] |
| `#sidebarColumn` | 3열 유동 레이아웃·사이드바 톤 | [ ] |

### `(1)` ECH Workspace — Work Management

| 앱 위치 | 체크 |
|---------|------|
| `#modalWorkHub` 레이아웃·헤더·닫기 | [ ] |
| `.work-hub-body--split`(넓은 화면) 업무 목록 ↔ 칸반 2열 비율·스크롤 | [ ] |
| 업무 탭/칸반 도구 모음이 목업 밀도와 맞는지 | [ ] |

### `(2)` 워크스페이스 셸 + 멤버/조직 맥락

| 앱 위치 | 체크 |
|---------|------|
| `#viewChat` · `.ech-chat-header` · `.ech-messages-wrap` | [ ] |
| `.ech-composer-bar` 입력·첨부·전송 정렬 | [ ] |
| `#appHeaderSearchInput` + 헤더 검색 동작 | [ ] |
| `#quickContainer` 퀵 레일·미읽음·`#mentionList` 멘션 목록 밀도 | [ ] |
| `#memberPanel` 햄버거 메뉴·액션 버튼 줄 | [ ] |

### `(3)` Enterprise Admin Hub

| 앱 위치 | 체크 |
|---------|------|
| `.ech-region--admin` 공통 패널 헤더·배경 | [ ] |
| 사이드바 관리자 섹션(`#adminSection`) 진입·강조 | [ ] |
| `#viewReleases` 인사이트 카드·`release-layout`(업로드/목록 2단)·이력 테이블 | [ ] |

### `(4)` Stratos Pro 상단 네비

| 앱 위치 | 체크 |
|---------|------|
| `#appShellTopBar`와 `ECH메인` 목업 상단 바 시각적 일치도 | [ ] |

### `(5)` Organization Directory

| 앱 위치 | 체크 |
|---------|------|
| `#viewOrgManagement` 인사이트 카드 + `org-tab-rail` / `org-tab-main` | [ ] |
| `#modalOrgChart` · `#modalOrgGroupEdit` | [ ] |
| `#modalAddMemberPicker` 조직도 오버레이 | [ ] |

### `(6)` Task & Kanban Management

| 앱 위치 | 체크 |
|---------|------|
| `#channelKanbanBoard` 컬럼·카드·빈 상태 | [ ] |
| `#modalKanbanCardDetail` | [ ] |

### `(7)` User Management

| 앱 위치 | 체크 |
|---------|------|
| `#viewUserManagement` 인사이트 카드·테이블·분할 패널 | [ ] |
| `#modalAdminUserEdit` | [ ] |

### `(8)` Enterprise Hub — Settings

| 앱 위치 | 체크 |
|---------|------|
| `#viewSettings` Hero + 좌우 2열(`settings-layout`)·반응형 1열 | [ ] |

### `(9)` 보조 패널 · 프로필형 카드 UI

| 앱 위치 | 체크 |
|---------|------|
| `#modalUserProfile` 히어로·부제·`.profile-dl--cards`·푸터 | [ ] |
| `#searchModal` 통합 검색 결과 레이아웃·타이포 | [ ] |
| 기타 보조 모달(`#modalThemePicker`, `#modalAppUpdate` 등) | [ ] |

---

## 4) 매핑 표에 없는 주요 화면

목업 단일 폴더에 대응되지 않지만 동일 토큰으로 맞출 항목입니다. (배포 `#viewReleases`는 관리자 허브 성격상 `(3)`과 함께 검증.)

| 화면 | ID / 영역 | 체크 |
|------|-----------|------|
| 로그인 | `#loginPage` | [ ] |
| 스레드 | `#modalThread` | [ ] |
| 첨부/이미지 허브 | `#modalFileHub`, `#modalImagePreview` | [ ] |
| 스레드 허브 | `#modalThreadHub` | [ ] |
| 채널/DM 생성 | `#modalCreateChannel`, `#modalCreateDm`, `#modalAddChannelMembers` | [ ] |
| 업무 항목 상세 | `#modalWorkItemDetail` | [ ] |
| 공통 다이얼로그 | `#modalAppDialog`, `#modalImageDownloadChoice` | [ ] |

---

## 5) 우선순위 가이드 (참고)

- **P0**: 사용자가 매일 보는 영역 — 채팅 `#viewChat`, 사이드바, 글로벌 바, 환영 `#viewWelcome`.
- **P1**: 관리자·설정·사용자·조직·배포 뷰와 핵심 모달(검색, 프로필, 업무·칸반).
- **P2**: 엣지 케이스 모달, 다운로드 선택, 앱 업데이트 안내 등.

---

## 6) 변경 이력 (문서)

- 문서 신설 및 `DESIGN_SYSTEM.md` §7 링크 시 `.cursor/rules/CHANGELOG.md`에 기록합니다.
- 2026-04-10: `(3)`에 `#viewReleases`·`(2)`에 퀵 레일/멘션 행 보강, §4 배포 뷰 검증 안내 추가.

---

## 7) 다음 작업으로 추천되는 순서 (신규 담당자용)

1. **§2 전역 토큰** 한 바퀴(특히 No-Line·CTA·모달 글래스) — 이후 구역 작업 시 재작업을 줄임.  
2. **P0**(`§5`) — `#viewChat`, 사이드바, `#appShellTopBar`, `#viewWelcome`.  
3. **P1** — 관리자 뷰(`#viewUserManagement`, `#viewOrgManagement`, `#viewReleases`, `#viewSettings`) + `#modalWorkHub`, `#modalUserProfile`, `#searchModal`.  
4. **P2** — §4 보조 모달·로그인.  
5. 체크 완료 시 해당 행을 `[x]`로 바꾸고, 스크린샷·PR에 구역 ID를 명시하면 추적이 쉬움.
