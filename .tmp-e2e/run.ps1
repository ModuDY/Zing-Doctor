# APACHE II overview page - headless browser E2E check (local only, not shipped)
# ASCII-only on purpose: Windows PowerShell 5.1 mis-decodes UTF-8 (no BOM) with CJK text.
# Expect/Forbid are '|' separated single strings to survive "-File" argument binding.
param(
    [int]$Port = 5199,
    [string]$EntryUrl = "",
    [string]$Expect = "",
    [string]$Forbid = "",
    [string]$Name = "case",
    [int]$Budget = 15000,
    [string]$Mode = "old"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tmp = $PSScriptRoot
$nodeExe = Join-Path (Get-ChildItem "$root\.tools" -Directory -Filter 'node-v*-win-x64' | Sort-Object Name -Descending | Select-Object -First 1).FullName 'node.exe'

$browsers = @(
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
)
$browser = $browsers | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw 'Chrome/Edge not found, cannot run headless check' }

if (-not $EntryUrl) {
    $EntryUrl = "http://127.0.0.1:$Port/entry/apache2-overview?extToken=zing-icu-link-token-2026&departCode=ICU01&departName=%E9%87%8D%E7%97%87%E5%8C%BB%E5%AD%A6%E7%A7%91"
}
$expectList = @($Expect -split '\|' | Where-Object { $_ })
$forbidList = @($Forbid -split '\|' | Where-Object { $_ })

$logFile = Join-Path $tmp 'server.log'
Remove-Item $logFile -ErrorAction SilentlyContinue
$env:PORT = "$Port"

# 每次使用全新 profile，避免复用旧 profile 时 Chrome 直接把结果丢掉
$profileDir = Join-Path $tmp ("profile-" + [Guid]::NewGuid().ToString('N'))
$stderrFile = Join-Path $tmp "browser-$Name.err"

Write-Host ">>> starting mock server" -ForegroundColor Cyan
$proc = Start-Process -FilePath $nodeExe -ArgumentList "`"$tmp\mock-server.js`"" -PassThru -WindowStyle Hidden

try {
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        $c = New-Object System.Net.Sockets.TcpClient
        try {
            if ($c.ConnectAsync('127.0.0.1', $Port).Wait(500)) { $ready = $true; $c.Close(); break }
        } catch { }
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw "mock server not ready on port $Port" }

    Write-Host ">>> headless open: $EntryUrl" -ForegroundColor Cyan
    # 必须走管道：PowerShell 对 GUI 子系统程序（chrome.exe）的直接赋值不会捕获 stdout；
    # 用 old headless：其 virtual-time-budget 会等 SPA 的异步路由/请求真正结束，new headless 会提前 dump
    $domText = (& $browser ("--headless=" + $Mode) --disable-gpu --no-sandbox --no-first-run --disable-extensions `
        "--user-data-dir=$profileDir" ("--virtual-time-budget=" + $Budget) --dump-dom $EntryUrl 2>$stderrFile | Out-String)
    $domFile = Join-Path $tmp "dom-$Name.html"
    [IO.File]::WriteAllText($domFile, $domText, (New-Object Text.UTF8Encoding($false)))
    Write-Host ">>> DOM saved: $domFile ($($domText.Length) chars)" -ForegroundColor DarkGray

    $failed = @()
    foreach ($kw in $expectList) {
        if ($domText -notlike "*$kw*") { $failed += "MISSING expected: $kw" }
    }
    foreach ($kw in $forbidList) {
        if ($domText -like "*$kw*") { $failed += "FOUND forbidden: $kw" }
    }

    Write-Host ""
    Write-Host "==== mock server log ====" -ForegroundColor DarkGray
    Get-Content $logFile -Encoding UTF8 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $_" }

    Write-Host ""
    if ($failed.Count -eq 0) {
        Write-Host "[PASS] $Name ($($expectList.Count) expectations ok)" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "[FAIL] $Name" -ForegroundColor Red
        $failed | ForEach-Object { Write-Host "   - $_" -ForegroundColor Red }
        if (Test-Path $stderrFile) {
            Write-Host "   --- browser stderr ---" -ForegroundColor DarkRed
            Get-Content $stderrFile -ErrorAction SilentlyContinue | Select-Object -First 10 | ForEach-Object { Write-Host "   $_" -ForegroundColor DarkRed }
        }
        exit 1
    }
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    Start-Sleep -Milliseconds 300
    Remove-Item $profileDir -Recurse -Force -ErrorAction SilentlyContinue
}
