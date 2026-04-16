# CHANGELOG

프로젝트 변경 이력을 기록합니다.

## 2026-04-16

### Added
- **프로필 사진**: 스토리지 `{FILE_STORAGE_DIR}/user-profiles/{사번안전문자}.{확장자}` 단일 폴더 저장, DB `users.profile_image_relpath`. API: `GET /api/users/profile-image?employeeNo=`(JWT), `POST /api/users/me/profile-image`(본인·`app.allow-user-profile-self-upload`로 비활성화 가능), `POST /api/admin/users/{employeeNo}/profile-image`(관리자). 프로필·`/api/auth/me`·채널 멤버·메시지에 이미지 메타 포함. 프론트: 채팅/멤버/검색/조직도 아바타·프로필 모달(좌측 하단 진입 시에만 본인 사진 편집 버튼), 관리자 사용자 편집에서 파일 선택 후 저장 시 업로드.
- **운영 DB 마이그레이션**: `docs/sql/migrate_users_add_profile_image_relpath.sql` — `users.profile_image_relpath` 컬럼 추가.

### Changed
- **프로필 이미지 업로드 한도**: 5MB → **3MB**(`UserProfileImageService`).
- **일반 신규 메시지 토스트**: `pushNewMessageToast` 미리보기에 `mentionPreviewForToastClient` 적용 — `@{사번|표시명}` 토큰이 OS/인앱 알림에 그대로 보이던 현상 방지.
- **채팅 타임라인 복구**: 탭이 다시 보일 때(`visibilitychange` → visible, 디바운스) `recoverActiveChannelTimelineIfNeeded` 호출 — 절전·재개 후 `online`/소켓 이벤트 없이도 실패 화면 복구.
- **`loadMessages`**: 타임라인·레거시 API가 5xx/408/429 또는 `fetch` 예외일 때 **최대 5회 재시도**(지수 백오프, 상한 10초).
- **DM 글로벌 탑바**: 채널명 왼쪽 접두를 정적 ●가 아니라 **상대(또는 나에게 쓰기 시 본인) 프레즌스 점**으로 표시(`updateChatHeaderDmPresence` + `dmSidebarLeadingHtml` `showPresence: true`).
- **본인 전용 DM(나에게 쓰기) UI 정렬**: 백엔드 생성·목록 요약에서 표시명을 **본인 이름**(단일 멤버 시)으로 통일. 프론트는 `displayNameForDmChannel`·`dmPeerEmployeeNosForPresence`로 사이드바·퀵레일·글로벌 탑바·워크플로 맥락을 1:1 DM과 동일 패턴으로 맞춤.

## 2026-04-12 (추가)

### Changed
- **데스크톱 빌드**: `npm run build:win` 전에 `scripts/clean-dist.cjs`로 `desktop/dist` 삭제 — 재시도·Cursor/Defender 안내. `CSTALK_SKIP_DIST_CLEAN=1`로 삭제 생략 가능. `package.json`에 `description`·`author` 추가(electron-builder 경고 완화).
- **채팅 타임라인 복구**: 네트워크 단절로 `메시지 로드 실패`가 남은 채널은 브라우저 `online` 이벤트·소켓 `connect/reconnect` 시 `recoverActiveChannelTimelineIfNeeded`가 자동 재로딩하여 앱 재시작 없이 복구.

## 릴리즈 v1.2.5

