#Requires -Version 5.1
#Requires -RunAsAdministrator
<#
.SYNOPSIS
  ECH DB 서버 자동 세팅 (192.168.11.179)
.DESCRIPTION
  - PostgreSQL 설치 확인 / 안내
  - DB·사용자 생성
  - pg_hba.conf / listen_addresses 설정
  - Windows 방화벽 포트 5432 허용 (WEB 서버 IP 전용)
.USAGE
  PowerShell (관리자) 에서:  .\setup-db-server.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 색상 출력 헬퍼 ────────────────────────────────────────────
function Info  { param($msg) Write-Host "  $msg" -ForegroundColor Cyan }
function Ok    { param($msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Warn  { param($msg) Write-Host "  [!!] $msg" -ForegroundColor Yellow }
function Fatal { param($msg) Write-Host "`n  [FAIL] $msg`n" -ForegroundColor Red; Read-Host "Enter 키로 종료"; exit 1 }
function Title { param($msg) Write-Host "`n═══ $msg ═══" -ForegroundColor White }
function Ask   { param($prompt) Read-Host "`n  >> $prompt" }
function AskPw { param($prompt)
    $ss = Read-Host -AsSecureString "`n  >> $prompt"
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ss))
}

Clear-Host
Write-Host @"

  ╔═══════════════════════════════════════════╗
  ║   ECH — DB 서버 자동 세팅                 ║
  ║   대상: 192.168.11.179 (이 서버)          ║
  ╚═══════════════════════════════════════════╝

"@ -ForegroundColor Cyan

# ════════════════════════════════════════════
# 0. 설정값 입력
# ════════════════════════════════════════════
Title "0단계 — 설정값 입력"

$WEB_SERVER_IP  = Ask "WEB 서버 IP를 입력하세요 [기본: 192.168.11.168]"
if (-not $WEB_SERVER_IP) { $WEB_SERVER_IP = "192.168.11.168" }

$DB_NAME        = Ask "DB 이름 [기본: ech]"
if (-not $DB_NAME) { $DB_NAME = "ech" }

$DB_USER        = Ask "DB 사용자 이름 [기본: ech_user]"
if (-not $DB_USER) { $DB_USER = "ech_user" }

$DB_PASSWORD    = AskPw "DB 사용자 비밀번호 (새로 생성할 비밀번호 입력)"
if (-not $DB_PASSWORD) { Fatal "비밀번호는 비워둘 수 없습니다." }

$PG_SUPERPASS   = AskPw "PostgreSQL postgres 슈퍼유저 비밀번호 입력"

Write-Host ""
Info "설정값:"
Info "  WEB 서버 IP : $WEB_SERVER_IP"
Info "  DB 이름     : $DB_NAME"
Info "  DB 사용자   : $DB_USER"
$yn = Ask "위 설정으로 진행하시겠습니까? (Y/n)"
if ($yn -and $yn.ToUpper() -ne "Y") { exit 0 }

# ════════════════════════════════════════════
# 1. PostgreSQL 확인
# ════════════════════════════════════════════
Title "1단계 — PostgreSQL 설치 확인"

# PostgreSQL bin 경로 자동 탐색
$pgBin = $null
$pgData= $null
$pgService = $null

# 레지스트리에서 설치 경로 탐색
$pgRegPaths = @(
    "HKLM:\SOFTWARE\PostgreSQL\Installations",
    "HKLM:\SOFTWARE\PostgreSQL Global Development Group\PostgreSQL"
)
foreach ($reg in $pgRegPaths) {
    if (Test-Path $reg) {
        Get-ChildItem $reg -ErrorAction SilentlyContinue | ForEach-Object {
            $props = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
            if (-not $props) { return }
            $base = $null
            # 속성 존재 여부를 안전하게 확인
            if ($props.PSObject.Properties["Base_Directory"]) {
                $base = $props.Base_Directory
            } elseif ($props.PSObject.Properties["InstallationDirectory"]) {
                $base = $props.InstallationDirectory
            }
            if ($base -and (Test-Path "$base\bin\psql.exe")) {
                $pgBin  = "$base\bin"
                if ($props.PSObject.Properties["Data_Directory"]) {
                    $pgData = $props.Data_Directory
                }
            }
        }
    }
}

