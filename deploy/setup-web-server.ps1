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
Info "─── 파일 저장소 설정 ───"
Info "  첨부파일을 이 서버(로컬)에 저장하려면: C:\ECH\storage (기본)"
Info "  DB 서버(네트워크)에 저장하려면 : \\\\192.168.11.179\ECHStorage"
$FILE_STORAGE_INPUT = Ask "파일 저장 경로 (로컬 또는 UNC)" "$INSTALL_DIR\storage"

# UNC 경로 여부 판별
$isUncStorage = $FILE_STORAGE_INPUT.StartsWith("\\")

$SVC_ACCOUNT_USER = ""
$SVC_ACCOUNT_PASS = ""
if ($isUncStorage) {
    Write-Host @"

  [안내] 네트워크(UNC) 경로 사용 시 Windows 서비스가 네트워크에 접근할 수 있도록
  이 서버와 DB 서버 양쪽에 동일한 로컬 계정(사용자명/비밀번호)이 필요합니다.

  사전 작업:
    1. DB 서버(192.168.11.179)에서 폴더 공유:
       - C:\ECHStorage 폴더 생성
       - 마우스 우클릭 → 속성 → 공유 → 고급 공유
       - 공유 이름: ECHStorage, 권한: Everyone 또는 아래 계정에 전체 권한
    2. 이 서버(WEB)와 DB 서버 양쪽에 동일한 로컬 계정 생성:
       net user echsvc <비밀번호> /add
       net localgroup Administrators echsvc /add  (또는 최소 권한)

"@ -ForegroundColor Yellow
    $SVC_ACCOUNT_USER = Ask "서비스 실행 계정 (도메인\사용자 또는 .\로컬계정)" ".\echsvc"
    $SVC_ACCOUNT_PASS = AskPw "서비스 계정 비밀번호"
    if (-not $SVC_ACCOUNT_PASS) { Fatal "서비스 계정 비밀번호는 필수입니다." }
}

Write-Host ""
Info "설정 요약:"
Info "  DB           : ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
Info "  설치 경로    : $INSTALL_DIR"
Info "  파일 저장소  : $FILE_STORAGE_INPUT"
if ($isUncStorage) { Info "  서비스 계정  : $SVC_ACCOUNT_USER" }
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

