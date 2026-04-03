# Create or update a GitHub Release and upload the Windows NSIS installer from desktop/dist.
# Requires: GITHUB_TOKEN (repo releases write), desktop/dist/ECH Setup {version}.exe built.
# Usage: powershell -File ./tools/publish-electron-github-release.ps1 [tag]
# Example: powershell -File ./tools/publish-electron-github-release.ps1 v0.0.2

$ErrorActionPreference = "Stop"

$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Set GITHUB_TOKEN (PAT with contents/releases write for this repo)."
}

$owner = "Jin-Ra-Men"
$repo = "Enterprise-Collaboration-Hub-ECH-"
$tag = if ($args.Count -ge 1 -and -not [string]::IsNullOrWhiteSpace($args[0])) { $args[0].Trim() } else { "v0.0.2" }

$repoRoot = Split-Path -Parent $PSScriptRoot
$pkgPath = Join-Path $repoRoot "desktop\package.json"
$pkg = Get-Content -LiteralPath $pkgPath -Raw | ConvertFrom-Json
$ver = $pkg.version
$exeName = "ECH Setup $ver.exe"
$exePath = Join-Path $repoRoot "desktop\dist\$exeName"

if (-not (Test-Path -LiteralPath $exePath)) {
  throw "Installer not found: $exePath (run: cd desktop && npm run build:win)"
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
    body             = "ECH desktop (Electron) Windows NSIS installer (version $ver)."
    draft            = $false
    prerelease       = $false
  }
  $payloadJson = $payloadObj | ConvertTo-Json -Depth 10
  $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
  $rel = Invoke-RestMethod -Method Post -Uri "$baseUri/releases" -Headers $headers `
    -ContentType "application/json" -Body $payloadBytes
}

foreach ($a in @($rel.assets)) {
  if ($null -ne $a -and $a.name -eq $exeName) {
    Invoke-RestMethod -Method Delete -Uri "$baseUri/releases/assets/$($a.id)" -Headers $headers | Out-Null
  }
}

$uploadBase = ($rel.upload_url -replace "\{\?name,label\}", "")
$uploadUrl = $uploadBase + "?name=" + [System.Uri]::EscapeDataString($exeName)
$fileBytes = [System.IO.File]::ReadAllBytes($exePath)
$resp = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers `
  -ContentType "application/octet-stream" -Body $fileBytes

Write-Output $resp.browser_download_url
