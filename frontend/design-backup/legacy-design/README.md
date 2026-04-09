# Legacy design snapshot (롤백용)

이 폴더에는 **디자인 시스템(Atmospheric / Stitch) 대규모 적용 이전**에 맞춰 둔 다음 파일 복사본이 있습니다.

| 파일 | 설명 |
|------|------|
| `styles.css` | 이전 테마·컴포넌트 스타일 |
| `index.html` | `#echStylesheet` / `ech_design_version` 스위치 **없는** 예전 마크업 |
| `app.js` | 이전 클라이언트 로직 |

## 1) 파일로 완전 롤백 (PowerShell)

저장소 루트에서:

```powershell
Copy-Item -Force "frontend/design-backup/legacy-design/styles.css" "frontend/styles.css"
Copy-Item -Force "frontend/design-backup/legacy-design/index.html" "frontend/index.html"
Copy-Item -Force "frontend/design-backup/legacy-design/app.js" "frontend/app.js"
```

브라우저 **강력 새로고침** 후 UI를 확인하세요.

## 2) CSS만 임시로 (현재 `index.html` 유지)

루트 `frontend/index.html`이 `#echStylesheet`를 쓰는 버전이라면, 개발자 도구 콘솔:

```js
localStorage.setItem('ech_design_version', 'legacy');
location.reload();
```

신규 디자인으로 되돌리기:

```js
localStorage.removeItem('ech_design_version');
location.reload();
```

이때 로드되는 파일은 `./design-backup/legacy-design/styles.css` 입니다.

## 3) 참고

- 상세: `docs/DESIGN_SYSTEM.md`
- Git으로 특정 커밋만 되돌리려면: `git checkout <hash> -- frontend/styles.css frontend/index.html frontend/app.js`
- `frontend/tailwind.config.js`는 토큰 참조용이며, 런타임 스타일은 `styles.css`가 담당합니다.
