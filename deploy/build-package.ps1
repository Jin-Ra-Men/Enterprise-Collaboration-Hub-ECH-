#Requires -Version 5.1
<#
.SYNOPSIS
  ECH 배포 패키지 생성 스크립트 (개발 PC에서 실행)
.DESCRIPTION
  1. Spring Boot JAR 빌드
  2. 배포에 필요한 파일을 하나의 ZIP으로 패키징
  3. 생성된 ZIP을 각 서버에 복사 후 해당 setup 스크립트 실행
.USAGE
  PowerShell에서:  .\deploy\build-package.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 색상 출력 헬퍼 ────────────────────────────────────────────
function Info  { param($msg) Write-Host "  $msg" -ForegroundColor Cyan }
function Ok    { param($msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Warn  { param($msg) Write-Host "  [!!] $msg" -ForegroundColor Yellow }
function Fatal { param($msg) Write-Host "`n  [FAIL] $msg" -ForegroundColor Red; exit 1 }
function Title { param($msg) Write-Host "`n═══ $msg ═══" -ForegroundColor White }

Title "ECH 배포 패키지 생성"

# ── 경로 설정 ────────────────────────────────────────────────
$root    = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$realtime= Join-Path $root "realtime"
$deploy  = Join-Path $root "deploy"
$outDir  = Join-Path $root "deploy\package"
$zipPath = Join-Path $root "deploy\ECH-deploy.zip"

# ── 1. Gradle JAR 빌드 ───────────────────────────────────────
Title "1단계 — Spring Boot JAR 빌드"
$gradlew = Join-Path $backend "gradlew.bat"
if (-not (Test-Path $gradlew)) { Fatal "gradlew.bat 을 찾을 수 없습니다: $gradlew" }

Info "bootJar 빌드 중... (1~3분 소요)"
Push-Location $backend
try {
    & cmd /c "gradlew.bat bootJar 2>&1"
    if ($LASTEXITCODE -ne 0) { Fatal "Gradle 빌드 실패 (exit $LASTEXITCODE)" }
} finally { Pop-Location }

$jarFiles = Get-ChildItem "$backend\build\libs\*.jar" | Where-Object { $_.Name -notmatch "plain" }
if (-not $jarFiles) { Fatal "빌드된 JAR 파일을 찾을 수 없습니다." }
$jarFile = $jarFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Ok "빌드 완료: $($jarFile.Name)"

# ── 2. 패키지 폴더 구성 ──────────────────────────────────────
Title "2단계 — 패키지 폴더 구성"
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory $outDir | Out-Null
New-Item -ItemType Directory "$outDir\backend" | Out-Null
New-Item -ItemType Directory "$outDir\realtime" | Out-Null

# JAR 복사 (이름 고정)
Copy-Item $jarFile.FullName "$outDir\backend\ech-backend.jar"
Ok "JAR 복사: ech-backend.jar"

# realtime 복사 (node_modules 제외)
$realtimeSrc = Get-ChildItem $realtime -Exclude "node_modules"
foreach ($item in $realtimeSrc) {
    if ($item.PSIsContainer) {
        Copy-Item $item.FullName "$outDir\realtime\$($item.Name)" -Recurse
    } else {
        Copy-Item $item.FullName "$outDir\realtime\"
    }
}
Ok "realtime 소스 복사"

# 설정 파일 복사
Copy-Item "$deploy\env.prod"                      "$outDir\"
Copy-Item "$deploy\pm2.ecosystem.config.cjs"      "$outDir\"
Copy-Item "$deploy\setup-db-server.ps1"           "$outDir\"
Copy-Item "$deploy\setup-web-server.ps1"          "$outDir\"
if (Test-Path "$deploy\nginx.conf") {
    Copy-Item "$deploy\nginx.conf" "$outDir\"
}
Ok "설정 파일 복사"

# ── 3. ZIP 압축 ──────────────────────────────────────────────
Title "3단계 — ZIP 패키지 생성"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path "$outDir\*" -DestinationPath $zipPath
$zipSize = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
Ok "생성 완료: deploy\ECH-deploy.zip ($zipSize MB)"

# ── 4. 안내 ──────────────────────────────────────────────────
Title "완료"
Write-Host @"

  생성된 파일: deploy\ECH-deploy.zip

  ┌── 이후 절차 ─────────────────────────────────────────────────┐
  │                                                              │
  │  [DB 서버 (192.168.11.179)] 에서:                           │
  │    1. ECH-deploy.zip 복사 후 압축 해제                       │
  │    2. PowerShell (관리자) 로 실행:                           │
  │       .\setup-db-server.ps1                                 │
  │                                                              │
  │  [WEB 서버 (192.168.11.168)] 에서:                          │
  │    1. ECH-deploy.zip 복사 후 C:\ECH-deploy\ 에 압축 해제    │
  │    2. PowerShell (관리자) 로 실행:                           │
  │       .\setup-web-server.ps1                                │
  │                                                              │
  └──────────────────────────────────────────────────────────────┘

"@ -ForegroundColor Cyan
