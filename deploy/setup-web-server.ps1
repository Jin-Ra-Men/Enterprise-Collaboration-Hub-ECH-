#Requires -Version 5.1
#Requires -RunAsAdministrator
<#
.SYNOPSIS
  ECH WEB 서버 자동 세팅 (192.168.11.168)
.DESCRIPTION
  - Java 17 / Node.js 설치 확인 및 자동 설치 (winget)
  - NSSM 다운로드 및 Spring Boot Windows 서비스 등록
  - PM2 설치 및 리얼타임 서버 Windows 서비스 등록
  - 시스템 환경변수 등록
  - Windows 방화벽 포트 8080 / 3001 개방
.USAGE
  1. ECH-deploy.zip 을 C:\ECH-deploy\ 에 압축 해제
  2. PowerShell (관리자) 에서:  .\setup-web-server.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 색상 출력 헬퍼 ────────────────────────────────────────────
function Info  { param($msg) Write-Host "  $msg" -ForegroundColor Cyan }
function Ok    { param($msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Warn  { param($msg) Write-Host "  [!!] $msg" -ForegroundColor Yellow }
function Fatal { param($msg) Write-Host "`n  [FAIL] $msg`n" -ForegroundColor Red; Read-Host "Enter 키로 종료"; exit 1 }
function Title { param($msg) Write-Host "`n═══ $msg ═══" -ForegroundColor White }
function Ask   { param($prompt, $default="") 
    $val = Read-Host "`n  >> $prompt$(if($default){" [기본: $default]"})"
    if (-not $val -and $default) { return $default }
    return $val
}
function AskPw { param($prompt)
    $ss = Read-Host -AsSecureString "`n  >> $prompt"
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ss))
}

Clear-Host
Write-Host @"

  ╔═══════════════════════════════════════════╗
  ║   ECH — WEB 서버 자동 세팅               ║
  ║   대상: 192.168.11.168 (이 서버)         ║
  ╚═══════════════════════════════════════════╝

"@ -ForegroundColor Cyan

# ════════════════════════════════════════════
# 0. 설정값 입력
# ════════════════════════════════════════════
Title "0단계 — 설정값 입력"

# 스크립트 실행 위치 기준으로 파일 탐색
$scriptDir = $PSScriptRoot

$DB_HOST        = Ask "DB 서버 IP" "192.168.11.179"
$DB_PORT        = Ask "DB 포트" "5432"
$DB_NAME        = Ask "DB 이름" "ech"
$DB_USER        = Ask "DB 사용자" "ech_user"
$DB_PASSWORD    = AskPw "DB 비밀번호"
if (-not $DB_PASSWORD) { Fatal "DB 비밀번호는 필수입니다." }

$JWT_SECRET     = Ask "JWT 시크릿 (32자 이상 무작위 문자열)"
if ($JWT_SECRET.Length -lt 16) { Fatal "JWT_SECRET 은 16자 이상이어야 합니다." }

$REALTIME_TOKEN = Ask "내부 통신 토큰 (Realtime ↔ Backend 공유 비밀값)"
if (-not $REALTIME_TOKEN) { Fatal "내부 통신 토큰은 필수입니다." }

$INSTALL_DIR    = Ask "설치 경로" "C:\ECH"

Write-Host ""
Info "설정 요약:"
Info "  DB           : $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
Info "  설치 경로    : $INSTALL_DIR"
$yn = Ask "위 설정으로 진행하시겠습니까? (Y/n)" "Y"
if ($yn.ToUpper() -ne "Y") { exit 0 }

# ════════════════════════════════════════════
# 1. 폴더 생성
# ════════════════════════════════════════════
Title "1단계 — 디렉터리 생성"

$dirs = @(
    "$INSTALL_DIR\backend",
    "$INSTALL_DIR\realtime",
    "$INSTALL_DIR\logs",
    "$INSTALL_DIR\storage",
    "$INSTALL_DIR\releases",
    "$INSTALL_DIR\tools"
)
foreach ($d in $dirs) {
    New-Item -ItemType Directory -Force $d | Out-Null
    Ok "폴더: $d"
}

# ════════════════════════════════════════════
# 2. Java 17 확인 / 설치
# ════════════════════════════════════════════
Title "2단계 — Java 17 확인"

$javaOk = $false
try {
    $jver = & java -version 2>&1
    if ($jver -match "17\." -or $jver -match "version ""17") { $javaOk = $true }
} catch {}

