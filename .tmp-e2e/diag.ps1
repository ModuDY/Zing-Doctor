# Dump browser console/error output for the target URL (local diagnostic only)
param(
    [int]$Port = 5499,
    [string]$EntryUrl = ""
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tmp = $PSScriptRoot
$nodeExe = Join-Path (Get-ChildItem "$root\.tools" -Directory -Filter 'node-v*-win-x64' | Sort-Object Name -Descending | Select-Object -First 1).FullName 'node.exe'
$browser = @(
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $EntryUrl) {
    $EntryUrl = "http://127.0.0.1:$Port/entry/apache2-overview?extToken=zing-icu-link-token-2026&departCode=ICU01&departName=%E9%87%8D%E7%97%87%E5%8C%BB%E5%AD%A6%E7%A7%91"
}
Remove-Item (Join-Path $tmp 'server.log') -ErrorAction SilentlyContinue
$env:PORT = "$Port"
$prof = Join-Path $tmp ("prof-" + [Guid]::NewGuid().ToString('N'))
$out = Join-Path $tmp 'browser-full.log'

$proc = Start-Process -FilePath $nodeExe -ArgumentList "`"$tmp\mock-server.js`"" -PassThru -WindowStyle Hidden
try {
    for ($i = 0; $i -lt 40; $i++) {
        $c = New-Object System.Net.Sockets.TcpClient
        try { if ($c.ConnectAsync('127.0.0.1', $Port).Wait(500)) { $c.Close(); break } } catch { }
        Start-Sleep -Milliseconds 300
    }
    & $browser --headless=new --disable-gpu --no-sandbox --no-first-run --enable-logging=stderr --v=1 `
        "--user-data-dir=$prof" --virtual-time-budget=15000 --dump-dom $EntryUrl *> $out
    Write-Host "=== error-ish lines ===" -ForegroundColor Cyan
    Get-Content $out -Encoding UTF8 | Where-Object { $_ -match 'ERROR|CONSOLE|Uncaught|SEVERE|Failed|TypeError|ReferenceError|SyntaxError' } | Select-Object -First 40
    Write-Host "=== server log ===" -ForegroundColor Cyan
    Get-Content (Join-Path $tmp 'server.log') -Encoding UTF8
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    Remove-Item $prof -Recurse -Force -ErrorAction SilentlyContinue
}
