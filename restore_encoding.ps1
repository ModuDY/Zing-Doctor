$path = 'd:\work\ZING\quality-board-redesign\pages\quality-monthly.html'
$corrupted = Get-Content $path -Raw -Encoding UTF8
$bytes = [System.Text.Encoding]::GetEncoding('GBK').GetBytes($corrupted)
$fixed = [System.Text.Encoding]::UTF8.GetString($bytes)
[System.IO.File]::WriteAllText($path, $fixed, [System.Text.Encoding]::UTF8)
Write-Host "Encoding restored."
