const fs = require('fs');

const pagePath = 'd:/work/ZING/quality-board-redesign/pages/quality-monthly.html';
let html = fs.readFileSync(pagePath, 'utf8');

const rows = fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/monthly-rows-business.html', 'utf8');
const trendOptions = fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/trend-options-business.html', 'utf8');

// Replace trend select options
const trendSelectStart = '<select class="qb-select" data-dom-id="trend-select" style="min-width:340px;flex:1;">';
const trendSelectEnd = '</select>';
const tsStart = html.indexOf(trendSelectStart);
const tsEnd = html.indexOf(trendSelectEnd, tsStart);
if (tsStart !== -1 && tsEnd !== -1) {
  html = html.slice(0, tsStart + trendSelectStart.length) + '\n' + trendOptions + '              ' + html.slice(tsEnd);
}

// Replace table header and rows
const tableStartMarker = '<table class="qb-table qb-wide-table" style="min-width: 2400px;">';
const tableEndMarker = '</table>';
const tStart = html.indexOf(tableStartMarker);
const tEnd = html.lastIndexOf(tableEndMarker);
if (tStart === -1 || tEnd === -1) {
  console.error('Table markers not found');
  process.exit(1);
}

const newTable = `<table class="qb-table qb-wide-table" style="min-width: 2200px;">
            <thead><tr>
              <th style="min-width: 120px; position: sticky; left: 0; z-index: 2; background: var(--qb-stone-50);">规则ID</th>
              <th style="min-width: 320px; position: sticky; left: 120px; z-index: 2; background: var(--qb-stone-50);">指标名称</th>
              <th style="width: 60px; text-align: center;">单位</th>
              <th style="width: 84px; text-align: right;">1月</th><th style="width: 84px; text-align: right;">2月</th><th style="width: 84px; text-align: right;">3月</th><th style="width: 84px; text-align: right;">4月</th><th style="width: 84px; text-align: right;">5月</th><th style="width: 84px; text-align: right;">6月</th>
              <th style="width: 84px; text-align: right;">7月</th><th style="width: 84px; text-align: right;">8月</th><th style="width: 84px; text-align: right;">9月</th><th style="width: 84px; text-align: right;">10月</th><th style="width: 84px; text-align: right;">11月</th><th style="width: 84px; text-align: right;">12月</th>
              <th style="width: 86px; text-align: right;">Q1</th><th style="width: 86px; text-align: right;">Q2</th><th style="width: 86px; text-align: right;">Q3</th><th style="width: 86px; text-align: right;">Q4</th>
              <th style="width: 100px; text-align: right;">全年均值</th>
              <th style="width: 76px; text-align: center;">最高月</th>
              <th style="width: 76px; text-align: center;">最低月</th>
            </tr></thead>
            <tbody>
${rows}
            </tbody>
          </table>`;

html = html.slice(0, tStart) + newTable + html.slice(tEnd + tableEndMarker.length);

// Update summary tags
html = html.replace(/原子指标 <strong>\d+<\/strong>/, '业务指标 <strong>60</strong>');
html = html.replace(/汇总指标数 <strong>\d+<\/strong>/, '汇总指标数 <strong>60</strong>');

fs.writeFileSync(pagePath, html);
console.log('Updated quality-monthly.html with business metrics');