### Release
- **버전**: 백엔드·데스크톱 **1.2.5**, Git 태그 **`v1.2.5`**. `backend/build/libs/cstalk-backend-1.2.5.jar`, `desktop/dist/` NSIS **`CSTalk-Setup-1.2.5.exe`**·`latest.yml`·(권장) `.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN` 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.5`. 백엔드 JAR은 스크립트에 포함되지 않음.
- **이번 패키지에 포함된 2026-04-12 작업(커밋 해시)**:
  - `6904efe` — 제품명 **CSTalk**·도메인 **`cstalk.co.kr`** 반영
  - `c5eccec` — Windows 데스크톱 아이콘·`icon:build` 파이프라인
  - `bbc6b09` — NSIS 설치 시 레거시 **ECH** 무인 제거 후 CSTalk 설치
  - `0036ebe` — 멘션 OS 알림: 작업 표시줄 깜빡임·토스트 장시간 표시
  - `89d6ed0` — 담당 업무 변경 **OS 알림** 경로 추가
  - `f08df77` — 담당 업무 알림을 멘션과 동일 수준으로 강조
- **백엔드 Java 소스**: 위 날짜 구간에서 **비즈니스 로직 변경 없음**(버전 정렬·배포 산출물명만 1.2.5). 웹 UI는 `frontend/app.js` 등 정적 파일 배포가 필요.

## 2026-04-12

### Added
- **Windows 데스크톱(NSIS)**: 레거시 **ECH** 설치 제거 후 CSTalk 설치 — `desktop/nsis/installer.nsh`의 `customInit`에서 `Uninstall ECH.exe` 무인 실행(기본 경로: `%ProgramFiles%\ECH\`, `%ProgramFiles(x86)%` 대응, `%LOCALAPPDATA%\Programs\ECH\`). `desktop/package.json` → `build.nsis.include`.
- **Electron 멘션 OS 알림**: 백그라운드일 때 멘션만 작업 표시줄 아이콘 깜빡임(`BrowserWindow.flashFrame`)·토스트 자동 닫힘 완화(`timeoutType: never`, Windows/Linux). `desktop/main.js`, `frontend/app.js` `showOsNotificationIfAllowed`·`pushMentionToast`.
- **담당 업무 사이드바 변경 OS 알림**: `loadMyChannels`에서 담당 업무 스냅샷 변경 시 `pushActivityToast`가 기존 인앱 토스트만 띄우던 것을, 백그라운드에서는 `showOsNotificationIfAllowed`(태그 `ech_os_work_sidebar`)로 Electron·브라우저(권한 시) OS 알림까지 표시. Electron은 인앱 토스트 생략.
- **담당 업무 알림 강조**: `kind: workActivity`로 멘션과 동일하게 Windows `flashFrame`·`timeoutType: never`(Linux 동일), 웹 OS 알림은 자동 닫힘 5분. 인앱 활동 토스트 표시 12초→25초.

### Changed
- **제품명·브랜딩**: 표시명을 **CSTalk**로 통일(웹 로그인·글로벌 바, Electron 창 제목·트레이, 로그 프리픽스 `[CSTalk]` 등). Java 패키지 `com.ech`·CSS 접두 `ech-*`·`ech_*` localStorage 키는 호환을 위해 유지.
- **운영 도메인 예시**: 기본 데스크톱 `serverUrl`·문서·배포 스크립트의 사이트 호스트를 **`cstalk.co.kr`**(예: `http://cstalk.co.kr:8080`) 기준으로 갱신. hosts 파일 예시 동일.
- **Windows 데스크톱**: `desktop/package.json` — `productName` **CSTalk**, `appId` `com.cstalk.desktop`, NSIS 산출물 **`CSTalk-Setup-${version}.exe`**, 패키지 `name` `cstalk-desktop`. 선택 설정 파일 **`cstalk-server.json`**(기존 `ech-server.json`·`%ProgramData%\ECH\` 경로는 `main.js`에서 호환 로드). 임시 열기 폴더 `%TEMP%\cstalk-open`.
- **백엔드 산출물**: `settings.gradle`·`bootJar` 파일명 **`cstalk-backend-{version}.jar`**, `spring.application.name` **`cstalk-backend`**, 헬스 `service` 필드·Hikari 풀명·첨부 로그 프리픽스 갱신.
- **Realtime**: PM2 앱명 **`cstalk-realtime`**, 헬스/콘솔 `service` 식별자·기동 로그 문구 정리. `deploy/pm2.ecosystem.config.cjs` 기본 경로 `C:/CSTalk/...`.
- **배포 패키지**: `deploy/CSTalk-deploy.zip`, 서버 스크립트·`DEPLOYMENT_WINDOWS.md` 등 설치 경로 예시 **`C:\CSTalk`**, Windows 서비스 표시명 **CSTalk-Backend** / **CSTalk-Realtime** 등.
- **문서**: `README.md`, `docs/DEVELOPER_README.md`, `HANDOVER`, `FEATURE_SPEC`, `DEPLOY`, `MONITORING`, `ENVIRONMENT_SETUP`, `DESIGN_SYSTEM`, 테스트 조직 샘플(`TestOrgUserProvider`) 등 명칭·예시 URL 반영.
- **`.gitignore`**: 배포 ZIP 무시 목록에 `deploy/CSTalk-deploy.zip` 및 기존 로컬 산출물명 `deploy/ECH-deploy.zip` 병기.

## 릴리즈 v1.2.4

### Release
- **버전**: 백엔드·데스크톱 **1.2.4**, Git 태그 **`v1.2.4`**. `backend/build/libs/ech-backend-1.2.4.jar`, `desktop/dist/` NSIS(`ECH-Setup-1.2.4.exe`)·`latest.yml`·`.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN`(repo `contents`·`releases` 권한) 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.4`. **백엔드 JAR**은 해당 스크립트에 포함되지 않음 — 릴리즈 페이지에 **수동 첨부**하거나 사내 배포 채널로 전달.
- **변경 요약**: 크림 라이트 테마 정리 — `@` 멘션 자동완성·글로벌/통합 검색·조직도·구성원 피커·칸반 등에서 `cream`이 다크 보강을 따라가던 문제를 수정하고 톤 통일. 채팅 본인 말풍선 멘션·DM 표시 가독성, 워크플로 담당 업무 진입·수동 자리비움·조직도 미활성 사용자 API 등(상세는 아래 `2026-04-10` 및 이전 항목). `frontend/styles.css` · `frontend/app.js` · `backend` · 문서.

## 릴리즈 v1.2.3

### Release
- **버전**: 백엔드·데스크톱 **1.2.3**, Git 태그 **`v1.2.3`**. `backend/build/libs/ech-backend-1.2.3.jar`, `desktop/dist/` NSIS(`ECH-Setup-1.2.3.exe`)·`latest.yml`·`.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN`(repo `contents`·`releases` 권한) 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.3`. **백엔드 JAR**은 해당 스크립트에 포함되지 않음 — 릴리즈 페이지에 **수동 첨부**하거나 사내 배포 채널로 전달.
- **변경 요약**: 크림 라이트 테마에서 워크플로 칸반이 다크 톤으로 보이던 CSS 수정; 사이드바 워크플로 **채널 선택만 연 상태**에서 **담당 업무**를 눌렀을 때 본문이 숨겨지던 문제 수정(통합 검색 업무·칸반 진입 동일). `frontend/styles.css` · `frontend/app.js` · 문서.

## 릴리즈 v1.2.2

### Release
- **버전**: 백엔드·데스크톱 **1.2.2**, Git 태그 **`v1.2.2`**. `backend/build/libs/ech-backend-1.2.2.jar`, `desktop/dist/` NSIS(`ECH-Setup-1.2.2.exe`)·`latest.yml`·`.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN`(repo `contents`·`releases` 권한) 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.2`. **백엔드 JAR**은 해당 스크립트에 포함되지 않음 — 릴리즈 페이지에 **수동 첨부**하거나 사내 배포 채널로 전달.
- **변경 요약**: v1.2.1 이후 프론트 **프레즌스 UX**(라이트·크림 단색 점·한 겹 링, 자리비움 노란색, 수동 자리비움 유지, 글로벌 탑바 채널 메타 정리 등) 반영 패치.

## 릴리즈 v1.2.1

### Release
- **버전**: 백엔드·데스크톱 **1.2.1**, Git 태그 **`v1.2.1`**. `backend/build/libs/ech-backend-1.2.1.jar`, `desktop/dist/` NSIS(`ECH-Setup-1.2.1.exe`)·`latest.yml`·`.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN`(repo `contents`·`releases` 권한) 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.1`. **백엔드 JAR**은 해당 스크립트에 포함되지 않음 — 릴리즈 페이지에 **수동 첨부**하거나 사내 배포 채널로 전달.
- **변경 요약(프론트·탑바)**: 라이트·크림 테마에서 프레즌스 점 가독성 보강, 글로벌 탑바 채널 영역은 채널명·접두만 표시(팀원 수·메타 초록 점·DM 접두 프레즌스 점 제거).
- **변경 요약(프론트·프레즌스 2차)**: 라이트·크림에서 온라인(선명한 초록)·오프라인(밝은 회색)·자리비움(노란색) 대비 재조정. 사이드바에서 **직접 선택한 자리비움**은 포인터·키 입력 등 동작 감지로 자동 해제되지 않음(5분 무활동 자동 자리비움만 활동 시 온라인 복귀). `frontend/styles.css` · `frontend/app.js`.

## 릴리즈 v1.2.0

### Release
- **버전**: 백엔드·데스크톱 **1.2.0**, Git 태그 **`v1.2.0`**. `backend/build/libs/ech-backend-1.2.0.jar`, `desktop/dist/` NSIS(`ECH-Setup-1.2.0.exe`)·`latest.yml`·`.blockmap`.
- **GitHub 릴리즈(Windows 에셋)**: 환경변수 `GITHUB_TOKEN`(repo `contents`·`releases` 권한) 설정 후 `powershell -File ./tools/publish-electron-github-release.ps1 v1.2.0`. **백엔드 JAR**은 해당 스크립트에 포함되지 않음 — 릴리즈 페이지에 **수동 첨부**하거나 사내 배포 채널로 전달.

## 2026-04-10

### Fixed
- **프론트(크림 라이트·조직도 UI)**: 조직도 단독 모달·구성원 피커(조직도 오버레이)에서 흰색 카드·입력과 크림 배경 대비가 과한 문제를 완화. 멤버 카드·패널·검색 줄·목록을 펄먼트·아이보리 계열로 통일, 배지·인원 수 칩·프레즌스 링 색을 맞춤. `frontend/styles.css`.
- **프론트(크림 라이트·검색바)**: 글로벌 탑바 검색창 포커스 시 `html:not([data-theme="light"])` 다크 스타일이 크림에 덮이던 문제를 수정(`cream` 제외). 통합 검색 모달(`#searchModal`) 툴바·입력·검색 버튼도 동일 원인으로 다크 톤이 적용되던 것을 크림 전용 라이트 스타일로 분리. 검색 결과 행 호버·구분선 크림 보강. `frontend/styles.css`.
- **프론트(크림 라이트·멘션 자동완성)**: `@` 멘션 제안 목록이 `html:not([data-theme="light"])` 다크 보강에 묶여 크림 테마에서도 어두운 패널·낮은 글자 대비로 보이던 문제를 수정. 다크 보강에서 `cream` 제외, 크림 전용 밝은 패널·호버 스타일 추가. `frontend/styles.css`.
- **프론트(채팅 가독성)**: 본인 말풍선 안 `@멘션`이 accent 보라색이라 배경과 구분이 어렵던 문제를 수정(밝은 글자색). DM 표시(●)는 글로벌 탑바·퀵레일·사이드바에서 채널명·말풍선과 겹치지 않도록 보조 텍스트 색으로 조정. `frontend/styles.css`.
- **프론트(워크플로우·담당 업무)**: 사이드바 워크플로우로 채널 선택 화면만 연 뒤 **담당 업무 목록** 항목을 눌렀을 때, 채널 선택 UI가 유지되고 상단만 `연결: …`로 바뀌며 해당 업무로 스크롤·강조되지 않던 문제를 수정. 채널이 확정되는 진입에서는 `workflowNeedsChannelPick`을 해제하고 `renderWorkflowChannelPicker()`로 본문을 표시. 통합 검색에서 업무·칸반으로 워크플로우를 열 때도 동일. `frontend/app.js`.
- **프론트(크림 라이트·워크플로우 칸반)**: `html:not([data-theme="light"])` 다크 보강이 `cream`에도 적용되어 칸반 컬럼·카드가 어두운 배경으로 보이던 문제를 수정. 크림은 다크 보강에서 제외하고, 라이트와 동등한 밝은 칸반·섹션·업무 행 스타일을 적용. `frontend/styles.css` · `docs/FEATURE_SPEC.md`.
- **프론트(본인 프레즌스)**: 메뉴에서 **자리비움**을 고른 뒤 마우스·키보드 동작이 감지되면 즉시 **온라인**으로 바뀌던 동작을 수정. 직접 자리비움은 **온라인**으로 다시 고르기 전까지 유지. 자동(5분 무활동) 자리비움은 기존처럼 동작 시 온라인 복귀. 소켓 **재연결 시**는 서버와 맞추기 위해 수동 플래그를 초기화하고 온라인으로 동기화. `frontend/app.js`.

### Changed
- **프론트(프레즌스 UI·라이트/크림)**: 온라인·자리비움·오프라인을 **단색 원**으로 표시하고, 글로우·이중 링·진한 테두리는 제거. 아바타와만 구분되도록 **한 겹 링**(사이드바/채팅/멤버 패널/조직도 배경에 맞춤). 오프라인은 **중간 회색 단색**(`#94a3b8` / 크림 `#a8a29e`). 자리비움은 노란색(`#eab308` / 크림 `#ca8a04`). `frontend/styles.css`.
- **프론트(글로벌 바·프레즌스)**: 라이트·크림 라이트 테마에서 프레즌스 점에 어두운 외곽선·배경 대비 링·약한 그림자를 두어 연한 배경에서도 식별하기 쉽게 함(사이드바 DM·멤버 패널·메시지 아바타·조직도 등). 글로벌 탑바 채널 컨텍스트는 **채널명(·접두)만** 표시 — **「팀원 N명」** 및 탑바 **프레즌스 초록 점** 메타 줄 제거. DM 채널도 탑바 접두에 **프레즌스 점 없이** DM 표식만(`dmSidebarLeadingHtml` 옵션). `frontend/styles.css` · `frontend/app.js` · `frontend/index.html`.

### Fixed
- **백엔드(조직도 API)**: `GET /api/user-directory/organization` 트리에 **미사용(INACTIVE)** 사용자가 그대로 노출되던 문제를 수정. `UserSearchService.getOrganizationTree`에서 `User.status == ACTIVE` 인 경우만 회사/본부/팀 노드에 포함. 조직도 모달·구성원 추가 피커 동일 API 공유. `UserSearchService.java` · `UserDirectoryApiTest.java`.

### Added
- **프론트(대용량 파일 다운로드 진행 표시)**: `Content-Length`가 약 512KB 이상이면 fetch 본문을 스트림으로 읽으며 하단 **`#fileDownloadStatusBar`**에 `다운로드 중… N%`(또는 길이 미상 시 문구만) 표시. 채널 파일 저장/열기·새 탭 열기·ZIP 일괄 저장·브라우저 JPEG 재압축 경로에 적용. `frontend/app.js` · `frontend/index.html` · `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md` · `README.md`.
- **프론트(대기 첨부 미리보기)**: 다중 파일·이미지 선택 시 항목별 **제거(✕)**, 추가 선택·드롭·붙여넣기 시 기존 대기 **누적(append)**. 메인·스레드 동일. `frontend/app.js` · `frontend/index.html` · `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.

### Fixed
- **프론트(파일 전송 취소)**: 전송 중 **대기를 비울 때**(항목별 ✕로 모두 제거 등) 진행 중이던 `XMLHttpRequest` 업로드를 **`abort()`**하고, 이미지 압축 대기 중 취소는 **`fileUploadSessionId`**로 감지해 이후 업로드 단계를 건너뜀. 순차 전송 루프는 스냅샷 기준으로 취소 시 중단. `frontend/app.js` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.

### Changed
- **프론트(첨부 미리보기)**: 전송 대기 영역에서 **전체 취소** 버튼 제거 — 대기 해제는 **항목별 ✕**만 사용. `frontend/index.html` · `frontend/app.js` · `frontend/styles.css` · `README.md` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(채팅 파일 첨부 시각 정렬)**: 비이미지 파일 카드 열을 이미지 번들과 동일 **최대 폭 360px**·본인 **우측 정렬**로 통일하고, `.msg-content-row .msg-time` 여백·`text-align` 상속을 `.msg-attachment-meta-below`에서 제거해 이미지와 같은 시각 푸터로 보이게 함. `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(채팅 첨부 시각·컴포저 미리보기)**: 첨부 메시지는 시각을 썸네일/카드 **아래**에 두고, 묶음이면 **일괄저장**과 같은 줄에서 시각 **왼쪽**·버튼 **오른쪽**(`msg-attachment-meta-below`, `space-between`). 전송 전 파일 미리보기 `#filePreview`·`#threadFilePreview`는 입력창 **오른쪽**이 아니라 **바로 위**에 오도록 마크업 순서·컴포저 세로 스택 조정. `frontend/app.js` · `frontend/index.html` · `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(조직도 모달)**: 헤더 **새로고침** 버튼 제거. 모달 오픈 시 로그인 사용자의 **최하위 소속**(팀 소속 사번 일치 → `department`와 팀명 일치 → 본부/회사 직속 순) 노드를 자동 선택·우측 구성원 표시·좌측 스크롤. `frontend/index.html` · `frontend/styles.css` · `frontend/app.js` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.

## 2026-04-12

### Added
- **데스크톱(Windows 아이콘)**: CS 로고 기반 `desktop/assets/icon.png`·`icon.ico` 갱신. 원본 `desktop/assets/icon-source-cs.png`, 흰 배경 제거 후 512×512 정사각 PNG로 맞춘 뒤 ICO까지 한 번에 만들려면 `npm run icon:build`(`scripts/build-desktop-icon.mjs`, devDependency `sharp`). `docs/DEVELOPER_README.md`.

### Fixed
- **프론트(본인 아바타 → 프로필)**: 글로벌 바 `#appHeaderAvatar`·사이드바 하단 `#sidebarAvatar` 클릭 시 본인 프로필 모달이 열리도록 `openCurrentUserProfile` 연결. 마크업을 `button.user-avatar`로 정리하고 포커스 링·상단 아바타 커서 보정. `frontend/app.js` · `frontend/index.html` · `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(크림 라이트 사용자 프로필 모달)**: `html:not([data-theme="light"])` 다크 전용 규칙이 `cream`에도 적용되어 프로필 정보 카드(`.profile-dl--cards .profile-dd-card`)가 어두운 배경·저대비로 보이던 문제를 `:not([data-theme="cream"])`로 분리하고, 크림에서 라이트 계열 표면을 지정. `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(채널 상세 패널 레이아웃)**: 글로벌 탑바만 있는 현재 구조에서 멤버 패널(햄버거·채널 상세)이 구 **인-채팅 헤더** 높이(`top: 76px`/`64px`)를 따라 상단에 빈 공간이 생기고 높이가 어긋나던 문제를 수정. `.ech-region--chat .member-panel`은 `top: 0`부터 `bottom: 0`까지 채우도록 통일하고, `#chatHeaderMeta` 기반 `:has()` 보정 규칙은 제거. `max-width`는 `100vw` 대신 채팅 열 기준 `100%`. `@media (max-width: 640px)`에서 패널 폭을 채팅 열 전체(`100%`)로 확장. `frontend/styles.css`.

### Changed
- **나에게 쓰기(본인 DM)**: `dmPeerEmployeeNos`에 본인 사번을 넣어 단일 멤버 DM을 열고, 프로필·웰컴·DM 만들기 모달에서 진입. 멤버 패널은 사번 기준 **중복 제거**(프론트 `dedupeChannelMembersByEmp` + 백엔드 `ChannelService` 응답 dedupe). 사이드바 표시명은 서버 `buildDmPeerDisplayLabel`/생성 시 **`나에게 쓰기`**. `frontend/app.js` · `frontend/index.html` · `frontend/styles.css` · `backend/.../ChannelService.java` · `README.md` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(프레즌스 자동 자리비움)**: 로그인 후 온라인이면 5분간 포인터·키보드·휠·스크롤 등 동작이 없을 때 자동 `AWAY`, 자리비움 중 동작·탭 복귀 시 자동 `ONLINE`. 서버 스냅샷/본인 `presence:update` 시 무활동 타이머 정합. 로그인 화면 전환 시 타이머 해제. `frontend/app.js` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(조직도 프레즌스)**: 조직도 모달 우측 멤버 카드 아바타에 채팅·멤버 패널과 동일한 프레즌스 점(`data-presence-user`) 표시, 모달 열 때 `fetchPresenceSnapshot()`으로 스냅샷 보강·렌더 후 `refreshPresenceDots()`. `frontend/app.js` · `frontend/styles.css` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(DM 멤버 패널)**: DM에서는 채널 생성자에 대한 `관리자` 배지와 멤버 `내보내기` 버튼을 표시하지 않음(우클릭 컨텍스트는 기존과 같이 DM에서 비활성). `frontend/app.js` · `docs/FEATURE_SPEC.md` · `docs/HANDOVER.md`.
- **프론트(크림 라이트 채널 상세·테마 설정 모달)**: `html:not([data-theme="light"])` 다크 전용 규칙이 `cream`에도 적용되며 멤버 패널(채널 상세)·ECH 채팅 헤더가 어둡게 보이던 문제를 `:not([data-theme="cream"])`로 분리. 크림 전용 멤버 패널 표면/구분선을 라이트 톤으로 추가. `#modalThemePicker`에 크림 전용 모달·헤더·본문·닫기 버튼 오버라이드를 추가해 테마 옵션창이 다크 톤으로 비치지 않도록 보강. `frontend/styles.css`.
- **프론트(상단 워크플로우 진입 조건 수정)**: 채널 미선택 상태에서 우측 상단 `워크플로우` 클릭 시 특정 채널로 자동 이동하지 않고, 사이드바와 동일하게 `workflowChannelPicker`(채널/DM 선택 UI)를 먼저 표시하도록 변경. 채널 내에서는 기존처럼 즉시 해당 채널 워크플로우로 진입. `frontend/app.js`.
- **프론트(크림 테마 관리자 4개 뷰 다크 분기 제외)**: 설정/배포/조직/사용자 관리 영역의 다크 공통 스타일(`settings-*`, `release-*`, `org-tab-*`, `user-*`)에 `:not([data-theme="cream"])`를 적용해 `cream`에서 어두운 패널이 재적용되던 문제를 수정. `frontend/styles.css`.
- **프론트(크림 테마 워크플로우/관리자 다크 분기 분리)**: `cream`에서 워크플로우 패널(`.work-hub-panel`)·관리자 헤더(`.admin-panel-header`)·인사이트 카드(`.admin-insight-card`)가 다크 스타일을 따르지 않도록 전용 라이트 오버라이드를 추가. 크림 표면/약한 그림자 기준으로 가독성을 복원. `frontend/styles.css`.
- **프론트(크림 테마 모달 다크 오버라이드 충돌 수정)**: 다크 공통 규칙 `html:not([data-theme="light"]) .modal-overlay/.modal`에 `:not([data-theme="cream"])`를 추가해 `cream` 모달이 다시 어두워지던 우선순위 충돌을 해소. 테마 설정 팝업/조직도 모달이 크림 라이트 톤으로 유지되도록 수정. `frontend/styles.css`.
- **프론트(워크플로우 버튼 상단 여백 추가 보정)**: 사이드바 최상단 `워크플로우` 버튼이 여전히 위에 붙어 보이던 간격을 추가 조정(`.sidebar-section--hub-shortcuts` margin `14px 0 8px`)해 시각적 균형을 개선. `frontend/styles.css`.
- **프론트(사이드바 섹션 간격 미세조정)**: 워크플로우 버튼이 최상단에서 딱 붙어 보이지 않도록 `sidebar-section--hub-shortcuts` 상단/하단 마진을 확장하고, 전체 섹션 간 간격(`.sidebar-section + .sidebar-section`)을 소폭 추가해 세로 정렬 리듬을 정리. `frontend/styles.css`.
- **프론트(사이드바 워크플로우 최상단 이동)**: `btnSidebarWorkflow` 블록(`sidebar-section--hub-shortcuts`)을 `sidebar-main` 최상단(퀵 레일 바로 아래)으로 이동. 동작은 동일하며 위치만 변경. `frontend/index.html`.
- **프론트(사이드바 워크플로우 버튼 리듬 보정)**: 단일 버튼 구조는 유지하면서 `btnSidebarWorkflow`의 폰트/패딩/아이콘 강도/섹션 간격을 일반 사이드바 리스트 항목과 동일한 밀도로 조정해 시각적 이질감을 완화. `frontend/styles.css`.
- **프론트(크림 라이트 모달/관리자 톤 정합)**: `cream` 테마에서 조직도 모달(`modalOrgChart`)·테마 설정 모달(`modalThemePicker`)·관리자 사이드바(`#adminSection`)가 다크 톤을 타지 않도록 전용 라이트 오버라이드 추가. 모달 배경/스크림/버튼/카드 그림자를 크림 컨셉으로 완화하고 관리자 라벨 대비를 개선. `frontend/styles.css`.
- **프론트(상단 검색폭 정렬 보정)**: 상단 헤더 좌측 검색 입력(`.app-shell-global-search`)의 기본 폭을 축소(`320px -> 236px`)해 사이드바(324px) 폭보다 우측으로 튀어나오지 않도록 정렬. `frontend/styles.css`.
- **프론트(채널 정보 중앙축 정렬 보정)**: 상단 채널 정보(`appTopbarChannelContext`)의 기준점을 상단바 전체가 아닌 채팅 영역 중심축으로 보정. 사이드바 펼침(324px)/접힘(64px) 상태별 `left` 오프셋을 적용해 채널명·인원수와 메시지창의 정중앙 정렬이 일치하도록 수정. `frontend/styles.css`.
- **프론트(웰컴 카드/워크플로우 버튼 정리)**: `cream` 테마에서 웰컴 카드와 하단 빠른 액션 버튼의 그림자를 완전히 제거. 웰컴 카드 hover 좌측 보라 라인이 카드 외곽으로 튀어나오지 않도록 `overflow: hidden` 적용. 사이드바 워크플로우는 접기 헤더 없이 단일 버튼(`button#btnSidebarWorkflow`)으로 단순화하고 키보드 보조 핸들러 중복을 제거. `frontend/index.html`, `frontend/styles.css`, `frontend/app.js`.
- **프론트(웰컴 카드 그림자 톤 보정)**: `cream` 테마에서 웰컴 상단 카드(`워크플로우/채널 만들기/DM 시작`)의 하단 그림자를 제거하고 얇은 보더만 유지. `dark`/`ocean` 계열에서 하단 3버튼(`ech-welcome-quick-action-btn`)의 상단 흰 하이라이트(inset)를 제거해 더 단정한 버튼 톤으로 조정. `frontend/styles.css`.
- **프론트(크림 라이트 상단 헤더/채팅 밝기 복원)**: 크림 테마에서 상단 헤더바가 다크 규칙에 끌려가며 어두워지던 문제를 전용 오버라이드로 수정. 상단 검색/내비 텍스트 대비를 올리고, 채팅 본문·컴포저의 다크 그라데이션을 라이트 크림 톤으로 교체해 가독성을 복원. `frontend/styles.css`.
- **프론트(크림 라이트 헤더/퀵레일 톤 완화)**: `cream` 테마에서 헤더바·퀵레일을 더 밝은 배경으로 조정하고 과한 음영을 축소(`--topbar-ambient-indigo`, `--header-ambient` 약화). 퀵레일 배경을 다크에서 라이트 베이지(`--quick-rail-bg`)로 변경하고 active/pinned 인셋 강조도 약화. `frontend/styles.css`.
- **프론트(웰컴 문구 줄바꿈/테마 라벨/크림 가독성)**: 웰컴 히어로 리드 문구를 2줄로 분리(`br`)하고, 테마 피커 라벨 `검정/하양`을 `다크/화이트`로 변경. `cream` 테마에 사이드바/상단바/텍스트 대비 변수(`--sidebar-search-*`, `--sidebar-item-*`, `--text-*`, `--quick-rail-*`)를 보강해 가독성을 개선. `frontend/index.html`, `frontend/styles.css`.
- **프론트(웰컴 섹션 문구 제거)**: 웰컴 본문 카드 영역의 안내 문구 `바로 실행 가능한 기능` / `채널 선택 없이도 바로 열 수 있는 화면들입니다.`를 제거하고 카드만 노출되도록 정리. `frontend/index.html`.
- **프론트(웰컴 히어로/추가 액션 정리)**: 웰컴 히어로 상단 버튼(`워크플로우 열기`, `채널 만들기`)을 제거. 하단 추가 기능(조직도 열기/테마 설정/내 프로필 확인)은 세로 리스트에서 가로 3버튼(`ech-welcome-quick-actions`)으로 변경. `frontend/index.html`, `frontend/styles.css`, `frontend/app.js`.
- **프론트(채널 미선택 웰컴 개편)**: `#viewWelcome`을 단일 안내 카드에서 기능형 대시보드로 변경. 상단은 `👋 안녕하세요, {이름}님` 인사/히어로, 기존 `채널 목록 보기·검색·조직도` 버튼은 제거하고 즉시 실행 가능한 액션(워크플로우 열기, 채널 만들기, DM 만들기, 조직도, 테마, 내 프로필) 카드/버튼으로 재구성. `frontend/index.html`, `frontend/app.js`.
- **프론트(사이드바 워크플로우 위치 조정)**: 사이드바 `워크플로우` 섹션(`sidebar-section--hub-shortcuts`, `btnSidebarWorkflow`)을 상단 고정 위치에서 `멘션` 섹션 바로 위로 이동. `frontend/index.html`.
- **프론트(상단 메뉴/아이콘 정리)**: 상단 내비 `btnTopNavTeam`의 라벨을 `팀`에서 `조직도`로 변경. 상단 우측 보조 아이콘 버튼(알림/도움말/테마)을 제거해 헤더 우측 영역을 단순화. `frontend/index.html`.
- **프론트(워크플로우 채널 선택 모드 액션 숨김)**: 사이드바 워크플로우 진입 시 표시되는 채널 선택 단계(`workflowNeedsChannelPick=true`)에서는 하단 액션 푸터(`.work-hub-footer`)를 숨기고, 채널 선택 후 실제 워크플로우 화면에서만 `저장/닫기` 버튼이 보이도록 변경. `frontend/app.js`.
- **프론트(워크플로우 칸반 반응형 폭)**: `#channelKanbanBoard`의 컬럼 고정폭(`320px`)을 제거하고 3개 컬럼이 가용 폭을 균등 분할(`flex: 1 1 0`)하도록 조정. 큰 화면 우측 여백을 제거하고 작은 화면에서도 가로 스크롤 없이 보드가 화면 폭에 맞게 표시되도록 수정. `frontend/styles.css`.
- **프론트(사이드바 워크플로우 채널 선택 단계)**: 사이드바 `워크플로우` 진입 시 즉시 로드 대신 채널/DM 선택 패널(`workflowChannelPicker`)을 먼저 표시하고, 선택 후 해당 채널 워크플로우를 로드하도록 변경. 채널 내부 `📋` 진입은 기존처럼 즉시 현재 채널 워크플로우를 연다. `frontend/index.html`, `frontend/app.js`, `frontend/styles.css`.
- **프론트(하단 라인 완전 정렬)**: `.composer` 높이를 `--layout-footer-height`로 고정하고(`height`+`min-height`), 하단 안내문 `.ech-composer-footnote`를 숨겨 좌측 하단 프로필 바와 수평 라인을 동일하게 고정.
- **프론트(상단 컨텍스트 중앙정렬·컴포저 라인 정렬)**: 상단 채널 컨텍스트(`appTopbarChannelContext`)를 탑바 절대 중앙 정렬(`left:50%/translateX`)로 보정. 메시지 입력의 서식 툴바(`ech-composer-toolbar`) 제거, 컴포저 패딩을 축소해 좌측 하단 메뉴 라인과 높이감을 맞춤.
- **프론트(상단바 채널 컨텍스트 이동)**: 채팅방 이름·구성원 수를 채팅 헤더에서 상단 글로벌 바 검색 옆 중앙(`appTopbarChannelContext`)으로 이동. 채널 설정 햄버거(`btnHeaderMenu`)는 상단 우측 아이콘 영역으로 재배치하고 채팅 뷰에서만 노출.
- **프론트(테마·가독성·프레즌스)**: 테마 2종 추가(`ocean`, `cream`) 및 테마 피커 버튼 확장. 프레즌스 점을 테마 변수(`--presence-*`) 기반으로 재설계해 온라인/자리비움/오프라인 식별성과 대비(링/글로우/크기)를 강화. `index.html`, `app.js`, `styles.css`.
- **프론트(워크플로우 표시 위치 보정)**: `ensureWorkflowMountedInChatArea()`로 `#modalWorkHub`를 `.chat-area`에 부착해 워크플로우가 메인(가운데 채팅) 영역에서 바로 표시되도록 조정. `styles.css`의 `.view-workflow`를 `flex: 1` 기반으로 수정.
- **프론트(워크플로우 페이지 전환)**: `#modalWorkHub`를 오버레이 모달에서 메인 콘텐츠의 페이지 뷰(`.view-workflow`/`.workflow-page`)로 전환. `showView("modalWorkHub")` 기반으로 이동하고 상/하단 닫기 버튼(`btnCloseWorkflowPage*`)은 채팅/시작 화면으로 복귀. `index.html`, `app.js`, `styles.css`, `docs/FEATURE_SPEC.md`, `docs/DESIGN_SYSTEM.md`, `docs/DESIGN_GAP_CHECKLIST.md`, `docs/HANDOVER.md`.
- **프론트(워크플로우 모달 디자인 정합)**: `design/ECH화면설계 (1)` 톤으로 `#modalWorkHub` 상단 툴바(`.workflow-topbar`: Search/Filters/New Task), 섹션 표면(`.workflow-section`)·타이포를 재정렬하고, `btnWorkflowNewTask`/`btnWorkflowFilter` 동작을 추가. `index.html`, `styles.css`, `app.js`.
- **프론트(워크플로우·검색)**: 업무 항목·칸반을 **워크플로우**로 통합 — `#modalWorkHub` 단일 패널(`.work-hub-panel--workflow`, Tasks/Board 섹션), 채널·DM 연결 `#workHubChannelContext`(`syncWorkHubChannelContext`). 사이드바 **워크플로우** 한 항목(`btnSidebarWorkflow`), 상단 네비 라벨 **워크플로우**. 사이드바 **검색 필드 제거** — 통합 검색은 상단 `appHeaderSearchInput`만(`submitWorkspaceSearchFromHeader`). `index.html`, `app.js`, `styles.css`, `README.md`, `FEATURE_SPEC.md`, `DESIGN_SYSTEM.md`.
- **프론트(사이드바·칸반)**: 좌측 사이드바 상단 `.sidebar-workspace`(조직도 단축 버튼 줄) 제거 — 조직도는 상단 **팀**(`btnTopNavTeam`)·환영 화면 `openOrgChartModal()`로만 진입. `#channelKanbanBoard`에 `margin-top: 20px`. `index.html`, `app.js`, `styles.css`, `docs/FEATURE_SPEC.md`.
- **프론트(업무·칸반 모달 레이아웃)**: `#modalWorkHub`에서 `.work-hub-body--split` 제거 — 업무 항목 패널 **위**·칸반 패널 **아래**로 항상 세로 스택(`work-hub-body`). 칸반 3컬럼 가로 폭 확보. `index.html`, `styles.css`, `docs/FEATURE_SPEC.md`, `docs/DESIGN_GAP_CHECKLIST.md`, `docs/DESIGN_SYSTEM.md`, `docs/HANDOVER.md`.
- **프론트(IA·내비)**: 대시보드 상단 탭 제거 · `#btnAppShellHome`(ECH)로 시작 화면 복귀. `#viewWelcome`을 슬림 `.ech-home-*` 카드로 교체(구 히어로·3열 카드 제거). 좌측 **업무·칸반** 레일(`btnSidebarWorkHubWork`/`Kanban`, `openWorkHubFromTopNav`·`pendingWorkHubPanelFocus`·`#workHubPanelWork`/`Kanban`). 사이드바 워크스페이스 줄에서 ECH 로고 타일 제거·조직도만 유지(`sidebar-workspace--tools-only`). `README.md`·`FEATURE_SPEC.md`·`HANDOVER.md`·`DESIGN_SYSTEM.md`·`DESIGN_GAP_CHECKLIST.md` 반영.
- **프론트(관리자 레이아웃 리듬)**: `styles.css` — `#adminSection` 상단 여백(`margin-top`/`padding-top`) 확대, `.view-admin` 패딩 `clamp`, `.admin-panel-header` 줄바꿈·`gap`·다크 `h2` 트래킹, `.admin-hint`·`.admin-insight-grid` 여백·갭 정리.
- **프론트(관리자 사이드바)**: `index.html` `#adminSection` — `data-ech-design-ref="screen7-admin-rail"`, Material 아이콘(`account_tree`·`group`·`rocket_launch`·`settings`)·`sidebar-section--admin`. `app.js` `syncAdminSidebarActive`를 `showView`에 연동해 관리 뷰와 메뉴 `.active` 일치. `styles.css` 구역 구분선·라이트 인디고 틴트·히어로 `isolation`. `DESIGN_GAP_CHECKLIST.md` `(3)`·`FEATURE_SPEC.md`·`HANDOVER.md`·`DESIGN_SYSTEM.md` §6 표·`README.md`(관리자) 보강.
- **프론트(업무 허브 진입)**: `app.js` — `getDefaultChannelForWorkHub`·`openWorkHubModalForActiveChannel`·`openWorkHubFromTopNav`. 상단 **프로젝트**·환영 카드 **업무·칸반**은 채널 미선택 시 공개→비공개→DM 순으로 기본 채널을 잡아 `selectChannel` 후 모달 오픈. 채팅 헤더 `btnOpenWorkHub`는 채널 필수 동작 유지. `docs/FEATURE_SPEC.md`·환영 카드 카피(`index.html`)·`README.md`(업무 섹션) 반영.
- **프론트(다크 테마·디자인 갭)**: `frontend/styles.css` — `html:not([data-theme="light"])` 전역 보강(글로벌 바 blur·인셋 섀도·헤더 검색 포커스, 로그인 카드 글래스, 모달 본체·스크림 blur). ECH 채팅: 헤더·채널명(`#a5b4fc`)·멤버 패널 `top`/글래스·타임라인 그라데이션·날짜 구분선·컴포저 바/글래스 포커스·툴바·힌트. 환영 카드 다크 호버·슬레이트 아이콘 랩. 사이드바 활성 인디케이터 글로우. 관리자(인사이트 카드 호버·패널 헤더)·설정 히어로·사용자 분할·조직 탭 레일/본문·`#searchModal` 툴바·`modal-work-hub` 셸 보더.
- **프론트(다크 P1)**: `styles.css` 하단 블록 — `.btn-primary` 섀도, `work-hub-panel`, `release-upload-card`·`release-panel--main`, 칸반 컬럼·카드, `mention-suggest` 글래스, 프로필 히어로 아바타·`.profile-dl--cards`, 퀵 레일 인셋, `#adminSection` 라벨, `.orgchart-member-card`.
- **문서**: `docs/DESIGN_GAP_CHECKLIST.md`(§2·§3·§4·§6)·`docs/FEATURE_SPEC.md`(다크 테스트 기준)·`docs/HANDOVER.md`(다크 톤 안내).

## 2026-04-11

### Added
- **프론트(ECH채팅 목업 반영)**: `design/ECH채팅/code.html` 기준으로 `#viewChat` — 헤더 2줄(채널명 인디고·`#chatHeaderMeta`·터치 점)·햄버거 Material 아이콘, 날짜 구분선 좌우 라인, 라이트 말풍선/아바타 원형, 글래스 컴포저(`.ech-composer-glass`·장식 툴바·하단 힌트)·첨부/전송 Material 아이콘, 멤버 패널 제목「채널 상세」·`top` 보정. `app.js`에서 멤버 로드 시 `팀원 N명`·메타 표시.
- **문서**: `docs/DESIGN_SYSTEM.md` §1·§6에 `design/ECH채팅/`·`data-ech-design-ref="ech-chat"`·`.ech-composer-glass` 안내. `FEATURE_SPEC`·`HANDOVER`·`DESIGN_GAP_CHECKLIST` 갱신.
- **문서**: `docs/DESIGN_SYSTEM.md` §8 — Cursor MCP(Stitch 등)로 목업 생성 후 `design/` 저장·토큰 정합·`build:css`·갭 체크리스트까지의 앱 반영 절차.

### Changed
- **프론트(라이트 테마 P0 시각)**: `styles.css` — 글로벌 바 글래스·헤더 그림자, 헤더 검색 포커스, 모달 스크림(blur·채도), 멤버 패널 글래스·고스트 보더, 컴포저 포커스 링, `.btn-primary` 앰비언트 섀도, 환영 카드 보더 토큰화, 사이드바 섹션 라벨 다크 일관. `--outline-variant` 라이트 토큰 명시.
- **프론트(라이트 테마 2차 시각)**: `styles.css` — 관리자 인사이트 카드 호버·`org-tab-rail` 글래스, `#searchModal` 툴바/쿼리 입력·결과 행, `.btn-secondary` 라이트 톤, 컴포저 전송 버튼 섀도, 환영 히어로 그라데이션 섀도 강화.
- **문서**: `README.md`·`HANDOVER.md`·`FEATURE_SPEC.md`(테스트 기준)·`DESIGN_GAP_CHECKLIST.md` 변경 이력 반영.

## 2026-04-10

### Added
- **문서**: `docs/DESIGN_GAP_CHECKLIST.md` — `design/ECH메인`·`ECH화면설계 (1)~(9)` 대비 앱 구역별 갭 체크리스트·검증 방법·우선순위. `docs/DESIGN_SYSTEM.md` §7에서 링크.
- **프론트(ECH 화면설계 연동)**: `design/ECH화면설계 (1)~(9)` 구역별 마크업 훅 — 채팅 `#viewChat` (`.ech-region--chat`, `.ech-chat-header`, `.ech-messages-wrap`, `.ech-composer-bar`), 업무·칸반 `#modalWorkHub` (`.ech-workhub-shell`, 모달 폭 960px·라운드), 관리자 뷰 `.ech-region--admin` + `data-ech-design-ref`. `styles.css`에 타임라인 스크롤바·라이트 컴포저 바·관리자 패널 헤더 글래스 보강. `docs/DESIGN_SYSTEM.md` 섹션 6 매핑 표.

### Changed
- **문서**: `docs/DESIGN_GAP_CHECKLIST.md` 보강 — `(3)`에 `#viewReleases`, `(2)`에 퀵 레일·멘션 목록 행, §4 배포 뷰 검증 안내, 신규 담당자용 권장 순서(§7). 루트 `README.md`·`docs/DEVELOPER_README.md`에 갭 체크리스트 링크.
- **프론트(프로필 모달)**: `ECH화면설계 (9)` 카드형에 맞춰 `#modalUserProfile`을 히어로·부제·`.profile-dl--cards`·푸터(DM/닫기)로 정리. `openUserProfile`에서 부제에 부서·직급을 ` · `로 표시. `styles.css`에 `.ech-profile-modal`(overflow)·`.ech-profile-footer`(`.modal-footer`와 중복 테두리 제거) 등 보강. `DESIGN_SYSTEM.md` 매핑(9)·`FEATURE_SPEC.md`·`HANDOVER.md` 반영. 중복된 `.ech-profile-modal.modal` 규칙 정리.
- **문서**: `index.html`/유틸 변경 시 `npm run build:css` 안내 유지.
- **프론트(조직 관리 레이아웃)**: `viewOrgManagement`를 `ECH화면설계 (5)`에 맞춰 상단 인사이트 카드 + 좌측 세로 탭 레일(`org-tab-rail`)·우측 본문(`org-tab-main`) 2단으로 재구성. `updateOrgInsightMetrics()`로 현재 탭·표시 항목 수·저장 대기(`orgPendingCountMirror`) 동기화.
- **프론트(배포 관리·업무칸반 레이아웃)**: `viewReleases`에 인사이트 카드·`release-layout`(업로드/목록 2단)·`updateReleaseInsightMetrics()` 연동. `#modalWorkHub` 본문에 `.work-hub-body--split`으로 넓은 화면에서 업무·칸반 좌우 2열 배치. `loadReleases` DOM null 가드.

## 2026-04-09

### Added
- **프론트(상단 네비 동작)**: `index.html`에 `btnTopNavDashboard`·`btnTopNavProjects`·`btnTopNavTeam`. `app.js` — 대시보드는 환영 화면 복귀(`clearActiveChannelAndReload`), 프로젝트는 기존 업무·칸반 허브(`btnOpenWorkHub`, 채널 미선택 시 안내), 팀은 조직도(`btnOrgChart`). `setTopNavActive`·`syncTopNavFromMainView`로 `showView`·`showMain`·`openModal`/`closeModal`과 맞춰 활성 탭 동기화.

### Changed
- **프론트(업무·칸반 `ECH화면설계 (1)(6)` 정합)**: `index.html` — `#modalWorkHub` 모달 부제(`.ech-workhub-modal-subtitle`)·패널 `.work-hub-panel-head`/`.work-hub-panel-kicker`·`#workHubPanelKanban` `data-ech-design-ref="screen6-kanban"`. `app.js` — 업무 목록에 상태 칩(`.channel-work-item-chip--*`), 칸반 컬럼 헤더(`.kanban-column-head`·도트·카운트)·완료 유사 컬럼(`.kanban-column--done-like`). `styles.css` — 칸반 가로 스크롤·컬럼 고정 폭·카드/업무 행 라이트 톤·빈 컬럼 점선 안내. `docs/DESIGN_GAP_CHECKLIST.md` `(1)`·`(6)` 메모 갱신.
- **프론트(디자인 2차 — 관리자·설정·모달)**: `frontend/styles.css` 라이트 테마 — `--ech-tracking-headline`·`--ech-tracking-label`; 관리자 패널 헤더·인사이트 카드·배포 카드·데이터 테이블·설정 카드/캔버스·조직 탭 레일 No-Line 톤; 공통 `.modal`·헤더/푸터 인셋 구분선; 로그인 카드; 컨텍스트 메뉴 글래스; `#modalAddMemberPicker` `.ech-orgmap-window` 라운드·글래스. `docs/DESIGN_GAP_CHECKLIST.md` 타이포·멤버 피커 항목 갱신.
- **프론트(디자인 3차 — 검색·채팅 보조·업무·칸반)**: `styles.css` 라이트 — `#searchModal` 툴바·쿼리·포커스; `.mention-suggest` 글래스; `#modalThread` 스레드 답글 세로선; `#modalThreadHub` `.thread-hub-row`; `#modalWorkHub` `.ech-workhub-shell`·`.work-hub-panel`; `#channelKanbanBoard` 컬럼·카드. `DESIGN_GAP_CHECKLIST.md` (1)(2)(6)(9)·§4 스레드 허브 메모 갱신.
- **프론트(디자인 4차 — 파일·이미지·테마·토스트)**: `styles.css` 라이트 — `.mention-toast`·`.mention-toast-notice`; `#modalFileHub` `.file-hub-tabs`/`.file-hub-tab`; `#modalThemePicker` `.theme-option-btn`; 이미지 프리뷰 오버레이·닫기·캡션; `.user-search-results`; `#modalAppDialog`·`#modalImageDownloadChoice`·`#modalAppUpdate` 라이트 보강(app-dialog 글래스 헤더·입력 포커스, 다운로드 선택 버튼 톤 분리, 업데이트 안내 텍스트 카드). `DESIGN_GAP_CHECKLIST.md` §4 보강.
- **프론트(화면설계 구조 훅 보강)**: `index.html` 상단바를 `ECH화면설계 (4)` 기준으로 `대시보드/프로젝트/팀` 메뉴 버튼 구조로 정리하고 `data-ech-design-ref`를 `#appShellTopBar`, `#modalThreadHub`, `#modalWorkHub`, `#modalKanbanCardDetail`, `#modalUserProfile`에 추가. `styles.css`에 `.app-shell-nav-link*` 스타일 추가. `DESIGN_GAP_CHECKLIST.md` `(4)` 항목 메모 갱신.
- **프론트(화면설계 동기화)**: `design/ECH메인`·`ECH채팅`·`DESIGN.md`(Chromatic Sanctuary) 기준으로 `frontend/styles.css` 라이트 테마 전반 정리 — 글로벌 바 인디고 앰비언트 섀도·하단 보더 제거, 웰컴 카드/리스트 카드 No-Line(톤·섀도만), 히어로 그라데이션 보강, 채팅 헤더·상대 말풍선·컴포저 글래스(ECH채팅 `rounded-xl` 톤), 사이드바 사용자 구분선·활성 행·접기 탭. `index.html` — 워크스페이스 `corporate_fare` 로고 타일, 사이드바 검색·설정 Material 아이콘.
- **프론트(ECH메인 레이아웃)**: `design/ECH메인/code.html`에 맞춰 상단 글로벌 바(`app-shell-topbar`)·`app-shell-body`·채널 미선택 시 **대시보드형 환영 화면**(히어로·3열 카드·바로가기·Pro Tip). Tailwind 빌드: `frontend/package.json`, `src/tailwind-input.css` → `ech-tailwind.css`. Material Symbols·헤더 검색·환영 카드 클릭 동작 `app.js`. `docs/DESIGN_SYSTEM.md` 빌드 절차 보강.
- **fix(환영 대시보드)**: `ech-tailwind.css` 미로드 시 레이아웃 붕괴 — 환영 화면을 **`styles.css`의 `.ech-welcome-*` 시맨틱 스타일만**으로 재구성(Tailwind 유틸 의존 제거). 히어로·그리드·카드 푸터·바로가기 리스트 정렬.
- **프론트(디자인 시스템)**: `design/ECH메인`·`ECH화면설계`·DESIGN.md(Atmospheric) 기준으로 `frontend/styles.css` 라이트 테마 — 고스트 보더·사이드바 톤·채널 헤더 글래스·모달 등. `frontend/tailwind.config.js`에 `content`/그림자/타이포 확장. 롤백: `localStorage` `ech_design_version` 또는 `frontend/design-backup/legacy-design/`. 문서 `docs/DESIGN_SYSTEM.md`, `docs/HANDOVER.md`, 루트 `README.md` 문서 표, `design/README.md` 보강.
- **프론트(화면설계 1~9 구역 적용)**: `frontend/index.html`에 구역 훅 클래스(`.ech-region--chat`, `.ech-messages-wrap`, `.ech-composer-bar`, `.ech-region--admin`, `.ech-workhub-shell`)를 추가하고, `styles.css`에서 채팅/관리자/업무칸반 영역의 시맨틱 스코프 스타일을 보강. `docs/DESIGN_SYSTEM.md` 화면 매핑 표와 `docs/FEATURE_SPEC.md` 기능 기준을 함께 갱신.
- **프론트(화면설계 2차 디테일 매칭)**: `styles.css` 라이트 테마에서 관리자 영역의 테이블/카드/조직탭/조직선택 패널 톤을 `ECH화면설계 (5)(7)(8)`에 맞춰 보강(고스트 보더·소프트 섀도·선택 그라데이션). 사용자 관리 분할 패널·설정 카드·관리 테이블 행 hover 가독성 개선.
- **프론트(화면설계 2차 디테일 매칭-채팅)**: `ECH화면설계 (2)` 톤에 맞춰 채팅 타임라인 라이트 테마를 보강(`.ech-messages-wrap .messages` 배경 레이어, 메시지 말풍선 보더/섀도, 메타/시간 대비, `.composer-inner` 고스트 보더·소프트 섀도). 채팅 가독성은 유지하면서 카드형 밀도를 개선.
- **프론트(레이아웃 구조 변경)**: 톤 조정 중심에서 확장해 `viewUserManagement`·`viewSettings` 화면 구조 자체를 재배치. 사용자 관리 상단에 인사이트 카드(조회 대상/선택 조직 인원/저장 대기)를 추가하고 `app.js`에서 목록 렌더 시 실시간 동기화. 설정 화면은 Hero 카드 + 좌측 입력 패널/우측 설정 목록 캔버스(`settings-layout`) 2열 구조로 변경.
- **프론트(레이아웃 재구성)**: `viewUserManagement`에 상단 인사이트 카드 3종(조회 대상/선택 조직 인원/저장 대기 변경)을 추가하고 `app.js`에서 패널 제목·인원·대기 건수를 동기화. `viewSettings`는 상단 히어로 카드 + 우측 설정 목록 캔버스 + 좌측 추가 폼(2열 레이아웃)으로 구조를 재배치해 화면 형태가 목업처럼 즉시 체감되도록 변경.
- **문서(README)**: 제품 소개용 `README.md`에 이모지·강조·표·섹션 확장 — 기능을 영역별로 세분화해 가독성·인상 강화, 기술 스택 요약 표 추가. 세부 API는 `docs/DEVELOPER_README.md`로 안내 유지.
- **문서**: 루트 `README.md`를 제품·기능 소개 중심으로 정리하고, 기존 개발자·API·실행 안내 전체는 `docs/DEVELOPER_README.md`로 이전. `docs/FEATURE_SPEC.md`에 문서 역할 안내 문구 추가. `.cursor/rules/core-rules.mdc`, `docs/PROJECT_REQUIREMENTS.md`, `docs/HANDOVER.md`에 README·DEVELOPER_README 역할 구분 반영.

### Release
- **GitHub `v1.1.3`**: 내 업무 사이드바 **채널 표시명**(DM)·업무 목록 **선택 강조**, 채팅 첨부 **ZIP 일괄**·**저장 후 열기**(Electron 저장 대화상자) 등 `0a3697e` 이후 반영. `desktop/package.json`·`desktop/package-lock.json`·`backend/build.gradle` **`1.1.3`**. 태그 `v1.1.3` — `cd backend && .\\gradlew.bat bootJar`, `cd desktop && npm run build:win` 후 `tools/publish-electron-github-release.ps1 v1.1.3`

## 2026-04-08

### Changed
- **프론트(업무·칸반)**: 사이드바 **내 업무 항목** 채널 표시 — DM 내부명(`_dm__...`) 대신 **표시용 이름**(`displayChannelLabelForWorkSidebar` + API). 모달 업무 목록 **선택 행 강조**(`channel-work-item--selected`). **백엔드** `GET /api/work-items/sidebar/by-assigned-cards`의 `channelName`은 DM일 때 `description` 우선. `frontend/app.js`, `frontend/styles.css`, `backend/.../WorkItemService.java`, `docs/FEATURE_SPEC.md`
- **프론트(채팅 첨부)**: 이미지 **1장만**일 때 2열 그리드 빈 칸 제거(`grid-column: 1 / -1`). **일괄저장**은 **JSZip**으로 **ZIP 한 파일** 다운로드(CDN). **FILE 메시지 직후** 다음 줄은 **아바타 새 발화**(`shouldShowAvatarForMessage`·`appendMessageRealtime`). **저장 후 열기**: 브라우저는 다운로드 후 새 탭; **Electron**은 **저장 대화상자** 후 `shell.openPath`(`ech-save-file-and-open-default-app`). `frontend/app.js`, `frontend/index.html`, `frontend/styles.css`, `desktop/main.js`, `desktop/preload.js`, `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`

## 2026-04-07

### Release
- **GitHub `v1.1.2`**: 채팅 **텍스트 말풍선**·**행 간격** 가독성 개선(위 `Changed` 항목). `desktop/package.json`·`desktop/package-lock.json`·`backend/build.gradle` **`1.1.2`**. 태그 `v1.1.2` — `cd desktop && npm run build:win` 후 `tools/publish-electron-github-release.ps1 v1.1.2`

### Changed
- **프론트(채팅 가독성)**: 텍스트 메시지에 **말풍선**(배경·테두리·모서리 반경·가벼운 그림자), 상대/본인 색 구분(`--msg-bubble-other-*` / `--msg-bubble-mine-*`, 라이트/다크 테마). **본인** 본문은 말풍선 안 **좌측 정렬**, 시각은 말풍선 옆 하단 정렬; **상대**는 flex로 본문·시각 분리. `msg-chat` 행 **세로 간격** 확대. `frontend/styles.css`, `docs/FEATURE_SPEC.md`

## 2026-04-06

### Release
- **GitHub `v1.1.1`**: 데스크톱 **저장 후 열기** — Electron에서 임시 파일 저장 후 `shell.openPath`로 **OS 기본 앱** 연결(Windows 메모장·Office 등). 브라우저 전용은 기존처럼 blob 새 탭. `desktop/package.json`·`desktop/package-lock.json`·`backend/build.gradle` **`1.1.1`**. 태그 `v1.1.1` — `cd desktop && npm run build:win` 후 `tools/publish-electron-github-release.ps1 v1.1.1`

### Changed
- **데스크톱(Electron)**: 채널 첨부 **저장 후 열기** 시 브라우저 탭 대신 **임시 파일 저장 후 `shell.openPath`로 OS 기본 앱** 연결(예: Windows `.txt` → 메모장·연결된 앱). IPC `ech-open-temp-file-default-app`, `preload` `openTempFileWithDefaultApp`. **브라우저 전용** 접속은 기존처럼 blob 새 탭. `desktop/main.js`, `desktop/preload.js`, `frontend/app.js`, `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`, `README.md`
- **프론트(채팅)**: **본인 메시지**(`msg-mine`)의 **타임스탬프**를 본문·첨부 푸터에서 **말풍선 콘텐츠 왼쪽**에 배치(텍스트 `row-reverse`, 첨부 푸터 `flex-start`, 그룹 푸터 `row-reverse`). 답글 배너 스니펫: **단일 이미지 그리드 행**에서도 썸네일 `title` 폴백. `frontend/styles.css`, `frontend/app.js`, `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`
- **프론트(채팅 첨부)**: 다중 **이미지**는 한 메시지 묶음에서 **2열 그리드** 썸네일(줄당 2개 후 개행), 클릭 시 **라이트박스**에서 다운로드; **뷰어로 열기** 제거. 다중 **비이미지**는 한 말풍선에 **세로 카드**(저장·저장 후 열기만). 답글 배너 스니펫은 이미지 썸네일 `title` 폴백. `app.js`, `styles.css`, `docs/FEATURE_SPEC.md`, `README.md`, `docs/HANDOVER.md`

### Fixed
- **프론트(업무·칸반)**: 업무 **소프트 삭제(✕)** 시 목록에서 즉시 제거되지 않고 **삭제 예정** 배지·삭제 취소 표시. **완전 삭제** 저장 시 서버에서 카드가 먼저 삭제되어 남은 `PUT /kanban/cards/{id}`가 실패하던 문제 — purge 직전·`queueWorkItemPurge` 시 해당 업무의 카드 ID를 pending 맵에서 제거. `app.js`, `styles.css`, `docs/FEATURE_SPEC.md`

### Changed
- **프론트(채팅)**: 첨부(이미지·문서 공통)를 **카드 리스트**로 표시 — 파일명·크기 + **저장** / **저장 후 열기** / **뷰어로 열기**(이미지는 라이트박스, 그 외는 새 탭 blob). **연속 FILE 메시지**(이미지+문서 혼합 가능, 같은 분·같은 발신자, 스레드 댓글 없음)는 `tryConsumeFileAttachmentGroup` → `createFileAttachmentGroupRowFromMsgs`로 묶고 **일괄저장**. `fetchChannelFileBlob` 등. `app.js`, `styles.css`, `docs/HANDOVER.md`

### Fixed
- **프론트(업무·칸반)**: 비활성 업무 **완전 삭제** 확인(`uiConfirm`)이 업무 상세 모달 뒤에 가리던 문제 — `#modalAppDialog` z-index 상향. 업무 목록에서 **복원**·**완전 삭제**·예약 **취소** 가능, 복원/완전삭제 예약은 배지로 표시(`queueWorkItemRestore`/`queueWorkItemPurge` 공통화). `index.html` 안내, `styles.css` 배지·행 강조

### Changed
- **프론트**: 메인·스레드 메시지 입력을 **`textarea`** 로 전환 — **Shift+Enter** 줄바꿈·**Enter** 전송, 타임라인 본문은 `formatMessageWithMentions` 후 `\n` → `<br>` 로 표시; **본인 메시지**는 **오른쪽 정렬**·아바타·발신자 행 미표시(`msg-mine`/`msg-body--mine`); **채널 전환 시** 입력·답글 대상·대기 첨부를 **채널별로 저장/복원**(`composerDraftByChannelId`, 로그아웃 시 `clear`). `index.html`, `app.js`, `styles.css`
- **프론트**: 「새 메시지」구분선(`msg-read-anchor-divider`) — 입력창에 **글자를 입력**하거나 **전송 성공** 시 제거(`clearChatReadAnchorUi`); 본인 전송으로 인한 `loadMessages`/`uploadFile` 갱신 시 구분선 **재삽입 생략**(`loadMessages` `skipNewMsgsDivider`, `uploadFile` `skipNewMsgsDividerAfterReload`). `README.md`

### Added
- **프론트**: 채팅 영역(`#viewChat`) **드래그 앤 드롭**으로 이미지·일반 파일 첨부, **파일 선택·클립보드 붙여넣기**로 **여러 파일 한 번에** 미리보기·순차 업로드; 스레드 댓글 입력도 다중 선택·다중 전송. `index.html` `multiple`, `app.js` `pendingFilesQueue`/`threadPendingFilesQueue`, `styles.css` `.view-chat--drag-over`

### Release
- **GitHub `v1.1.0`**: 정식 마이너 릴리즈 — **이미지** 라이트박스(서버 미리보기 우선·원본 보기)·파일 허브 이미지 탭 지연 로드, **데스크톱** Windows 시작 시 실행(트레이·`setLoginItemSettings`), **프론트** 첨부 직후 「새 메시지」 구분선·동일 채널 알림(`document.hidden`) 수정, GitHub 릴리즈 Source code(zip) 안내 문서. `desktop/package.json`·`desktop/package-lock.json`·`backend/build.gradle` **`1.1.0`**. 태그 `v1.1.0` — GitHub 에셋 업로드: `cd desktop && npm run build:win` 후 `tools/publish-electron-github-release.ps1 v1.1.0`

### Fixed
- **프론트**: 첨부·이미지 업로드 직후 `loadMessages` 시 읽음 포인터보다 앞선 타이밍으로 **내 메시지 앞에만** 「새 메시지」 구분선이 뜨던 현상 — lastRead 이후 DOM에 **다른 사람 메시지가 없으면** 구분선 미삽입(`hasChatMessageFromOtherAfter`). 동일 채널 일반 알림은 `document.hidden` 기준으로 정리(파일 대화상자 포커스 이슈). `docs/FEATURE_SPEC.md` 읽음 포인터 비고

### Added
- **데스크톱(Electron)**: Windows 시작 시 자동 실행 — 트레이 우클릭 메뉴 체크박스, `app.setLoginItemSettings`; IPC `ech-get-open-at-login` / `ech-set-open-at-login`·`preload` 노출. `README.md`, `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`, `docs/DEPLOYMENT_WINDOWS.md`

### Changed
- **프론트**: 이미지 모아보기·인라인·검색 **크게 보기** — 서버 미리보기가 있으면 라이트박스에 먼저 `/preview`(JPEG) 표시, **원본 보기**로 `variant=original` 전환; 파일 허브 이미지 탭 썸네일은 스크롤 영역 진입 시에만 로드(IntersectionObserver)·탭 전환 시 재observe. `index.html` `imagePreviewLoadOriginal`, `styles.css` 모달 보조 버튼

### Docs
- **`docs/FEATURE_SPEC.md`**, **`README.md`**, **`docs/HANDOVER.md`**: 이미지 라이트박스(미리보기 우선·원본 보기)·파일 허브 그리드 지연 로드
- **GitHub 릴리즈**: **Source code (zip/tar.gz)** 는 GitHub가 태그 기준으로 **자동 생성**하는 항목이며, `publish-electron-github-release.ps1` 업로드 대상(설치 exe·`latest.yml`·blockmap)과 구분 — `docs/HANDOVER.md`, `docs/DEPLOYMENT_WINDOWS.md`, `README.md`, `tools/publish-electron-github-release.ps1` 주석

### Release
- **GitHub `v1.0.0`**: **운영 실제 배포** — 기능·코드 변경 없이 **1.0.0** 정식 버전 표기(Semantic Versioning). `v0.1.9`와 **동일 코드베이스**. `desktop/package.json`·`backend/build.gradle` **`1.0.0`**

### Fixed
- **백엔드**: 스레드 **댓글 수**·스레드 모아보기·`GET .../messages/{rootId}/replies` — 타임라인 **답글(REPLY_*)**은 댓글로 집계·나열하지 않음(원글 자식만 `COMMENT_*`). 댓글 ID로 여는 `/replies`는 REPLY·중첩 댓글 포함
- **프론트**: `message:new`가 **현재 채널**일 때만 처리하던 경로에서 **최소화·트레이·다른 창 포커스**여도 일반 메시지 OS/토스트가 안 뜨던 문제 — 백그라운드면 `pushNewMessageToast` 호출(내 멘션은 `pushMentionToast`와 중복 안 함)
- **백엔드**: 스레드 댓글 수 집계 `aggregateThreadCommentsForRoots`가 **직접 부모 id**만 쓰면 루트와 불일치·중복 행 시 오류 가능 — **루트 메시지 id**로 합산, 동일 메시지 id 중복 제거

### Changed
- **프론트**: 채팅 이미지 blob URL — `loadMessages`·채널 전환 시 **전부 revoke** 하지 않고 세션 캐시 재사용(같은 채널 재입장·새로고침 시 네트워크 재다운로드 감소). 메모리 상한 **약 120개** LRU로 정리, 로그아웃(`clearSession`) 시 전부 해제

### Release
- **GitHub `v0.1.9`**: **백그라운드(최소화·트레이) 동일 채널**에서도 일반 메시지 OS/토스트·**스레드 댓글 수** 루트 기준 집계 수정·`v0.1.8` 이후 누적. `desktop/package.json`·`backend/build.gradle` **`0.1.9`**
- **GitHub `v0.1.8`**: 채팅 **이미지 blob 세션 캐시**(재입장·타임라인 갱신 시 재다운로드 감소·LRU)·`v0.1.7` 이후 누적. `desktop/package.json`·`backend/build.gradle` **`0.1.8`**
- **GitHub `v0.1.7`**: 이미지 **서버 미리보기**(`multipart preview`, `GET .../preview`, 다운로드 `variant=original|preview`)·원본/미리보기 **용량 표시**·`channel_files` `preview_*` 컬럼(마이그레이션 `docs/sql/migrate_channel_files_preview.sql`). `desktop/package.json`·`backend/build.gradle` **`0.1.7`**

### Changed
- **프론트·백엔드(연동 완료)**: 이미지 업로드 시 `file`=원본, 선택 `preview`=업로드 전 JPEG 압축본을 함께 전송해 서버에 **미리보기 스토리지** 저장; 인라인·썸네일은 `GET .../preview`, 라이트박스는 `variant=original` 스트림. 다운로드 모달은 서버 미리보기가 있으면 **원본/미리보기** 각각 `download-info` 기준 용량 표시, 없으면 기존처럼 클라이언트 JPEG 재인코딩 안내
- **프론트**: `modalImageDownloadChoice` 두 번째 버튼 표시 문구를 **미리보기(압축)** 로 통일

### Fixed
- **프론트**: 이미지 다운로드 선택 모달 — **원본/압축** 모두 버튼 형태(`.btn-secondary` 기본 스타일 추가), 각 버튼에 **용량·안내** 표시; 서버 저장 크기 vs 파일명 혼동 방지 안내 문구

### Docs
- **`docs/DEPLOYMENT_WINDOWS.md`**: 내부망 **데스크톱 자동 업데이트** — 동료·운영자용 **동작 개념**(한 줄 요약·비유·흐름·Mermaid·역할 표) 및 기존 번호 목록을 **운영 절차**로 정리; `latest.yml`/`path` 오타(디스크上的→디스크의) 수정
- **`README.md`**: 내부망 자동 업데이트 설명을 `DEPLOYMENT_WINDOWS.md` 해당 절로 안내

### Added
- **프론트**: 대용량(약 512KB 이상) **이미지** 다운로드 시 `modalImageDownloadChoice`로 **원본** / **JPEG 압축본** 선택; GIF·SVG는 원본만
- **프론트**: DM **채팅 헤더**(`#chatChannelPrefix`)를 사이드바와 동일한 **프레즌스 점** 표시(`updateChatHeaderDmPresence`·`dmSidebarLeadingHtml`)

### Changed
- **프론트**: `INLINE_IMAGE_EXT`를 `isImageContentType`보다 앞에 선언해 이미지 판별 시 참조 순서 정리

### Release
- **GitHub `v0.1.6`**: DM 헤더 프레즌스 점·대용량 이미지 다운로드 원본/JPEG 선택·`v0.1.5` 이후 누적 반영. `desktop/package.json`·`backend/build.gradle` **`0.1.6`**
- **GitHub `v0.1.5`**: 구성원 추가 **선택 부서원** 카드형 목록 UI·`v0.1.4` 이후 누적 반영. `desktop/package.json`·`backend/build.gradle` **`0.1.5`**

### Fixed
- **프론트**: 구성원 추가 피커 우측 **선택 부서원** 목록 — 인원별 **카드형 칸** 복구(테두리·배경·간격). 기존 행 구분선이 라이트 테마에서 거의 보이지 않던 문제 보완

### Changed
- **desktop**: NSIS **`perMachine: true`** — Windows에 **`%PROGRAMFILES%\ECH\`** 전역 설치(UAC). `ech-server.json`은 exe 옆 또는 **`%ProgramData%\ECH\ech-server.json`** 추가 지원(`readEchServerJson`)

### Release
- **GitHub `v0.1.4`**: Program Files 설치·`ech-server.json` ProgramData 경로·문서 반영. `desktop/package.json`·`backend/build.gradle` **`0.1.4`**
- **GitHub `v0.1.3`**: Windows **`.ico` 임베드**로 설치본·작업 표시줄 아이콘이 ECH 브랜드로 표시되도록 정리(`icon.ico`·`prebuild:win`·`AppUserModelId`). `desktop/package.json`·`backend/build.gradle` **`0.1.3`**

### Fixed
- **desktop**: Windows **프로그램·작업 표시줄 아이콘이 Electron 기본으로 보이던 문제** — `assets/icon.ico` 생성(`png-to-ico`·`prebuild:win`), `electron-builder` `win.icon`·런타임 `BrowserWindow`/`Tray`가 **`.ico` 우선** 사용, `app.setAppUserModelId` 추가

### Release
- **GitHub `v0.1.2`**: 프론트 대용량 첨부·이미지 전송 개선(미리보기 다운스케일·업로드 전 압축·진행률·갱신 병렬화) 및 `v0.1.1` 이후 누적 반영. `desktop/package.json`·`backend/build.gradle` **`0.1.2`**

### Changed
- **프론트**: 대용량 이미지·첨부 전송 개선 — 미리보기는 `createImageBitmap`+캔버스로 다운스케일(256KB 초과 이미지), 2MB 초과 이미지는 업로드 전 해상도·JPEG 압축, XHR로 **업로드 진행률** 표시, 타임라인·파일 허브 갱신 `Promise.all` 병렬화

### Release
- **GitHub `v0.1.1`**: 데스크톱 UX 정비(아이콘 통일·단일 인스턴스·조직 정렬·사이드바 로그아웃 제거) 반영. `desktop/package.json`·`backend/build.gradle` **`0.1.1`**
- **desktop `assets/icon.png`**: NSIS 빌드(`win.icon`) 요구 **최소 256×256** 충족을 위해 동일 디자인 업스케일(고품질 보간)

### Changed
- **백엔드**: `build.gradle` 프로젝트 `version` `0.1.0`으로 상향, `bootJar` 산출물명 `ech-backend-{version}.jar` 고정(데스크톱 0.1.0과 맞춤)
- **desktop**: `assets/tray-icon.png` → `assets/icon.png` 통합, 트레이·창·NSIS 빌드(`win.icon`)에 동일 아이콘 사용
- **desktop** `main.js`: Windows 트레이 이미지를 **16×16**으로 리사이즈 후 `Tray`에 설정(큰 PNG 그대로 쓸 때 기본 Electron 아이콘처럼 보이는 현상 완화)
- **desktop**: `app.requestSingleInstanceLock()` — 이미 실행 중이면 두 번째 프로세스 종료, 재실행 시 기존 창 포커스(`second-instance`)
- **프론트**: 조직 인원 정렬 `sortOrgDirectoryMembers` — 조직도 모달·구성원 추가 피커·관리자 사용자 표에 공통 적용(팀장 → 직급 → 이름 가나다). 사이드바 **로그아웃** 버튼 제거

### Added
- **release**: GitHub **`v0.1.0`** — 데스크톱 마이너 첫 릴리즈(자동 업데이트·내부망 피드·UI 등 누적). `package.json` `0.1.0`
- **desktop**: 자동 업데이트 다운로드 완료 시 메인 UI 모달(`modalAppUpdate`)·`preload` IPC(`onUpdateDownloaded`, `installUpdateAndRestart`)·`quitAndInstall` 즉시 재시작. `checkForUpdatesAndNotify` 제거(토스트 중복 방지)
- **desktop**: 창 제목·트레이 툴팁에 앱 버전(`app.getVersion()`) 표시

### Changed
- **프론트**: 구성원 추가 `+` 버튼 — 텍스트 `+` 대신 SVG 십자(18px→14px·선 1.5)로 박스 내부 시각 중심 정렬, `.member-add-heading` 행은 좌측 정렬 유지
- **백엔드**: `UserSearchResponse`·`UserRepository.searchUsers`·`UserSearchService` 에 `createdAt` 포함(기타 기능용; 조직 목록 정렬은 프론트 `sortOrgDirectoryMembers` 기준으로 통일)
- **`docs/FEATURE_SPEC.md`**: 위 UX·API 반영

## 2026-04-03

### Added
- **백엔드**: 관리자 `GET /api/admin/storage/probe` — 현재 `file.storage.base-dir` 에 대해 JVM 기준 쓰기·삭제 프로브(UNC·Local System 이슈 진단). 공통 로직 `FileStorageAccessProbe`
- **백엔드**: 기동 시 첨부 저장 경로 쓰기 검사 `FileStorageStartupValidator` — 로그 `[ECH] file storage ready:` / `NOT writable`
- **문서**: `DEPLOYMENT_WINDOWS`·`HANDOVER`·`FEATURE_SPEC`·`README` 에 프로브 API·UNC 검증 주의(대화형 PowerShell ≠ 서비스) 반영

### Changed
- **백엔드**: `GlobalExceptionHandler` — `MaxUploadSizeExceededException` → 413 및 용량/Nginx 안내; 파일 IO 실패 메시지에 저장 경로·권한 힌트
- **`docs/DEPLOYMENT_WINDOWS.md`**: `file.storage.base-dir` DB 우선·첨부 업로드 트러블슈팅·SQL 예시
- **`docs/HANDOVER.md`**: 첨부 경로(`app_settings` 우선) 운영 메모

### Added
- **내부망 Electron 자동 업데이트**: Spring Boot `/desktop-updates/**` → `DesktopUpdateResourceConfig` (`{APP_RELEASES_DIR}/desktop` 또는 `DESKTOP_UPDATE_DIR`)
- **desktop**: `ech-server.json`에 `serverUrl` 또는 `updateBaseUrl`이 있으면 `electron-updater`를 `generic` 피드로 전환(GitHub 불필요). NSIS 산출물명 `ECH-Setup-${version}.exe` (`artifactName`)

### Changed
- **`.gitignore`**: `deploy/ECH-deploy.zip`, `deploy/package/` 제외 (로컬 빌드 산출물)
- **데스크톱**: `package.json` 버전 `0.0.9` (릴리즈)
- **`README.md`**: 내부망 데스크톱 자동 업데이트(`/desktop-updates`, `ech-server.json`) 안내 추가
- **`docs/FEATURE_SPEC.md`**: Electron 내부망 generic 피드·백엔드 정적 경로 설명 추가
- **`docs/DEPLOYMENT_WINDOWS.md`**: 이후 배포 절차에 `frontend/` 복사·ZIP 통째 갱신·`desktop-updates` 검증 보강(JAR만 교체 시 웹 UI 미갱신 이유 명시)

### Added
- **관리자 기초설정(app_settings) 직접 추가**
  - `POST /api/admin/settings` (ADMIN): `CreateSettingRequest`(키·값·설명·updatedBy), 키는 `^[a-zA-Z0-9._-]+$`·최대 100자, 중복 키 거부
  - 관리자 **설정** 화면에 「기초설정 추가」 카드(키/값/설명·추가 버튼) 및 스타일(`.settings-add-*`)

### Fixed
- **조직도 본부 토글 UX 개선**: 본부 이름 클릭 시 접기/펼치기 동작 제거 → `▶` 화살표 버튼에서만 토글, `<details>/<summary>` 구조를 `div + data-expanded` 방식으로 교체
- **조직도 노드 레이블 정리**: 본부/회사 직속 멤버 표시 시 헤더에서 `(직속)` 문자열 제거
- **사용자 관리 좌측 패널 필터 수정**: 본부/회사 노드 선택 시 하위 팀 전체 인원이 표시되던 문제 수정 → 해당 노드에 직속 배정된 인원만 표시되도록 변경 (`renderAdminUserTable`, `countForOrg`)
- **사용자 설정 부서 셀렉트박스 수정**: `getOrgGroupOptions()` 가 TEAM 타입만 반환해 본부/회사를 선택할 수 없던 문제 수정 → COMPANY(`[회사] `), DIVISION(`[본부] `), TEAM 순으로 통합 반환

### Added
- **조직도 모달 기능 추가**
  - 사이드바 워크스페이스 헤더(ECH 영역) 우측에 조직도 버튼(`btn-workspace-tool`) 추가
  - `modalOrgChart` 모달: 좌측 조직 트리(회사→본부→팀) + 우측 팀 멤버 카드 그리드 레이아웃
  - `/api/user-directory/organization` API 활용, MEMBER 이상 접근 가능
  - 팀 선택 시 멤버 카드(이름·사번·직급·직위·직책 배지) 표시, 새로고침 버튼 지원
  - `styles.css`: `.btn-workspace-tool`, `.modal-orgchart`, `.orgchart-*` 스타일 추가
- **조직도 모달 멤버 정렬 및 레이아웃 개선**
  - 팀장(직책) 최우선 → 부장→차장→과장→대리→사원→인턴→기타 직급 순 → 이름 가나다 순 정렬
  - flex `min-height: 0` 누락으로 모달 내용이 잘리던 문제 수정 (tree/member pane 전체)
- **조직도 본부/회사 직속 멤버 지원**
  - `OrgDivisionResponse`, `OrgCompanyResponse` DTO에 `directMembers` 필드 추가
  - `UserSearchService.getOrganizationTree()`: TEAM 멤버십이 DIVISION/COMPANY 코드를 가리키는 사용자(본부장 등) 조회 로직 추가
  - 프론트엔드: 본부·회사 노드를 클릭하면 직속 멤버 표시, 직속 인원 수 배지 표시

### Fixed
- **사용자 삭제 연쇄 FK 오류 3차 수정 (실제 DB에 CASCADE 전무 대응)**
  - kanban_boards → kanban_columns RESTRICT: KanbanColumnRepository.deleteByBoardCreatorEmployeeNo() 추가
  - kanban_cards → kanban_card_assignees RESTRICT: KanbanCardAssigneeRepository.deleteAllRelatedToEmployeeNo() 추가
  - kanban_card_events (보드 소속 카드 이벤트): KanbanCardEventRepository.deleteAllRelatedToEmployeeNo()로 통합
  - kanban_cards (보드 소속 카드): KanbanCardRepository.deleteByBoardCreatorEmployeeNo() 추가
  - messages → channel_read_states RESTRICT: ChannelReadStateRepository.nullLastReadRefByEmployeeNo() 추가
  - channels → channel_members RESTRICT: ChannelMemberRepository.deleteAllRelatedToEmployeeNo() 추가
  - channels → channel_read_states RESTRICT: ChannelReadStateRepository.deleteAllRelatedToEmployeeNo() 추가
  - messages (user's channels 전체): MessageRepository.nullParentRefByChannelCreatorEmployeeNo(), deleteByChannelCreatorEmployeeNo() 추가
  - AdminUserService.deleteUser() 19단계 완전 수동 삭제 순서로 전면 개편
  - 주입 의존성 추가: KanbanCardAssigneeRepository, KanbanColumnRepository, ChannelReadStateRepository, ChannelMemberRepository

- **사용자 삭제 연쇄 FK 오류 2차 수정 (실제 DB 제약 기반)**
  - kanban_cards.work_item_id RESTRICT 대비: 삭제 전 NULL 초기화 추가
  - messages.parent_message_id 자기참조 RESTRICT 대비: 자식 메시지 NULL 초기화 추가
  - channel_files.channel_id CASCADE 없는 경우 대비: 채널 삭제 전 파일 전체 삭제 추가
  - AdminUserService.deleteUser() 11단계 완전 삭제 순서로 정비
  - KanbanCardRepository, MessageRepository, ChannelFileRepository 쿼리 추가
- **DataIntegrityViolation 에러 응답에 실제 DB 원인 메시지 포함**
  - `GlobalExceptionHandler.handleDataIntegrity()` — PostgreSQL root cause 메시지 추출
  - 에러 응답: "DB 제약 위반 [원인: ...]" 으로 개선
- **사용자 완전 삭제 시 DB 제약 위반 오류 해소**
  - `AdminUserService.deleteUser()` — 단순 `delete()` 대신 FK 순서에 맞는 연쇄 삭제 로직으로 교체
  - 삭제 순서: kanban_card_events → work_items(채널 기준) → work_items(생성자 기준) → kanban_boards → messages → channel_files → channels → users
  - `KanbanCardEventRepository.deleteByActorEmployeeNo()` 네이티브 쿼리 추가
  - `KanbanBoardRepository.deleteByCreatorEmployeeNo()` 네이티브 쿼리 추가
  - `WorkItemRepository.deleteByCreatorEmployeeNo()` / `deleteBySourceChannelCreatorEmployeeNo()` 추가
  - `MessageRepository.deleteBySenderEmployeeNo()` 네이티브 쿼리 추가
  - `ChannelFileRepository.deleteByUploaderEmployeeNo()` 네이티브 쿼리 추가
  - `ChannelRepository.deleteByCreatorEmployeeNo()` 네이티브 쿼리 추가
  - `UserRepository.deleteByEmployeeNo()` JPQL 쿼리 추가

### Changed
- **조직 그룹 `group_path` 저장 형식 변경: 표시명+슬래시 → 코드+세미콜론**
  - 변경 전: `코비젼/CS사업본부/CS영업팀` (display_name + `/`)
  - 변경 후: `ORGROOT;ORG;TEAM_CODE` (group_code + `;`)
  - `AdminOrgService.computeGroupPath()` — 그룹 코드 세미콜론 체인으로 변경
  - `AdminOrgService.updateDescendantPaths()` — 코드 체인으로 재계산
  - 프론트엔드 미리보기(`#ogPathPreview`)는 `buildDisplayPath()` 헬퍼로 display name `/` 표시 유지
- **사용자 관리 UI: 좌우 분할 패널로 개편**
  - 좌측: 조직 트리 패널 (COMPANY > DIVISION > TEAM, 접기/펼치기, 조직별 인원 수 배지)
  - 조직 선택 시 우측 사용자 목록이 해당 조직 및 하위 팀으로 필터링
  - 기존 트리/목록 토글 버튼 제거, 분할 패널로 대체
  - `renderUserOrgPanel()`, `getAllDescendantTeamCodes()` 함수 추가
  - `adminUserSelectedOrgCode` / `userOrgPanelExpanded` 상태 변수로 관리
- **신규 사용자 등록 시 기본 비밀번호 `Test1234!` 자동 설정**
  - `AdminUserService.createUser()` — `PasswordEncoder.encode("Test1234!")` 적용
  - `PasswordEncoder` 인터페이스로 주입 (테스트 컨텍스트 호환)
- **사원번호·그룹코드 수정 가능하도록 개선**
  - `User.setEmployeeNo()` setter 추가 — 기존 `employeeNo` 불변 제약 해제
  - `OrgGroup.setGroupCode()` setter 추가
  - `OrgGroupRepository.updateMemberOfGroupCode()` — 코드 변경 시 자식 그룹 member_of_group_code 일괄 갱신
  - `OrgGroupMemberRepository.updateGroupCode()` — 코드 변경 시 org_group_members.group_code 네이티브 갱신
  - `AdminUserService.updateUser()` — 사원번호 변경 요청 처리 및 중복 검사
  - `AdminOrgService.updateOrgGroup()` — 그룹코드 변경 요청 처리 및 FK 연쇄 갱신
  - 프론트엔드: 사용자 편집 모달 사원번호 필드 활성화, 변경 시 경고 표시
  - 프론트엔드: 조직 그룹 편집 모달 코드 필드 활성화, 변경 시 경고 표시
  - 프론트엔드: pending 저장 시 원래 코드를 URL에, 새 코드를 body에 전송

### Added
- **조직 관리 기능 구현** (관리자 전용)
  - 사이드바 관리자 메뉴에 `🏢 조직 관리` 항목 추가
  - `GET/POST/PUT/DELETE /api/admin/org-groups` API (`AdminOrgController`, `AdminOrgService`)
  - `OrgGroupResponse`, `OrgGroupSaveRequest` DTO 신규 작성
  - `OrgGroup` 엔티티에 `setSortOrder()`, `setIsActive()` setter 추가 + 기존 setter에 `updatedAt` 갱신
  - `OrgGroupRepository`에 `findAllByOrderByGroupTypeAscSortOrderAscDisplayNameAsc()`, `findAllByMemberOfGroupCode()` 추가
  - `OrgGroupMemberRepository`에 `deleteAllByGroupCode()` (JPQL `@Modifying`) 추가
  - 프론트엔드 `viewOrgManagement`: 조직 구조(회사/본부/팀) 탭 트리뷰 + 직급/직위/직책 플랫 탭
  - `modalOrgGroupEdit`: 유형·코드·표시명·상위조직·정렬순서·활성여부 + 경로 자동 미리보기
  - `group_path` 자동 계산 및 하위 그룹 경로 연쇄 갱신 (`updateDescendantPaths`)
  - 조직 삭제 시 자식 그룹 + OrgGroupMember 연쇄 삭제 (`deleteRecursive`)

- **사용자 관리 UX 개선**
  - 작업 버튼 컬럼 제거 → `역할`/`상태` 컬럼을 인라인 `<select>`로 교체 (변경 즉시 pending 반영)
  - 우클릭 컨텍스트 메뉴(`#adminUserContextMenu`) 추가: 편집 / 삭제 / 복원
  - `#adminUserContextMenu` 화면 경계 보정 로직 포함

### Changed
- **칸반 DnD UX**: 전용 ⋮⋮ 핸들 제거 → 카드 **`article` 전체 드래그**(제목·본문 등), `input`/`select`/`button`/담당 검색 영역에서는 `dragstart` 취소. 드롭 말미 **`rebuildKanbanCardColumnSelectDom`** 로 컬럼 `<select>` 노드 교체 + **`change` 위임**(`ensureKanbanBoardColumnSelectChangeDelegated`)로 Chrome `<select>` 표시 불일치 완화(`frontend/app.js`, `frontend/styles.css`)
- **로그인**: 아이디 저장 줄(`label.login-remember`) 가로 **가운데 정렬**(`display:flex`·`width:fit-content`·`margin:10px auto 0`)
- **퀵 레일 고정**: 상단 📌(사이드바 접기 잠금) 제거 → 채널/DM/퀵 레일 항목 **우클릭** 메뉴 `퀵 레일에 고정`으로 순서 고정(새 메시지로 재정렬되지 않음, `localStorage` 사원별 저장). 채팅방 나가기 시 해당 ID는 고정 목록에서 제거
- **로그인**: 초기 비밀번호 안내 문구 삭제, 아이디 저장 라벨/체크박스 정렬 수정, 저장소에 테마 값이 없을 때·로그인 화면은 **라이트** 기본(인라인 스크립트 + `initTheme`/`showLogin`)
- **테마 팝업**: 옵션 2열 그리드·폭 정리로 빈 칸 제거
- **내 업무 항목**: 비활성(`in_use=false`) 업무는 `GET .../sidebar/by-assigned-cards`에서 제외

### Fixed
- **칸반 DnD(Chrome) 행 컬럼 `<select>` 불일치**: 카드 `article` 전체 `draggable` 대신 제목 줄 **`⋮⋮` 드래그 핸들**(`.kanban-card-drag-handle`)만 `draggable` — 자식 `<select>`가 draggable 조상 안에 있을 때 이동 후 표시가 어긋나는 브라우저 동작 회피(`frontend/app.js`, `frontend/styles.css`)
- **칸반 DnD만 사용 시 행 컬럼 `<select>` 불일치(추가)**: 이동한 `<select>`에 `selectedIndex`로 컬럼 옵션을 강제 지정(`applyKanbanColumnSelectToColumnId`); 호스트 컬럼 id는 **카드·셀렉트 각각 `closest(.kanban-column)`** 폴백; `drop` 후 **`setTimeout(0)` 한 틱** 뒤 `sync*` 재실행으로 dragend/DOM 안정화 이후 재동기화(`frontend/app.js`)
- **칸반 DnD 후 셀렉트 불일치**: 컬럼 `drop` 처리에서 **`loadChannelKanbanBoard`(풀 렌더) 호출 제거** — 드래그로 DOM 위치는 이미 반영되므로 `sync*`·`loadChannelWorkItems`만 수행해 연속 DnD 시 GET 재렌더 레이스를 제거; 렌더 시 행 `<select>` 기본값은 **`data-render-column-id` 우선**(`frontend/app.js`)
- **칸반 연속 DnD**: 컬럼 `drop` 직후 **이중 `requestAnimationFrame` 대기 전**에 fetch 세대를 한 번 더 올려, 직전 드롭의 느린 GET 응답이 다음 드롭 DOM을 덮어쓰지 않게 함; 컬럼 `<select>` `change`에도 동일 선제 bump(`frontend/app.js`)
- **칸반 보드 조회(간헐)**: `loadChannelKanbanBoard` 동시 호출 시 **늦게 도착한 이전 응답**이 `renderKanbanBoard`로 다시 그려져 카드 컬럼과 행 `<select>`가 불일치하던 문제 — 채널별 fetch **세대 번호**로 오래된 응답은 렌더 생략(`frontend/app.js`)
- **칸반 DnD(간헐)**: 셀렉트 값을 **`closest(.kanban-column")`의 `data-column-id`를 최우선**으로 두고 매 렌더·`syncKanbanCardColumnSelectsFromDom`에서 `workHubPendingCardColumn`·연결 업무 상태와 `data-render-column-id`를 동기화; `syncKanbanBoardPartial`에서도 카드마다 `renderColumnId` 갱신; `drop` 직후 **이중 `requestAnimationFrame`** 후 DOM 동기화해 레이아웃 타이밍 레이스 완화
- **칸반 DnD 안정화(추가)**: 렌더 버킷 컬럼(`data-render-column-id`)과 base/pending 컬럼이 다를 때 렌더 컬럼을 기준으로 `workHubPendingCardColumn`·연결 업무 상태를 즉시 보정해, 카드가 `진행 중` 칼럼에 보여도 셀렉트가 `완료`로 남는 케이스를 추가 완화
- **칸반 DnD 텍스트 유입 방지(보강)**: 모달 내부 가드 외에 문서 캡처 단계 글로벌 가드를 추가해, 카드 드래그 중 `input/textarea/select/contenteditable`에 드롭해도 텍스트가 입력되지 않게 차단
- **칸반 DnD**: 카드가 옮겨진 컬럼과 행 **컬럼 `<select>`** 불일치 — 렌더 시 `data-render-column-id`(컬럼 버킷)를 두고 셀렉트 기본값을 그 id로 고정; `syncKanbanCardColumnSelectsFromDom`에서 pending도 `closest(.kanban-column)` 기준으로 갱신
- **칸반 DnD**: 드래그 시작 시 `text/plain: kanban-card` 대신 **`application/x-ech-kanban-card`** + 빈 `text/plain`, 업무 허브에서 **input/textarea/select/contenteditable** 위 드롭 시 `preventDefault`로 검색창 등에 문자열이 들어가지 않게 함
- **칸반 DnD**: 드롭이 카드·셀렉트 바로 아래 좁은 영역에서만 잡히던 문제 — `.kanban-column` 전체에 `dragover`/`drop` 연결, `.kanban-card-list`는 컬럼 내 **세로로 확장**(flex·`min-height`)해 빈 공간에서도 드롭 가능; 컬럼 강조(`.kanban-column-drag-over`)
- **업무·칸반 허브**: 칸반 DnD 후 카드는 옮겨졌는데 행 **컬럼 셀렉트**만 이전 컬럼(예: 완료)에 머물던 문제 — 드롭 시 **전 컬럼 DOM** 기준 `syncKanbanBoardFromDomFull`, 셀렉트를 호스트 컬럼과 맞춤(`syncKanbanCardColumnSelectsFromDom`), 렌더 시 `pending` 없으면 `select`의 `.kanban-column` 부모 id 폴백, `dragend` 정리를 `setTimeout(0)`로 지연해 `drop`과 레이스 완화
- **업무·칸반 허브**: 칸반 카드만 컬럼 이동(DnD/셀렉트)했을 때 연결 **업무 항목**의 pending 상태가 갱신되지 않아, **저장** 시 카드는 완료 컬럼·업무는 진행 중 등으로 엇갈리던 문제 — 이동한 컬럼에 맞춰 `workHubPendingWorkStatus` 동기화, DnD 후 업무 목록도 다시 렌더
- **로그인 아이디 저장**: `.login-form label`·`input { width:100% }`가 체크박스까지 적용되어 클릭 영역이 과대·세로로 깨지던 문제 — `.login-form label.login-remember`(`inline-flex`·`width:fit-content`)·`input[type=checkbox]`로 재정의, 마크업 순서 `아이디 저장` + 체크박스

### Added
- **Windows 로컬 기동**: 루트 `start-ech-dev.bat`(헬스 확인 후 미기동 서버만 새 창에서 실행), `tools/start-ech-backend.bat`, `tools/start-ech-realtime.bat`; `README.md`·`docs/ENVIRONMENT_SETUP.md` 안내
- **로그인**: 아이디 저장(`localStorage`)
- **백엔드 설정**: `auth.initial-password-plaintext` — 비밀번호 미설정 사용자 기동 시 적용 평문(관리자 설정에서 변경, `DataInitializer`가 `initDefaultAppSettings` 이후 읽음)
- **desktop(Electron) 자동 업데이트**: `electron-updater` 의존성, `build.publish`(GitHub), 패키지 실행 시 `checkForUpdatesAndNotify`·6시간 주기 재확인·다운로드 완료 OS 알림(`main.js`)
- **릴리즈 업로드**: `tools/publish-electron-github-release.ps1`가 `latest.yml`에서 설치 파일명을 읽어 **설치 파일 + `latest.yml` + `.blockmap`(있을 때)** 을 동일 태그에 업로드; 기본 태그 `v{package.json version}`
- **문서**: `README.md`(데스크톱 자동 업데이트), `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`에 배포·메타 요구사항 반영

### Changed
- **UI/UX**: 공통 알림·확인 다이얼로그 우측 ✕ 제거; **파랑 테마** 제거(서버 허용 테마 `dark`/`light`만); 전체검색 **한 글자** 시 안내 `검색어는 두글자 이상부터 가능합니다.`; 채널 입장 시 타임라인 **맨 아래**로 스크롤(새 메시지 구분선은 유지); 업무 허브 **복원·완전 삭제**는 **저장** 시 일괄 반영; 업무 목록·칸반 패널 **간격** 보강; **Electron**에서는 멘션/새 메시지 **인앱 토스트 생략**(OS 알림만으로 중복 방지)
- **문서**: `README.md` 테마·초기 비밀번호 설명 갱신

### Changed
- **desktop**: `version` `0.0.4`(업데이터·릴리즈 메타 반영 빌드용)
- **desktop(Electron)**: `package.json`에 `repository` URL 추가, `build:win`을 `electron-builder --win nsis --publish never`로 변경해 빌드 말미 `publish.provider` null 오류로 실패하던 문제 방지
- **GitHub Release**: `v0.0.1` 릴리즈에 Windows NSIS 설치 파일(`ECH Setup 0.0.1.exe`) 업로드

### Fixed
- **Electron 설치형 흰 화면**: `electron-builder`가 프로젝트 밖 `../frontend/*` 경로를 asar에 넣지 않아 `app.asar`에 프론트가 비어 있었음 — `files`에 `from`/`to`로 `frontend/` 전체를 패키지에 포함하고, `main.js`에서 패키지 내 `frontend/index.html` 우선 로드
- **Electron 기본 메뉴바**: Windows/Linux에서 `Menu.setApplicationMenu(null)`로 File/Edit/View 등 기본 메뉴 제거
- **GitHub Release**: 데스크톱 버전 `0.0.2`, 태그 `v0.0.2`에 Windows NSIS 설치 파일 재배포(프론트 asar 포함·메뉴바 제거 반영)

### Fixed
- **Electron 로그인 “서버에 연결할 수 없습니다”**: `file://` origin이 `API_BASE`로 잡혀 `fetch`가 실패하던 문제 — `http(s)` origin일 때만 페이지 origin 사용, 그 외(Electron 설치형 등)는 `localhost:8080` / `localhost:3001` 폴백

### Changed
- **GitHub Release**: 데스크톱 `0.0.3` / 태그 `v0.0.3` — Electron API 베이스(`file://`) 수정이 반영된 Windows NSIS 설치 파일 배포

## 2026-04-02

### Added
- **마지막 읽은 위치 앵커 UI**: 채널 재진입 시 이전에 보던 메시지 위치로 자동 스크롤하고 "마지막 읽은 위치" 구분선을 메시지 목록에 삽입. `chatReadAnchorStorageKey`, `persistChatReadAnchor`, `readChatReadAnchor`, `insertChatReadAnchorDivider`, `restoreChatReadAnchor` 구현. `styles.css`에 `.msg-read-anchor-divider`, `.msg-read-anchor-highlight` 스타일 추가. 앵커 복원 중 scroll 이벤트로 덮어쓰기 방지(`suppressPersistChatReadAnchorOnce` + RAF 이중 보호).
- **백엔드 read-state 기반 "새 메시지" 구분선**: `fetchLastReadMessageId` — 채널 진입 시 `GET /api/channels/{id}/read-state` 조회(메시지 API와 병렬). `showNewMsgsDivider` — `lastReadMessageId` 이후 첫 번째 미읽음 메시지 앞에 "새 메시지" 구분선 삽입 및 스크롤. localStorage 앵커가 없는 첫 방문 DM/채널에서도 동작.
- **데스크톱(Electron) 래퍼**: `desktop/`에 Electron 기본 앱 추가. `frontend/app.js`의 OS 알림은 Electron 메인(`Notification`)으로 포워딩되어, 브라우저 `Notification` 권한 요청 없이 백그라운드에서도 OS 알림을 표시(일반 메시지 mute는 유지, 멘션은 무조건 알림).

### Changed
- **"마지막 읽은 위치" localStorage 앵커 제거**: `chatReadAnchorStorageKey`, `getTopVisibleTimelineMessageId`, `persistChatReadAnchor`, `readChatReadAnchor`, `insertChatReadAnchorDivider`, `restoreChatReadAnchor`, scroll 이벤트 리스너, `chatScrollPersistBound`, `suppressPersistChatReadAnchorOnce` 모두 삭제. 백엔드 read-state 기반 "새 메시지" 구분선만 유지.

- 채팅 **타임라인 페이지네이션**: `GET .../messages/timeline` 응답 `{ items, hasMoreOlder }`, `beforeMessageId` 커서·`findTimelineOlderThan`, 프론트 상단 스크롤 시 이전 페이지 prepend·`#msgHistoryLoading`, DOM `MAX_CHAT_DOM_NODES`/`HARD_MAX` 트림. 대량 테스트 SQL `tools/sql/seed_mass_channel_messages.sql`
- **미읽음·읽음 포인터 보강**: 루트 미읽음 건수를 메시지 `id`만이 아니라 **타임라인 순서(`created_at`, `id`)** 기준으로 계산(`MessageRepository.countRootMessagesNewerThanCursor`). `POST /api/channels/{channelId}/read-state/mark-latest-root`(body: `employeeNo`)로 **채널 최신 루트까지 읽음**(대량 히스토리에서도 첫 진입만으로 배지 해제). 프론트: 채널 전환 시 `localStorage` `ech_chat_scroll_v1_{employeeNo}`에 **스크롤 비율** 저장·복원, `loadMessages` 후 `markChannelReadCaughtUp`, 실시간 수신은 디바운스 `scheduleMarkChannelReadCaughtUp`

### Changed
- 문서: 채팅 DOM·타임라인 설명을 페이지네이션·상수(`MAX_CHAT_DOM_NODES` 등) 기준으로 정리(이전 200/300 문구는 구버전)
- **햄버거 메뉴 액션 버튼**: 이모지·본문을 `btn-member-panel-action-icon` / `label`로 분리하고 아이콘 슬롯 너비 고정으로 줄 정렬 통일
- **첨부 모달** `전체 파일` 탭: 이미지 첨부는 목록에서 제외(이미지는 **이미지** 탭만). 이미지만 있는 경우 전체 탭에 안내 문구 표시

### Fixed
- **칸반 담당자 검색 자동완성**: `ArrowDown` 첫 입력 시 하이라이트가 잠깐 보였다 사라지던 현상 — `input` 디바운스(140ms)가 ⌄ 이후에 `runKanban*AssigneeSuggest`를 실행해 목록을 다시 그리며 `resetKanbanSuggestActive`가 겹친 문제. ⌄/⌃/Enter 시 디바운스 타이머 취소, 비동기 suggest 완료 후에는 `ul._kanbanActiveIdx`를 유지해 동일 길이면 하이라이트 복원
- **일반 신규 메시지 토스트**: Realtime `message:new`가 채널 룸(`channel:join`) 구독자에게만 오는데, 프론트가 **현재 보는 채널 하나만** join 해 다른 채널/DM 메시지를 못 받던 문제 → `loadMyChannels`·소켓 `connect`/`reconnect` 시 참여 채널 전체에 `joinAllChannelSocketRooms`. `message:new`에서 현재 채널 판별은 `Number`로 통일
- `GET /api/channels?employeeNo=...`: 읽음 포인터 없을 때 `countRootMessagesNewerThanCursor`에 `NULL` 바인딩되며 PostgreSQL이 **매개변수 자료형을 알 수 없음**(`$2`)으로 500 나던 문제 → 포인터 없으면 `countRootMessagesInChannel`, 있을 때만 커서 쿼리로 분기
- 메시지 API: `GET .../messages/threads`가 `/{messageId}`에 걸려 `MethodArgumentTypeMismatchException("threads")` 나던 문제를 `\\d+` 경로 제한으로 방지(단건·replies·comments POST 동일)
- Windows/PowerShell에서 한글 커밋 메시지가 깨지던 문제를 `git rebase -i --reword`로 UTF-8 정상 한글 메시지로 재작성
- GitHub Release 생성 API 호출이 403으로 실패해, 권한 재설정 필요성을 `ERRORS.md`에 기록

### Changed
- **좌측 사이드바 레이아웃**: `sidebar-column`을 `align-self:stretch`+`flex` 열로 두고 `sidebar-slip`·`aside.sidebar`에 `flex:1 1 0%`·`min-height:0`로 **컬럼 전체 높이를 채움**(프로필 하단 빈 영역·작은 창에서 프로필 유실 완화). 컬럼 배경 `var(--bg-sidebar)`로 틈 색상 통일
- **업무·칸반 모달**: 두 패널 사이 `gap` 28px로 여백 확대
- **햄버거(채널 메뉴)**: `member-panel-scroll`로 알림~멤버~하단 액션을 **한 스크롤**로 통합(멤버 `ul` 단독 스크롤 제거)
- **업무·칸반 모달**: 좌우 2열 그리드 제거 → **세로 한 열**(업무 항목 위, 칸반 아래), 모달 본문 전체 스크롤, `max-width` 720px
- **스크롤·반응형(설치형/Electron 대비)**: `html`/`body` flex 체인, `#mainApp` `max-height: 100dvh`. 좌측 **사이드바 본문**(`sidebar-main`)에 세로 스크롤 — 채널·DM·관리자까지 한 열에서 스크롤로 도달. **퀵 레일**(`quick-rail-scroll`) 스크롤 보강. **업무·칸반 모달**: `work-hub-panel-body`로 폼·목록·보드 구조 유지
- 우측 **햄버거(멤버) 패널** 메뉴 순서: 알림 끄기/켜기 → 첨부파일 → 이미지 모아보기 → 스레드 모아보기 → 업무/칸반 → 채널(DM) 이름 변경 → **멤버 목록** 구역 제목 → 멤버 리스트 → 구성원 추가 → 채팅방 나가기 → 채널 폐쇄
- 일반 알림을 끈 채널/DM: 사이드바·퀵 레일 항목에 **벨+슬래시** 아이콘 표시(`notifyMutedBellSvg`). 음소거 토글 시 `lastSidebarChannelsSnapshot`으로 목록만 재렌더
- 채팅방 **알림 끄기**: **`pushNewMessageToast`(일반 신규 메시지 토스트)만** 억제. **멘션 토스트**(`pushMentionToast`)는 음소거와 무관하게 항상 표시. **미읽음 배지**(`unreadCount`)는 기존과 동일하게 유지(음소거와 무관). 햄버거 버튼 `title` 문구 보강

### Added
- 채널·DM **스레드 모아보기**: `GET /api/channels/{channelId}/messages/threads`, 햄버거 `💬 스레드 모아보기`·`modalThreadHub`, 최근 스레드 활동 순 원글 목록·클릭 시 기존 스레드 모달
- 채널·DM **이미지 모아보기**: 햄버거 메뉴 `🖼 이미지 모아보기`, `modalFileHub`에 **전체 파일 / 이미지** 탭·썸네일 그리드(클릭 시 라이트박스). 기존 `GET .../files`·`contentType` 활용
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
- Frontend 데모 메시지 DOM 상한(당시 200건·현재 `MAX_MSGS` 300), Backend Hikari 풀 환경변수 연동
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

## 2026-04-03

### Added
- AD 자동 로그인 구현 (Phase 5-1 완료)
  - `desktop/main.js`: IPC 핸들러 `get-windows-username` (os.userInfo 기반 Windows 사용자명 취득)
  - `desktop/preload.js`: `electronAPI.getWindowsUsername()` 렌더러에 노출
  - `api/auth/dto/AdLoginRequest.java`: 사원번호 수신 DTO
  - `AuthService.adLogin()`: 사원번호 DB 존재 + ACTIVE 상태 검증 후 JWT 발급
  - `AuthController`: `POST /api/auth/ad-login` 엔드포인트 추가
  - `SecurityConfig`: `/api/auth/ad-login` permitAll 추가
  - `AuthService.login()`: 기존 일반 로그인에 INACTIVE 계정 차단 로직 추가
  - `frontend/app.js`: `tryAdAutoLogin()` — 앱 초기화 시 Electron 환경에서 AD 자동 로그인 시도
  - 로드맵 Phase 5-1 완료 처리(`[v]`)
- 관리자 사용자 관리 기능 구현 (Phase 5-2 완료)
  - `api/admin/user/AdminUserController.java`: 5개 엔드포인트
    - `GET /api/admin/users` — 전체 사용자 + 조직 정보 조회 (ADMIN)
    - `GET /api/admin/users/org-options` — 부서/직급/직위/직책 드롭다운 옵션 (ADMIN)
    - `POST /api/admin/users` — 사용자 등록 (ADMIN)
    - `PUT /api/admin/users/{employeeNo}` — 사용자 정보/상태/조직 수정 (ADMIN)
    - `DELETE /api/admin/users/{employeeNo}` — 하드 삭제 (ADMIN)
  - `api/admin/user/AdminUserService.java`: 사용자 CRUD + org_group_members 연동 로직
  - `api/admin/user/dto/AdminUserListItemResponse.java`: 조회 응답 DTO
  - `api/admin/user/dto/AdminUserSaveRequest.java`: 등록/수정 요청 DTO
  - `api/admin/user/dto/OrgGroupOptionResponse.java`: 드롭다운 옵션 응답 DTO
  - `domain/user/User.java`: setName/setEmail/setRole/setStatus setter 추가
  - `domain/user/UserRepository.java`: `findAllByOrderByNameAsc()` 추가
  - `domain/org/OrgGroupMemberRepository.java`: `findMembersByEmployeeNos()` 추가
  - `domain/org/OrgGroupRepository.java`: `findByGroupCode()` 추가
  - `frontend/index.html`: 사이드바 관리자 메뉴 "사용자 관리" 항목 + 사용자 관리 뷰 + 편집 모달 추가
  - `frontend/app.js`: `loadAdminUsers()`, `renderAdminUserTable()`, `openAdminUserEditModal()`, `saveAdminUsers()` 등 사용자 관리 함수 블록 추가
  - `frontend/styles.css`: 사용자 관리 테이블·모달·배지 스타일 추가
  - 로드맵 Phase 5-2 완료 처리(`[v]`)

## 2026-04-15

### Changed
- 워크플로우 업무-칸반 상태 연동 보정: 연결된 카드 중 하나만 완료되어도 업무가 `DONE`으로 바뀌던 로직을 수정
- `frontend/app.js`의 `syncPendingWorkItemStatusFromKanbanColumn`에서 업무별 **전체 연결 카드 상태를 집계**해, 모든 카드가 완료 컬럼일 때만 업무를 `DONE`으로 반영하도록 변경
- `docs/FEATURE_SPEC.md`, `docs/HANDOVER.md`에 업무 완료 판정 기준(전체 카드 완료 기준) 반영
- 워크플로우 저장 후 삭제 마킹(`inUse=false`)된 업무 항목이 목록 하단으로 정렬되도록 변경
- 비활성 업무에 연결된 칸반 카드가 컬럼 내 하단으로 정렬되도록 변경(활성 카드 우선 노출)
- 첨부파일 업로드 중 Enter/전송 버튼 재입력 시 동일 첨부가 중복 업로드되던 문제를 수정 (`composerSendInFlight` 재진입 가드 추가)
- 첨부 선택/붙여넣기/드롭 시점에 업로드 최대 용량 정책을 먼저 조회·검사해, 초과 파일은 미리보기 생성 전에 즉시 차단하도록 변경
- `GET /api/channels/{channelId}/files/upload-policy` API를 추가해 프론트에서 현재 `file.max-size-mb` 설정값을 사용하도록 연동
- 배포 버전을 `1.2.6`으로 상향 (`backend/build.gradle`, `desktop/package.json`, `desktop/package-lock.json`)
- 문서/화면의 현재 배포 기준 버전 표기를 `1.2.6` (`v1.2.6`)으로 갱신 (`README.md`, `docs/DEVELOPER_README.md`, `docs/ROADMAP.md`, `docs/HANDOVER.md`, `frontend/index.html`)