# PATH 새로고침 (수동 설치 직후 세션에 반영되지 않을 수 있음)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# JAVA_HOME 환경변수 또는 Program Files 하위 JDK 경로도 탐색
function Find-JavaExeOnDisk {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft",
        "C:\Program Files\BellSoft"
    )
    foreach ($base in $candidates) {
        if (-not $base -or -not (Test-Path $base)) { continue }
        $found = Get-ChildItem -Path $base -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue |
                 Where-Object { $_.FullName -notmatch "jre.*bin\\java\.exe" -or $_.FullName -match "jdk" } |
                 Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

$javaOk = $false
try {
    $jver = & java -version 2>&1
    if ($jver -match "17\." -or $jver -match 'version "17') { $javaOk = $true }
} catch {}

if ($javaOk) {
    Ok "Java 17 이미 설치됨 (PATH)"
} else {
    Info "Java 17 설치 중 (winget)..."
    $wingetOk = $false
    try {
        winget install --id EclipseAdoptium.Temurin.17.JDK -e --silent --accept-package-agreements --accept-source-agreements 2>&1 | Out-Null
        $wingetOk = $true
        Ok "Java 17 설치 완료 (winget)"
    } catch {}

    if (-not $wingetOk) {
        Write-Host @"

  [!!] winget 으로 Java 자동 설치에 실패했습니다.
  수동 설치:
    https://adoptium.net/ 에서 Temurin JDK 17 Windows x64 설치 후
    이 스크립트를 다시 실행하세요.

"@ -ForegroundColor Yellow
    }

    # PATH 새로고침 후 재확인
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

    $javaOk = $false
    try {
        $jver = & java -version 2>&1
        if ($jver -match "17\." -or $jver -match 'version "17') { $javaOk = $true }
    } catch {}

    if (-not $javaOk) {
        # Program Files 직접 탐색 (수동 설치 경로)
        $diskJava = Find-JavaExeOnDisk
        if ($diskJava) {
            $javaDir = Split-Path $diskJava
            $env:Path = "$javaDir;$env:Path"
            [System.Environment]::SetEnvironmentVariable("Path", "$javaDir;" + [System.Environment]::GetEnvironmentVariable("Path","Machine"), "Machine")
            Ok "Java 발견 (디스크 탐색): $diskJava"
            $javaOk = $true
        }
    }

    if (-not $javaOk) {
        Fatal "Java 17 을 찾을 수 없습니다. 설치 후 PowerShell 창을 닫고 다시 관리자 권한으로 실행하세요."
    }
}

# ════════════════════════════════════════════
# 3. Node.js 확인 / 설치
# ════════════════════════════════════════════
Title "3단계 — Node.js 확인"

# PATH 새로고침 (수동 설치 직후 세션에 반영되지 않을 수 있음)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

function Find-NodeExeOnDisk {
    $candidates = @(
        $env:NODE_HOME,
        "C:\Program Files\nodejs",
        "C:\Program Files (x86)\nodejs"
    )
    foreach ($base in $candidates) {
        if (-not $base -or -not (Test-Path $base)) { continue }
        $exe = Join-Path $base "node.exe"
        if (Test-Path $exe) { return $exe }
    }
    # AppData\Roaming\nvm 또는 기타 경로 추가 탐색
    $found = Get-ChildItem -Path "C:\Program Files" -Recurse -Filter "node.exe" -ErrorAction SilentlyContinue |
             Select-Object -First 1
    if ($found) { return $found.FullName }
    return $null
}

function Test-NodeVersion {
    try {
        $nver = & node -v 2>&1
        if ($nver -match "v(\d+)\." -and [int]$Matches[1] -ge 18) { return $true }
    } catch {}
    return $false
}

$nodeOk = Test-NodeVersion

if ($nodeOk) {
    $nver = & node -v 2>&1
    Ok "Node.js 이미 설치됨 ($nver)"
} else {
    Info "Node.js LTS 설치 중 (winget)..."
    $wingetNodeOk = $false
    try {
        winget install --id OpenJS.NodeJS.LTS -e --silent --accept-package-agreements --accept-source-agreements 2>&1 | Out-Null
        $wingetNodeOk = $true
        Ok "Node.js 설치 완료 (winget)"
    } catch {}

    if (-not $wingetNodeOk) {
        Write-Host @"

  [!!] winget 으로 Node.js 자동 설치에 실패했습니다.
  수동 설치 방법:
    인터넷 되는 PC 에서 아래 URL 다운로드 후 이 서버에 복사/설치하세요.
    https://nodejs.org/dist/v20.19.0/node-v20.19.0-x64.msi  (Node.js v20 LTS)
    설치 후 PowerShell 창을 닫고 관리자 권한으로 다시 실행하세요.

"@ -ForegroundColor Yellow
    }

    # PATH 새로고침 후 재확인
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    $nodeOk = Test-NodeVersion

    if (-not $nodeOk) {
        # Program Files 직접 탐색 (수동 설치 경로)
        $diskNode = Find-NodeExeOnDisk
        if ($diskNode) {
            $nodeDir = Split-Path $diskNode
            $env:Path = "$nodeDir;$env:Path"
            [System.Environment]::SetEnvironmentVariable("Path", "$nodeDir;" + [System.Environment]::GetEnvironmentVariable("Path","Machine"), "Machine")
            Ok "Node.js 발견 (디스크 탐색): $diskNode"
            $nodeOk = $true
        }
    }

    if (-not $nodeOk) {
        Fatal "Node.js v18 이상을 찾을 수 없습니다. 설치 후 PowerShell 창을 닫고 다시 관리자 권한으로 실행하세요."
    }
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

# realtime (node_modules 포함 전체 복사)
$realtimeSrc = Join-Path $scriptDir "realtime"
if (-not (Test-Path $realtimeSrc)) { Fatal "realtime 폴더가 없습니다: $realtimeSrc" }
Copy-Item "$realtimeSrc\*" "$INSTALL_DIR\realtime\" -Recurse -Force
Ok "리얼타임 소스 복사"

# node_modules 존재 확인 — 없으면 npm install 시도 (인터넷 필요)
$npmLogDir = "$INSTALL_DIR\logs"
if (-not (Test-Path $npmLogDir)) { New-Item -ItemType Directory -Path $npmLogDir -Force | Out-Null }
if (Test-Path "$INSTALL_DIR\realtime\node_modules\socket.io") {
    Ok "node_modules 확인 (패키지 포함분 사용)"
} else {
    Warn "node_modules 없음 — npm install 시도 중 (인터넷 필요)..."
    $npmProc = Start-Process -FilePath "npm" `
        -ArgumentList "install", "--omit=dev" `
        -WorkingDirectory "$INSTALL_DIR\realtime" `
        -RedirectStandardOutput "$npmLogDir\npm-install-out.log" `
        -RedirectStandardError  "$npmLogDir\npm-install-err.log" `
        -Wait -PassThru -NoNewWindow
    if ($npmProc.ExitCode -eq 0) {
        Ok "npm install 완료"
    } else {
        Write-Host @"

  [!!] npm install 실패 — 인터넷 없는 서버에서는 개발 PC에서 아래를 실행 후
       ECH-deploy.zip 을 다시 만들어야 합니다:

       .\deploy\build-package.ps1   (node_modules 자동 포함)

"@ -ForegroundColor Yellow
        Get-Content "$npmLogDir\npm-install-err.log" -ErrorAction SilentlyContinue | Select-Object -Last 10 | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
        Fatal "node_modules 설치 실패"
    }
}

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
    "FILE_STORAGE_DIR"           = $FILE_STORAGE_INPUT.Replace('\','/')
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
    New-Item -ItemType Directory -Path "$INSTALL_DIR\tools" -Force | Out-Null

    # 1순위: 배포 패키지 내 tools\nssm.exe (build-package.ps1 이 포함시킨 것)
    $pkgNssm = Join-Path $scriptDir "tools\nssm.exe"
    if (Test-Path $pkgNssm) {
        Copy-Item $pkgNssm $nssmPath -Force
        Ok "NSSM 패키지에서 복사: $nssmPath"
    } else {
        # 2순위: 인터넷 다운로드 시도
        Info "NSSM 다운로드 중..."
        $downloaded = $false
        try {
            $nssmUrl = "https://nssm.cc/release/nssm-2.24.zip"
            $nssmZip = "$env:TEMP\nssm.zip"
            Invoke-WebRequest -Uri $nssmUrl -OutFile $nssmZip -UseBasicParsing -TimeoutSec 30
            Expand-Archive -Path $nssmZip -DestinationPath "$env:TEMP\nssm-extract" -Force
            $nssmExe = Get-ChildItem "$env:TEMP\nssm-extract" -Recurse -Filter "nssm.exe" |
                       Where-Object { $_.FullName -match "win64" } |
                       Select-Object -First 1
            if (-not $nssmExe) {
                $nssmExe = Get-ChildItem "$env:TEMP\nssm-extract" -Recurse -Filter "nssm.exe" | Select-Object -First 1
            }
            Copy-Item $nssmExe.FullName $nssmPath -Force
            Ok "NSSM 다운로드 완료"
            $downloaded = $true
        } catch {}

        if (-not $downloaded) {
            Write-Host @"

  [!!] NSSM 을 찾을 수 없습니다.
  해결 방법 (택1):

  A) 개발 PC 에서 build-package.ps1 을 다시 실행하면 tools\nssm.exe 가
     ECH-deploy.zip 에 자동 포함됩니다. ZIP 을 다시 받아 압축 해제 후 재실행하세요.

  B) 인터넷 되는 PC 에서 아래 URL 다운로드 후 이 서버에 복사:
     https://nssm.cc/release/nssm-2.24.zip
     압축 해제 → win64\nssm.exe 를 $nssmPath 에 복사 후 재실행하세요.

"@ -ForegroundColor Yellow
            Fatal "NSSM 설치 필요"
        }
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

# 자바 경로 탐색 (PATH 재확인 후 디스크 탐색 폴백)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
$javaCmdObj = Get-Command java -ErrorAction SilentlyContinue
$javaExe = if ($javaCmdObj) { $javaCmdObj.Source } else { $null }
if (-not $javaExe) {
    $javaExe = Find-JavaExeOnDisk
}
if (-not $javaExe) { Fatal "java.exe 를 찾을 수 없습니다. Java 17 설치 후 PowerShell 창을 닫고 다시 실행하세요." }

# DB 등 모든 설정값을 -D JVM 인수로 직접 전달 (NSSM 환경변수 전달 불안정 문제 회피)
$fileStorageDir = $FILE_STORAGE_INPUT.Replace('\', '/')
$jvmArgs = (
    "-Xms128m -Xmx512m",
    "-DDB_HOST=$DB_HOST",
    "-DDB_PORT=$DB_PORT",
    "-DDB_NAME=$DB_NAME",
    "-DDB_USER=$DB_USER",
    "-DDB_PASSWORD=$DB_PASSWORD",
    "-DJWT_SECRET=$JWT_SECRET",
    "-DJWT_EXPIRATION_MS=28800000",
    "-DREALTIME_INTERNAL_TOKEN=$REALTIME_TOKEN",
    "-DREALTIME_INTERNAL_BASE_URL=http://localhost:3001",
    "-DFILE_STORAGE_DIR=$fileStorageDir",
    "-DAPP_RELEASES_DIR=$($INSTALL_DIR.Replace('\','/'))/releases",
    "-DMAX_UPLOAD_SIZE=500MB",
    "-DMAX_REQUEST_SIZE=500MB",
    "-DEXPOSE_ERROR_DETAIL=false",
    "-jar `"$INSTALL_DIR\backend\ech-backend.jar`""
) -join " "

& "$nssmPath" install $svcName "$javaExe" | Out-Null
& "$nssmPath" set $svcName AppParameters $jvmArgs | Out-Null
& "$nssmPath" set $svcName AppDirectory "$INSTALL_DIR\backend" | Out-Null
& "$nssmPath" set $svcName AppStdout "$INSTALL_DIR\logs\backend-out.log" | Out-Null
& "$nssmPath" set $svcName AppStderr "$INSTALL_DIR\logs\backend-error.log" | Out-Null
& "$nssmPath" set $svcName AppRotateFiles 1 | Out-Null
& "$nssmPath" set $svcName AppRotateBytes 10485760 | Out-Null
& "$nssmPath" set $svcName Start SERVICE_AUTO_START | Out-Null
& "$nssmPath" set $svcName DisplayName "ECH Backend (Spring Boot)" | Out-Null

# UNC 경로 사용 시: 네트워크 접근 가능한 계정으로 서비스 실행
if ($isUncStorage -and $SVC_ACCOUNT_USER -and $SVC_ACCOUNT_PASS) {
    & "$nssmPath" set $svcName ObjectName "$SVC_ACCOUNT_USER" "$SVC_ACCOUNT_PASS" | Out-Null
    Ok "서비스 계정 설정: $SVC_ACCOUNT_USER"
    # DB 서버 네트워크 자격증명 등록 (cmdkey)
    $uncHost = ($FILE_STORAGE_INPUT -replace '^\\\\([^\\]+).*','$1')
    cmdkey /add:$uncHost /user:$SVC_ACCOUNT_USER /pass:$SVC_ACCOUNT_PASS | Out-Null
    Ok "네트워크 자격증명 등록: $uncHost"
    # 로컬 폴더 대신 UNC 연결 테스트
    try {
        $null = Get-ChildItem $FILE_STORAGE_INPUT -ErrorAction Stop
        Ok "네트워크 저장소 접근 확인: $FILE_STORAGE_INPUT"
    } catch {
        Warn "네트워크 저장소 접근 실패 — DB 서버 공유 설정을 확인하세요: $FILE_STORAGE_INPUT"
        Warn "서비스는 계속 등록합니다. 백엔드 기동 전 공유 설정을 완료하세요."
    }
} else {
    # 로컬 저장소: storage 폴더 생성
    $localStorage = $FILE_STORAGE_INPUT.Replace('/', '\')
    if (-not (Test-Path $localStorage)) {
        New-Item -ItemType Directory -Path $localStorage -Force | Out-Null
        Ok "로컬 저장소 폴더 생성: $localStorage"
    }
}

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
# 7. NSSM 으로 리얼타임 서비스 등록 (PM2 불필요)
# ════════════════════════════════════════════
Title "7단계 — 리얼타임 서버 Windows 서비스 등록 (NSSM)"

# node.exe 경로 탐색
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
$nodeCmdObj = Get-Command node -ErrorAction SilentlyContinue
$nodeExe = if ($nodeCmdObj) { $nodeCmdObj.Source } else { $null }
if (-not $nodeExe) {
    $nodeExe = Find-NodeExeOnDisk
}
if (-not $nodeExe) { Fatal "node.exe 를 찾을 수 없습니다. Node.js 설치 후 재실행하세요." }
Ok "node.exe 경로: $nodeExe"

# 기존 서비스 제거
$rtSvcName = "ECH-Realtime"
$rtExisting = Get-Service $rtSvcName -ErrorAction SilentlyContinue
if ($rtExisting) {
    Stop-Service $rtSvcName -Force -ErrorAction SilentlyContinue
    & "$nssmPath" remove $rtSvcName confirm | Out-Null
    Warn "기존 서비스 '$rtSvcName' 제거"
}

# server.js 존재 확인
$serverJs = "$INSTALL_DIR\realtime\src\server.js"
if (-not (Test-Path $serverJs)) { Fatal "server.js 를 찾을 수 없습니다: $serverJs" }

# NSSM 서비스 등록 (경로는 반드시 백슬래시 절대경로)
$nodeExeAbs  = $nodeExe.Replace('/', '\')
$serverJsAbs = $serverJs.Replace('/', '\')
$rtWorkDir   = "$INSTALL_DIR\realtime"

& "$nssmPath" install $rtSvcName "$nodeExeAbs" | Out-Null
& "$nssmPath" set $rtSvcName AppParameters "`"$serverJsAbs`"" | Out-Null
& "$nssmPath" set $rtSvcName AppDirectory "$rtWorkDir" | Out-Null
& "$nssmPath" set $rtSvcName AppStdout "$INSTALL_DIR\logs\realtime-out.log" | Out-Null
& "$nssmPath" set $rtSvcName AppStderr "$INSTALL_DIR\logs\realtime-error.log" | Out-Null
& "$nssmPath" set $rtSvcName AppRotateFiles 1 | Out-Null
& "$nssmPath" set $rtSvcName AppRotateBytes 10485760 | Out-Null
& "$nssmPath" set $rtSvcName Start SERVICE_AUTO_START | Out-Null
& "$nssmPath" set $rtSvcName DisplayName "ECH Realtime (Socket.IO)" | Out-Null
# 서비스 재시작 정책 (오류 시 자동 재시작)
& "$nssmPath" set $rtSvcName AppRestartDelay 3000 | Out-Null

# 리얼타임 서버 환경변수 주입
$rtVars = [ordered]@{
    "DB_HOST"                 = $DB_HOST
    "DB_PORT"                 = $DB_PORT
    "DB_NAME"                 = $DB_NAME
    "DB_USER"                 = $DB_USER
    "DB_PASSWORD"             = $DB_PASSWORD
    "SOCKET_PORT"             = "3001"
    "REALTIME_INTERNAL_TOKEN" = $REALTIME_TOKEN
}
foreach ($k in $rtVars.Keys) {
    & "$nssmPath" set $rtSvcName AppEnvironmentExtra "+$k=$($rtVars[$k])" | Out-Null
}
Ok "ECH-Realtime 서비스 등록 완료"
Info "  실행 파일 : $nodeExeAbs"
Info "  스크립트  : $serverJsAbs"
Info "  작업 디렉 : $rtWorkDir"

# 서비스 시작 (실패 시 로그 자동 출력)
try {
    Start-Service $rtSvcName -ErrorAction Stop
} catch {
    Warn "서비스 시작 실패 — 아래 로그를 확인하세요:"
    Start-Sleep -Seconds 2
    $errLog = "$INSTALL_DIR\logs\realtime-error.log"
    if (Test-Path $errLog) {
        Get-Content $errLog -Tail 20 | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
    } else {
        Warn "로그 파일 없음: $errLog"
        Warn "수동 확인: & `"$nodeExeAbs`" `"$serverJsAbs`"  (PowerShell 에서 직접 실행)"
    }
}
Start-Sleep -Seconds 3
$rtSt = (Get-Service $rtSvcName -ErrorAction SilentlyContinue).Status
if ($rtSt -eq "Running") { Ok "ECH-Realtime 서비스 시작 완료" }
else { Warn "서비스 상태: $rtSt — 수동 진단: & `"$nodeExeAbs`" `"$serverJsAbs`"" }

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
} catch { Warn "리얼타임 응답 없음. 로그 확인: $INSTALL_DIR\logs\realtime-error.log" }

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
    Start-Service ECH-Realtime      # 리얼타임 시작
    Stop-Service ECH-Realtime       # 리얼타임 중지
    Restart-Service ECH-Realtime    # 리얼타임 재시작

"@ -ForegroundColor Green

Read-Host "  Enter 키로 종료"