# 경로 직접 탐색 (fallback)
if (-not $pgBin) {
    $candidates = @(
        "C:\Program Files\PostgreSQL\17\bin",
        "C:\Program Files\PostgreSQL\16\bin",
        "C:\Program Files\PostgreSQL\15\bin",
        "C:\Program Files\PostgreSQL\14\bin"
    )
    foreach ($c in $candidates) {
        if (Test-Path "$c\psql.exe") { $pgBin = $c; break }
    }
}

if (-not $pgBin) {
    Write-Host @"

  [!!] PostgreSQL이 설치되지 않았습니다.

  설치 방법:
    1. https://www.postgresql.org/download/windows/ 접속
    2. PostgreSQL 16 또는 17 Windows x86-64 다운로드
    3. 설치 시 슈퍼유저(postgres) 비밀번호 설정
    4. 설치 완료 후 이 스크립트를 다시 실행하세요.

"@ -ForegroundColor Yellow
    Fatal "PostgreSQL 설치 후 재실행해 주세요."
}
Ok "PostgreSQL bin: $pgBin"

# Data 디렉터리 탐색 (fallback)
if (-not $pgData) {
    $pgParent = Split-Path $pgBin -Parent
    $pgData   = "$pgParent\data"
}
if (-not (Test-Path $pgData)) {
    # 서비스에서 data 경로 추출 시도
    $svc = Get-WmiObject Win32_Service | Where-Object { $_.Name -match "postgresql" } | Select-Object -First 1
    if ($svc) {
        $pgService = $svc.Name
        $match = [regex]::Match($svc.PathName, '-D\s+"?([^"]+)"?')
        if ($match.Success) { $pgData = $match.Groups[1].Value.TrimEnd('\') }
    }
}
Ok "PostgreSQL data: $pgData"

# 서비스 이름 탐색
if (-not $pgService) {
    $svc = Get-Service | Where-Object { $_.Name -match "^postgresql" } | Select-Object -First 1
    if ($svc) { $pgService = $svc.Name }
}
if ($pgService) { Ok "PostgreSQL 서비스: $pgService" }

$env:PGPASSWORD = $PG_SUPERPASS
$psql = "$pgBin\psql.exe"

# 연결 테스트
Info "postgres 계정 연결 테스트..."
$testResult = & "$psql" -U postgres -c "SELECT 1" -t 2>&1
if ($LASTEXITCODE -ne 0) {
    Fatal "postgres 연결 실패. 비밀번호를 확인하세요.`n$testResult"
}
Ok "PostgreSQL 연결 성공"

# ════════════════════════════════════════════
# 2. DB 및 사용자 생성
# ════════════════════════════════════════════
Title "2단계 — DB 및 사용자 생성"

# 사용자 존재 여부 확인
$userExists = & "$psql" -U postgres -t -c "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" 2>&1
if ($userExists -match "1") {
    Warn "사용자 '$DB_USER' 이미 존재 — 비밀번호만 업데이트합니다."
    & "$psql" -U postgres -c "ALTER USER $DB_USER WITH PASSWORD '$DB_PASSWORD';" | Out-Null
} else {
    & "$psql" -U postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';" | Out-Null
    Ok "사용자 '$DB_USER' 생성"
}

# DB 존재 여부 확인
$dbExists = & "$psql" -U postgres -t -c "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" 2>&1
if ($dbExists -match "1") {
    Warn "데이터베이스 '$DB_NAME' 이미 존재 — 건너뜁니다."
} else {
    & "$psql" -U postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER ENCODING 'UTF8';" | Out-Null
    Ok "데이터베이스 '$DB_NAME' 생성"
}

& "$psql" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;" | Out-Null
Ok "권한 부여 완료"

# ════════════════════════════════════════════
# 3. postgresql.conf — listen_addresses 설정
# ════════════════════════════════════════════
Title "3단계 — postgresql.conf 설정"

$pgConf = "$pgData\postgresql.conf"
if (-not (Test-Path $pgConf)) { Fatal "postgresql.conf 를 찾을 수 없습니다: $pgConf" }

$content = Get-Content $pgConf -Raw
if ($content -match "^listen_addresses\s*=\s*'\*'") {
    Warn "listen_addresses = '*' 이미 설정됨"
} else {
    # 기존 listen_addresses 주석 처리 후 새 값 추가
    $content = $content -replace "(?m)^(listen_addresses\s*=.*)", "#`$1"
    if ($content -notmatch "listen_addresses = '\*'") {
        $content += "`nlisten_addresses = '*'   # ECH setup`n"
    }
    Set-Content $pgConf $content -Encoding UTF8
    Ok "listen_addresses = '*' 설정"
}

# ════════════════════════════════════════════
# 4. pg_hba.conf — WEB 서버 접속 허용
# ════════════════════════════════════════════
Title "4단계 — pg_hba.conf 설정"

$pgHba = "$pgData\pg_hba.conf"
if (-not (Test-Path $pgHba)) { Fatal "pg_hba.conf 를 찾을 수 없습니다: $pgHba" }

$hbaContent = Get-Content $pgHba -Raw
$newRule    = "host    $DB_NAME    $DB_USER    $WEB_SERVER_IP/32    scram-sha-256"

if ($hbaContent -match [regex]::Escape($newRule)) {
    Warn "pg_hba.conf 규칙 이미 존재"
} else {
    Add-Content $pgHba "`n# ECH WEB 서버 접속 허용 (setup-db-server.ps1)`n$newRule"
    Ok "pg_hba.conf 규칙 추가: $WEB_SERVER_IP → $DB_NAME"
}

# ════════════════════════════════════════════
# 5. PostgreSQL 서비스 재시작
# ════════════════════════════════════════════
Title "5단계 — PostgreSQL 서비스 재시작"

if ($pgService) {
    Restart-Service $pgService -Force
    Start-Sleep -Seconds 3
    $svcStatus = (Get-Service $pgService).Status
    if ($svcStatus -eq "Running") { Ok "PostgreSQL 재시작 완료 (Running)" }
    else { Fatal "PostgreSQL 재시작 실패. 서비스 상태: $svcStatus" }
} else {
    Warn "PostgreSQL 서비스를 찾을 수 없습니다. 수동으로 재시작해 주세요."
}

# ════════════════════════════════════════════
# 6. Windows 방화벽 — 포트 5432
# ════════════════════════════════════════════
Title "6단계 — 방화벽 설정 (포트 5432)"

$ruleName = "ECH-PostgreSQL-5432"
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Warn "방화벽 규칙 '$ruleName' 이미 존재 — 업데이트합니다."
    Remove-NetFirewallRule -DisplayName $ruleName
}
New-NetFirewallRule -DisplayName $ruleName `
    -Direction Inbound -Protocol TCP -LocalPort 5432 `
    -RemoteAddress $WEB_SERVER_IP -Action Allow | Out-Null
Ok "방화벽 규칙 추가: 5432 ← $WEB_SERVER_IP 만 허용"

# ════════════════════════════════════════════
# 7. 연결 최종 확인
# ════════════════════════════════════════════
Title "7단계 — 최종 확인"

$env:PGPASSWORD = $DB_PASSWORD
$verify = & "$psql" -U $DB_USER -d $DB_NAME -h localhost -c "SELECT current_database(), current_user;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Ok "DB 접속 확인 완료 (ech_user → $DB_NAME)"
} else {
    Warn "로컬 접속 테스트 실패 (원격에서는 정상일 수 있음)`n$verify"
}

# ── 완료 ─────────────────────────────────────────────────────
Write-Host @"

  ╔═══════════════════════════════════════════════╗
  ║  DB 서버 세팅 완료!                           ║
  ╠═══════════════════════════════════════════════╣
  ║  DB 이름    : $($DB_NAME.PadRight(32))  ║
  ║  DB 사용자  : $($DB_USER.PadRight(32))  ║
  ║  허용 IP    : $($WEB_SERVER_IP.PadRight(32))  ║
  ╚═══════════════════════════════════════════════╝

  다음: WEB 서버 (192.168.11.168) 에서 setup-web-server.ps1 실행

"@ -ForegroundColor Green

Read-Host "  Enter 키로 종료"
