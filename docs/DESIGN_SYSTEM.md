# ECH 디자인 시스템 (Atmospheric / Stitch)

이 문서는 `design/` 폴더의 **ECH메인**·**ECH화면설계 (1)~(9)** 참고물과 실제 앱(`frontend/`)을 어떻게 맞추는지, 그리고 **이전 디자인으로 되돌리는 방법**을 정리합니다.

## 1) 참고 소스 (저장소 `design/`)

| 경로 | 용도 |
|------|------|
| `design/ECH메인/` | `code.html`, `DESIGN.md`, `screen.png` — 메인·레이아웃 기준 |
| `design/ECH화면설계 (1)` ~ `(9)/` | 세부 화면별 `code.html`·`DESIGN.md`·`screen.png` |

`DESIGN.md`의 **Chromatic Sanctuary** 규칙(톤 대비·고스트 보더·그라데이션 CTA 등)은 `frontend/styles.css`의 CSS 변수·`html[data-theme="light"]` 오버라이드로 반영합니다.

## 2) 토큰과 Tailwind

- **실제 렌더링**: Vanilla CSS — `frontend/styles.css`의 `:root` / `html[data-theme="light"]` 변수.
- **동일 팔레트 스냅샷**: `frontend/tailwind.config.js` (`colors`, `borderRadius`, `boxShadow`, `fontSize`, `fontFamily`).
- **빌드 산출물**: `frontend/ech-tailwind.css` — 선택(추가 유틸용). **채널 미선택 시 메인 대시보드**는 `styles.css`의 `.ech-welcome-*` 만으로도 동작하며, Tailwind 파일이 없어도 깨지지 않게 구성합니다. 수정 후 재생성:
  ```bash
  cd frontend && npm install && npm run build:css
  ```
  `content`는 `./index.html`, `./app.js`를 스캔합니다.

## 3) 점진적 적용 팁

- **한 번에 전부 교체하지 말고** 사이드바·버튼·모달 등 **구역 단위**로 `styles.css`를 수정합니다.
- Stitch `code.html`의 **플레이스홀더 이미지 URL**은 프로덕션에 그대로 두지 말고, 아바타·썸네일은 기존 API/정적 자산 경로를 사용합니다.
- 라이트 테마가 기본(`data-theme="light"`)인 경우가 많으므로, 시각 변경은 **`html[data-theme="light"]`** 선택자와 함께 검증합니다.

## 4) 롤백 — 이전 디자인으로 되돌리기

### 방법 A: 브라우저만 (파일 수정 없음)

`frontend/index.html`은 `#echStylesheet`와 `localStorage` 키 **`ech_design_version`** 으로 스타일시트를 고릅니다.

- **레거시 CSS만 로드**(스냅샷): 개발자 도구 콘솔에서  
  `localStorage.setItem('ech_design_version', 'legacy'); location.reload();`
- **다시 신규 디자인**:  
  `localStorage.removeItem('ech_design_version'); location.reload();`

레거시 파일: `frontend/design-backup/legacy-design/styles.css`

### 방법 B: 파일 덮어쓰기 (완전 동기)

ATMospheric 적용 **이전**에 맞춰 둔 복사본이 `frontend/design-backup/legacy-design/`에 있습니다.

```powershell
# 저장소 루트에서
Copy-Item -Force "frontend/design-backup/legacy-design/styles.css" "frontend/styles.css"
Copy-Item -Force "frontend/design-backup/legacy-design/index.html" "frontend/index.html"
Copy-Item -Force "frontend/design-backup/legacy-design/app.js" "frontend/app.js"
```

이후 강력 새로고침.  
**주의**: 롤백 후에도 로컬에 `ech_design_version === 'legacy'`가 남아 있으면, 현재 `index.html`이 다시 레거시 CSS 경로를 물을 수 있으므로 방법 A의 `removeItem`으로 초기화하거나, B로 `index.html`을 덮어쓴 뒤에는 스위치가 없는 예전 마크업이 됩니다.

### 스냅샷 갱신(운영/리드만)

새 디자인을 안정화한 뒤 **다음 롤백 기준점**을 바꾸려면, 그 시점의 `styles.css`·`index.html`·`app.js`를 다시 `design-backup/legacy-design/`에 복사해 두면 됩니다.

## 5) 관련 파일

| 파일 | 역할 |
|------|------|
| `frontend/styles.css` | 앱 UI 스타일(메인) |
| `frontend/tailwind.config.js` | 디자인 토큰 정의(참조·향후 Tailwind 빌드) |
| `frontend/design-backup/legacy-design/` | 롤백용 스냅샷 + `README.md` |
| `design/` | Stitch 산출물(HTML/MD/PNG) |

## 6) 화면설계 (1)~(9) ↔ 앱 구역 매핑

`design/ECH화면설계 (N)/code.html` 목업은 화면마다 레이아웃이 다릅니다. 실제 앱에서는 **구역 단위**로 `frontend/index.html` 마크업·`styles.css`·(선택) `ech-tailwind.css` 유틸을 맞춥니다. 아래는 **참고 제목(`<title>` 또는 본문 역할)** 과 **적용 위치**의 대응입니다.

| 폴더 | 참고 목업(요지) | 앱에서의 구역 / 식별자 |
|------|-----------------|-------------------------|
| `(1)` | ECH Workspace — Work Management | 업무·칸반 모달 `#modalWorkHub` · `.ech-workhub-shell` · 넓은 뷰에서 `.work-hub-body--split`(업무/칸반 2열) |
| `(2)` | 워크스페이스 셸 + 조직/멤버 모달 | 채팅 타임라인 `#viewChat` · `.ech-messages-wrap` · 글로벌 검색 `#appShellTopBar` |
| `(3)` | Enterprise Admin Hub | 관리자 뷰 공통 `.ech-region--admin` · 사이드바 관리 메뉴 |
| `(4)` | Stratos Pro 상단 네비 | `app-shell-topbar` (ECH메인과 동일 계열) |
| `(5)` | Organization Directory | 조직 관리 `#viewOrgManagement`(인사이트 카드 + `org-layout` 좌측 탭 레일) · 조직도 `#modalOrgChart` |
| `(6)` | Task & Kanban Management | `#modalWorkHub` 칸반 컬럼 `#channelKanbanBoard` |
| `(7)` | User Management | 사용자 관리 `#viewUserManagement` (`data-ech-design-ref="screen7-users"`) |
| `(8)` | Enterprise Hub — Settings | 앱 설정 `#viewSettings` |
| `(9)` | (코드만 상이·제목 생략 가능) | 사용자 **프로필 모달** `#modalUserProfile`(`.ech-profile-modal`·히어로·부제·`.profile-dl--cards`)·통합 검색 등 보조 패널 점진 적용 |

**마크업 훅**: 채팅 `.ech-region--chat`, 헤더 `.ech-chat-header`, 입력 `.ech-composer-bar`, 관리자 `.ech-region--admin` + `data-ech-design-ref`, 배포 관리 `#viewReleases`의 `release-layout`·인사이트 카드. 스타일은 **Tailwind 미로드 시에도** `styles.css`만으로 레이아웃이 유지되도록 시맨틱 규칙을 우선합니다. `index.html`이나 유틸 클래스를 바꾼 뒤에는 `cd frontend && npm run build:css`로 `ech-tailwind.css`를 재생성합니다.
