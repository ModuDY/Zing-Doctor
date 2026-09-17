$html = Get-Content 'd:\work\ZING\quality-board-redesign\pages\quality-monthly.html' -Raw
$rows = Get-Content 'd:\work\ZING\quality-board-redesign\.preflight\monthly-rows-127.html' -Raw

$thead = @'
            <thead><tr>
              <th style="min-width: 120px; position: sticky; left: 0; z-index: 2; background: var(--qb-stone-50);">指标编码</th>
              <th style="min-width: 260px; position: sticky; left: 120px; z-index: 2; background: var(--qb-stone-50);">指标名称</th>
              <th style="width: 130px; position: sticky; left: 380px; z-index: 2; background: var(--qb-stone-50);">域</th>
              <th style="width: 60px; text-align: center;">单位</th>
              <th style="width: 84px; text-align: right;">1月</th><th style="width: 84px; text-align: right;">2月</th><th style="width: 84px; text-align: right;">3月</th><th style="width: 84px; text-align: right;">4月</th><th style="width: 84px; text-align: right;">5月</th><th style="width: 84px; text-align: right;">6月</th>
              <th style="width: 84px; text-align: right;">7月</th><th style="width: 84px; text-align: right;">8月</th><th style="width: 84px; text-align: right;">9月</th><th style="width: 84px; text-align: right;">10月</th><th style="width: 84px; text-align: right;">11月</th><th style="width: 84px; text-align: right;">12月</th>
              <th style="width: 86px; text-align: right;">Q1</th><th style="width: 86px; text-align: right;">Q2</th><th style="width: 86px; text-align: right;">Q3</th><th style="width: 86px; text-align: right;">Q4</th>
              <th style="width: 100px; text-align: right;">全年合计</th>
              <th style="width: 86px; text-align: right;">月均</th>
              <th style="width: 76px; text-align: center;">最高月</th>
              <th style="width: 76px; text-align: center;">最低月</th>
            </tr></thead>
'@

$newTable = "<table class=`"qb-table qb-wide-table`" style=`"min-width: 2400px;`">`n" + $thead + "            <tbody>`n" + $rows + "`n            </tbody>`n          </table>"
$pattern = '<table class="qb-table qb-wide-table" style="min-width: 1900px;">.*?</table>'
$html = [regex]::Replace($html, $pattern, $newTable, [System.Text.RegularExpressions.RegexOptions]::Singleline)

$html | Set-Content 'd:\work\ZING\quality-board-redesign\pages\quality-monthly.html' -Encoding UTF8
Write-Host "Table updated. Rows imported: $($rows.Split('<tr').Length - 1)"
