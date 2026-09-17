$path = 'd:\work\ZING\quality-board-redesign\pages\quality-monthly.html'
$s = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$lines = $s -split "`n"
for ($i = 0; $i -lt 10; $i++) {
    $line = $lines[$i]
    Write-Host "Line $($i+1): $line"
    $chars = $line.ToCharArray()
    foreach ($c in $chars) {
        Write-Host ("  U+{0:X4} '{1}'" -f [int]$c, $c)
    }
}
