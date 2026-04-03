# Create or update a GitHub Release and upload Windows NSIS artifacts from desktop/dist.
# electron-updater needs: latest.yml, the installer named in that file, and (recommended) *.exe.blockmap.
# Requires: GITHUB_TOKEN (repo releases write), successful `npm run build:win` (see desktop/dist).
# Usage: powershell -File ./tools/publish-electron-github-release.ps1 [tag]
# Example: powershell -File ./tools/publish-electron-github-release.ps1 v0.0.4

$ErrorActionPreference = "Stop"

$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Set GITHUB_TOKEN (PAT with contents/releases write for this repo)."
}

$owner = "Jin-Ra-Men"
$repo = "Enterprise-Collaboration-Hub-ECH-"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pkgPath = Join-Path $repoRoot "desktop\package.json"
$pkg = Get-Content -LiteralPath $pkgPath -Raw | ConvertFrom-Json
$ver = $pkg.version
$tag = if ($args.Count -ge 1 -and -not [string]::IsNullOrWhiteSpace($args[0])) { $args[0].Trim() } else { "v$ver" }

$distDir = Join-Path $repoRoot "desktop\dist"
$ymlPath = Join-Path $distDir "latest.yml"
if (-not (Test-Path -LiteralPath $ymlPath)) {
  throw "latest.yml not found: $ymlPath (run: cd desktop && npm run build:win)"
}

$ymlRaw = Get-Content -LiteralPath $ymlPath -Raw
if ($ymlRaw -notmatch '(?m)^path:\s*(.+)\s*$') {
  throw "Could not parse installer filename from path: in $ymlPath"
}
$installerName = $Matches[1].Trim()
# electron-builder may list a URL-safe name in latest.yml while the on-disk NSIS file keeps spaces (e.g. "ECH Setup 0.0.4.exe").
$installerPath = Join-Path $distDir $installerName
if (-not (Test-Path -LiteralPath $installerPath)) {
  $spaced = Join-Path $distDir "ECH Setup $ver.exe"
  if (Test-Path -LiteralPath $spaced) {
    $installerPath = $spaced
  } else {
    throw "Installer not found: expected '$installerName' or 'ECH Setup $ver.exe' under $distDir"
  }
}

$blockmapAssetName = "$installerName.blockmap"
$blockmapPath = Join-Path $distDir $blockmapAssetName
if (-not (Test-Path -LiteralPath $blockmapPath)) {
  $spacedBm = Join-Path $distDir "ECH Setup $ver.exe.blockmap"
  if (Test-Path -LiteralPath $spacedBm) {
    $blockmapPath = $spacedBm
  }
}

$assetNames = @($installerName, "latest.yml")
if (Test-Path -LiteralPath $blockmapPath) {
  $assetNames += $blockmapAssetName
} else {
  Write-Warning "Optional blockmap missing (differential updates may be disabled): $blockmapAssetName"
}

$headers = @{
  Authorization = "Bearer $token"
  Accept        = "application/vnd.github+json"
}
$baseUri = "https://api.github.com/repos/$owner/$repo"

$rel = $null
try {
  $rel = Invoke-RestMethod -Method Get -Uri "$baseUri/releases/tags/$tag" -Headers $headers -ErrorAction Stop
} catch {
  $rel = $null
}

if (-not $rel) {
  $payloadObj = @{
    tag_name         = $tag
    name             = $tag
    target_commitish = "main"
    body             = "ECH desktop (Electron) Windows NSIS + auto-update metadata (version $ver)."
    draft            = $false
    prerelease       = $false
  }
  $payloadJson = $payloadObj | ConvertTo-Json -Depth 10
  $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
  $rel = Invoke-RestMethod -Method Post -Uri "$baseUri/releases" -Headers $headers `
    -ContentType "application/json" -Body $payloadBytes
}

$nameSet = @{}
foreach ($n in $assetNames) { $nameSet[$n] = $true }

foreach ($a in @($rel.assets)) {
  if ($null -ne $a -and $nameSet.ContainsKey($a.name)) {
    Invoke-RestMethod -Method Delete -Uri "$baseUri/releases/assets/$($a.id)" -Headers $headers | Out-Null
  }
}

# Re-fetch release so upload_url and assets are current after deletes
$rel = Invoke-RestMethod -Method Get -Uri "$baseUri/releases/tags/$tag" -Headers $headers
$uploadBase = ($rel.upload_url -replace "\{\?name,label\}", "")

function Upload-ReleaseAsset {
  param(
    [string]$FilePath,
    [string]$AssetName
  )
  $uploadUrl = $uploadBase + "?name=" + [System.Uri]::EscapeDataString($AssetName)
  $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
  $ct = "application/octet-stream"
  return Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers -ContentType $ct -Body $fileBytes
}

$urls = @()
$urls += (Upload-ReleaseAsset -FilePath $installerPath -AssetName $installerName).browser_download_url
$urls += (Upload-ReleaseAsset -FilePath $ymlPath -AssetName "latest.yml").browser_download_url
if (Test-Path -LiteralPath $blockmapPath) {
  $urls += (Upload-ReleaseAsset -FilePath $blockmapPath -AssetName $blockmapAssetName).browser_download_url
}

$urls | ForEach-Object { Write-Output $_ }