if ($javaOk) {
    Ok "Java 17 이미 설치됨"
} else {
    Info "Java 17 설치 중 (winget)..."
    try {
        winget install --id EclipseAdoptium.Temurin.17.JRE -e --silent --accept-package-agreements --accept-source-agreements
        Ok "Java 17 설치 완료"
    } catch {
        Write-Host @"

  [!!] winget 으로 Java 자동 설치에 실패했습니다.
  수동 설치:
    https://adoptium.net/ 에서 Temurin JDK 17 Windows x64 설치 후
    이 스크립트를 다시 실행하세요.

"@ -ForegroundColor Yellow
        Fatal "Java 17 설치 필요"
    }
    # PATH 새로고침
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}

# ════════════════════════════════════════════
# 3. Node.js 확인 / 설치
# ════════════════════════════════════════════
Title "3단계 — Node.js 확인"

$nodeOk = $false
try {
    $nver = & node -v 2>&1
    if ($nver -match "v\d+\." -and [int]($nver -replace "v(\d+)\..*",'$1') -ge 18) { $nodeOk = $true }
} catch {}

if ($nodeOk) {
    Ok "Node.js 이미 설치됨 ($nver)"
} else {
    Info "Node.js LTS 설치 중 (winget)..."
    try {
        winget install --id OpenJS.NodeJS.LTS -e --silent --accept-package-agreements --accept-source-agreements
        Ok "Node.js 설치 완료"
    } catch {
        Write-Host @"

  [!!] winget 으로 Node.js 자동 설치에 실패했습니다.
  수동 설치:
    https://nodejs.org/ 에서 LTS 버전 설치 후 재실행하세요.

"@ -ForegroundColor Yellow
        Fatal "Node.js 설치 필요"
    }
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}

# ════════════════════════════════════════════
# 4. 파일 배포
# ════════════════════════════════════════════
Title "4단계 — 파일 배포"

# backend JAR
$jarSrc = Join-Path $scriptDir "backend\ech-backend.jar"
if (-not (Test-Path $jarSrc)) { Fatal "JAR 파일이 없습니다: $jarSrc`nbuild-package.ps1 을 먼저 실행하고 ECH-deploy.zip 을 복사하세요." }
Copy-Item $jarSrc "$INSTALL_DIR\backend\ech-backend.jar" -Force
Ok "백엔드 JAR 복사"

# realtime
$realtimeSrc = Join-Path $scriptDir "realtime"
if (-not (Test-Path $realtimeSrc)) { Fatal "realtime 폴더가 없습니다: $realtimeSrc" }
Copy-Item "$realtimeSrc\*" "$INSTALL_DIR\realtime\" -Recurse -Force
Ok "리얼타임 소스 복사"

# PM2 설정 파일 복사
$pm2Src = Join-Path $scriptDir "pm2.ecosystem.config.cjs"
if (Test-Path $pm2Src) {
    Copy-Item $pm2Src "$INSTALL_DIR\realtime\pm2.ecosystem.config.cjs" -Force
    Ok "PM2 설정 파일 복사"
}

# realtime npm install
Info "리얼타임 의존성 설치 중 (npm install)..."
Push-Location "$INSTALL_DIR\realtime"
try {
    & npm install --omit=dev 2>&1 | Out-Null
    Ok "npm install 완료"
} finally { Pop-Location }

# ════════════════════════════════════════════
# 5. 시스템 환경변수 등록
# ════════════════════════════════════════════
Title "5단계 — 시스템 환경변수 등록"

$vars = [ordered]@{
    "DB_HOST"                    = $DB_HOST
    "DB_PORT"                    = $DB_PORT
    "DB_NAME"                    = $DB_NAME
    "DB_USER"                    = $DB_USER
    "DB_PASSWORD"                = $DB_PASSWORD
    "SPRING_PORT"                = "8080"
    "EXPOSE_ERROR_DETAIL"        = "false"
    "JWT_SECRET"                 = $JWT_SECRET
    "JWT_EXPIRATION_MS"          = "28800000"
    "REALTIME_INTERNAL_TOKEN"    = $REALTIME_TOKEN
    "REALTIME_INTERNAL_BASE_URL" = "http://localhost:3001"
    "FILE_STORAGE_DIR"           = "$INSTALL_DIR\storage".Replace('\','/')
    "APP_RELEASES_DIR"           = "$INSTALL_DIR\releases".Replace('\','/')
    "MAX_UPLOAD_SIZE"            = "500MB"
    "MAX_REQUEST_SIZE"           = "500MB"
    "DB_POOL_MAX"                = "10"
    "DB_POOL_MIN_IDLE"           = "2"
    "DB_POOL_CONNECT_TIMEOUT_MS" = "30000"
}
foreach ($k in $vars.Keys) {
    [System.Environment]::SetEnvironmentVariable($k, $vars[$k], "Machine")
    Ok "SET $k"
}
# 현재 프로세스에도 즉시 적용
foreach ($k in $vars.Keys) { Set-Item "env:$k" $vars[$k] }

# ════════════════════════════════════════════
# 6. NSSM 다운로드 및 백엔드 서비스 등록
# ════════════════════════════════════════════
Title "6단계 — Spring Boot Windows 서비스 등록 (NSSM)"

$nssmPath = "$INSTALL_DIR\tools\nssm.exe"
if (-not (Test-Path $nssmPath)) {
    Info "NSSM 다운로드 중..."
    try {
        $nssmUrl = "https://nssm.cc/release/nssm-2.24.zip"
        $nssmZip = "$env:TEMP\nssm.zip"
        Invoke-WebRequest -Uri $nssmUrl -OutFile $nssmZip -UseBasicParsing
        Expand-Archive -Path $nssmZip -DestinationPath "$env:TEMP\nssm-extract" -Force
        $nssmExe = Get-ChildItem "$env:TEMP\nssm-extract" -Recurse -Filter "nssm.exe" |
                   Where-Object { $_.FullName -match "win64" } |
                   Select-Object -First 1
        if (-not $nssmExe) {
            $nssmExe = Get-ChildItem "$env:TEMP\nssm-extract" -Recurse -Filter "nssm.exe" | Select-Object -First 1
        }
        Copy-Item $nssmExe.FullName $nssmPath -Force
        Ok "NSSM 다운로드 완료"
    } catch {
        Warn "NSSM 자동 다운로드 실패. 수동으로 https://nssm.cc/download 에서 nssm.exe 를 $nssmPath 에 복사 후 재실행하세요."
        Fatal "NSSM 다운로드 실패"
    }
} else {
    Ok "NSSM 이미 존재: $nssmPath"
}

# 기존 서비스 제거
$svcName = "ECH-Backend"
$existing = Get-Service $svcName -ErrorAction SilentlyContinue
if ($existing) {
    Stop-Service $svcName -Force -ErrorAction SilentlyContinue
    & "$nssmPath" remove $svcName confirm | Out-Null
    Warn "기존 서비스 '$svcName' 제거"
}

# 자바 경로 탐색
$javaExe = (Get-Command java -ErrorAction SilentlyContinue)?.Source
if (-not $javaExe) { Fatal "java.exe 를 찾을 수 없습니다. Java 17 설치를 확인하세요." }

& "$nssmPath" install $svcName "$javaExe" | Out-Null
& "$nssmPath" set $svcName AppParameters "-Xms256m -Xmx1g -jar `"$INSTALL_DIR\backend\ech-backend.jar`"" | Out-Null
& "$nssmPath" set $svcName AppDirectory "$INSTALL_DIR\backend" | Out-Null
& "$nssmPath" set $svcName AppStdout "$INSTALL_DIR\logs\backend-out.log" | Out-Null
& "$nssmPath" set $svcName AppStderr "$INSTALL_DIR\logs\backend-error.log" | Out-Null
& "$nssmPath" set $svcName AppRotateFiles 1 | Out-Null
& "$nssmPath" set $svcName AppRotateBytes 10485760 | Out-Null
& "$nssmPath" set $svcName Start SERVICE_AUTO_START | Out-Null
& "$nssmPath" set $svcName DisplayName "ECH Backend (Spring Boot)" | Out-Null

# 서비스에 환경변수 전달
foreach ($k in $vars.Keys) {
    & "$nssmPath" set $svcName AppEnvironmentExtra "+$k=$($vars[$k])" | Out-Null
}

Start-Service $svcName
Start-Sleep -Seconds 5
$st = (Get-Service $svcName).Status
if ($st -eq "Running") { Ok "ECH-Backend 서비스 시작 완료" }
else { Warn "서비스 상태: $st — 잠시 후 확인하세요 (Spring Boot 초기 기동에 10~20초 소요)" }

# ════════════════════════════════════════════
# 7. PM2 설치 및 리얼타임 서비스 등록
# ════════════════════════════════════════════
Title "7단계 — PM2 리얼타임 서비스 등록"

# PM2 설치 확인
$pm2Ok = $false
try { & pm2 -v 2>&1 | Out-Null; $pm2Ok = $true } catch {}
if (-not $pm2Ok) {
    Info "PM2 전역 설치 중..."
    & npm install -g pm2 2>&1 | Out-Null
    & npm install -g pm2-windows-service 2>&1 | Out-Null
    Ok "PM2 설치 완료"
} else { Ok "PM2 이미 설치됨" }

# PM2 ecosystem 파일 환경변수 업데이트 (실제 값으로 치환)
$pm2Config = "$INSTALL_DIR\realtime\pm2.ecosystem.config.cjs"
if (Test-Path $pm2Config) {
    $cfg = Get-Content $pm2Config -Raw
    $cfg = $cfg -replace '"CHANGE_ME_STRONG_PASSWORD"', "`"$DB_PASSWORD`""
    $cfg = $cfg -replace '"CHANGE_ME_INTERNAL_SECRET"', "`"$REALTIME_TOKEN`""
    $cfg = $cfg -replace '"192\.168\.11\.179"', "`"$DB_HOST`""
    $cfg = $cfg -replace 'cwd: "C:/ECH/realtime"', "cwd: `"$($INSTALL_DIR.Replace('\','/') + '/realtime')`""
    $cfg = $cfg -replace 'C:/ECH/logs', $INSTALL_DIR.Replace('\','/') + '/logs'
    Set-Content $pm2Config $cfg -Encoding UTF8
    Ok "PM2 설정 파일 업데이트"
}

# 기존 PM2 프로세스 정리
Push-Location "$INSTALL_DIR\realtime"
try {
    & pm2 delete ech-realtime 2>&1 | Out-Null
} catch {}
& pm2 start pm2.ecosystem.config.cjs 2>&1 | Out-Null
& pm2 save 2>&1 | Out-Null
Ok "PM2 ech-realtime 시작 및 저장"
Pop-Location

# Windows 서비스로 등록
$pm2SvcExists = Get-Service "PM2" -ErrorAction SilentlyContinue
if (-not $pm2SvcExists) {
    try {
        & pm2-service-install -n "PM2" 2>&1 | Out-Null
        Start-Service "PM2" -ErrorAction SilentlyContinue
        Ok "PM2 Windows 서비스 등록 완료"
    } catch {
        Warn "PM2 Windows 서비스 자동 등록 실패. 수동으로: pm2-service-install"
    }
} else {
    Restart-Service "PM2" -ErrorAction SilentlyContinue
    Ok "PM2 서비스 재시작"
}

# ════════════════════════════════════════════
# 8. 방화벽 설정
# ════════════════════════════════════════════
Title "8단계 — 방화벽 포트 개방"

@(
    @{Name="ECH-Backend-8080";  Port=8080; Desc="ECH Backend (Spring Boot)"},
    @{Name="ECH-Realtime-3001"; Port=3001; Desc="ECH Realtime (Socket.IO)"}
) | ForEach-Object {
    $r = $_
    $ex = Get-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    if ($ex) { Remove-NetFirewallRule -DisplayName $r.Name }
    New-NetFirewallRule -DisplayName $r.Name -Direction Inbound `
        -Protocol TCP -LocalPort $r.Port -Action Allow | Out-Null
    Ok "포트 $($r.Port) 개방: $($r.Desc)"
}

# ════════════════════════════════════════════
# 9. 기동 확인
# ════════════════════════════════════════════
Title "9단계 — 기동 확인 (Spring Boot 기동 대기 최대 60초)"

$backendOk = $false
for ($i = 1; $i -le 12; $i++) {
    Start-Sleep -Seconds 5
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $backendOk = $true; break }
    } catch {}
    Info "백엔드 기동 대기 중... ($($i*5)초)"
}

if ($backendOk) { Ok "백엔드 응답 확인: http://localhost:8080/api/health" }
else { Warn "백엔드 응답 없음. 로그 확인: $INSTALL_DIR\logs\backend-error.log" }

try {
    $r = Invoke-WebRequest -Uri "http://localhost:3001/health" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -eq 200) { Ok "리얼타임 응답 확인: http://localhost:3001/health" }
} catch { Warn "리얼타임 응답 없음. pm2 list 로 상태 확인하세요." }

# ── 완료 ─────────────────────────────────────────────────────
Write-Host @"

  ╔══════════════════════════════════════════════════════════════╗
  ║  WEB 서버 세팅 완료!                                         ║
  ╠══════════════════════════════════════════════════════════════╣
  ║  백엔드   : http://localhost:8080/api/health                  ║
  ║  리얼타임 : http://localhost:3001/health                      ║
  ║  로그     : $($INSTALL_DIR)\logs\                             ║
  ╠══════════════════════════════════════════════════════════════╣
  ║  클라이언트 PC hosts 파일에 추가:                              ║
  ║  192.168.11.168    ech.co.kr                                 ║
  ║                                                              ║
  ║  브라우저 접속: http://ech.co.kr:8080                         ║
  ╚══════════════════════════════════════════════════════════════╝

  서비스 관리 명령:
    Start-Service ECH-Backend       # 백엔드 시작
    Stop-Service ECH-Backend        # 백엔드 중지
    Restart-Service ECH-Backend     # 백엔드 재시작
    pm2 list                        # 리얼타임 상태
    pm2 restart ech-realtime        # 리얼타임 재시작

"@ -ForegroundColor Green

Read-Host "  Enter 키로 종료"
