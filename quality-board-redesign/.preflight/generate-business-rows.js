const fs = require('fs');

const business = JSON.parse(fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/metrics.json', 'utf8'));
const atomic = JSON.parse(fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/metrics-127.json', 'utf8'));
const atomicMap = new Map(atomic.map(m => [m.code, m]));

const visible = business.filter(m => m.is_show_page === '1' || m.is_show_page === 1).sort((a, b) => Number(a.sort_no) - Number(b.sort_no));
console.log('visible business metrics:', visible.length);

function getUnit(m) {
  if (m.percent_rate === '1000' || m.percent_rate === 1000) return '‰';
  return m.percent_unit || '%';
}

function sampleValue(m, seed) {
  // deterministic pseudo-random
  const code = m.numerator_code || '';
  const s = code.split('').reduce((a, c) => a + c.charCodeAt(0), 0) + seed;
  const rand = ((s * 9301 + 49297) % 233280) / 233280;
  const rate = Number(m.percent_rate) || 100;
  const prec = Number(m.percent_precision) || 2;
  const v = rand * rate;
  return v.toFixed(prec);
}

// --- board rows ---
let boardRows = '';
for (const m of visible) {
  const unit = getUnit(m);
  const numCode = m.numerator_code || '—';
  const denCode = m.denominator_code || '—';
  const numName = atomicMap.get(numCode)?.name || '';
  const denName = atomicMap.get(denCode)?.name || '';
  const numValue = '—'; // no real data
  const denValue = '—';
  const current = '—';
  const target = (m.target_value === null || m.target_value === undefined || m.target_value === '') ? '未配置' : m.target_value;
  const warning = (m.warning_value === null || m.warning_value === undefined || m.warning_value === '') ? '未配置' : m.warning_value;
  const status = current === '—' ? '无数据' : '已出数';
  const statusClass = current === '—' ? 'status-empty' : 'status-ok';
  const remarkDot = m.remark ? `<span class="remark-dot" title="${m.remark}">?</span>` : '';

  boardRows += `              <tr data-rule="${m.id}">\n`;
  boardRows += `                <td class="name">${m.count_name}${remarkDot}</td>\n`;
  boardRows += `                <td class="code qb-mono">${numCode}</td>\n`;
  boardRows += `                <td class="num-value">${numValue}</td>\n`;
  boardRows += `                <td class="atom-name">${numName}</td>\n`;
  boardRows += `                <td class="code qb-mono">${denCode}</td>\n`;
  boardRows += `                <td class="den-value">${denValue}</td>\n`;
  boardRows += `                <td class="atom-name">${denName}</td>\n`;
  boardRows += `                <td class="unit">${unit}</td>\n`;
  boardRows += `                <td class="current"><span class="empty">${current}</span></td>\n`;
  boardRows += `                <td class="target">${target}</td>\n`;
  boardRows += `                <td class="warning">${warning}</td>\n`;
  boardRows += `                <td class="status ${statusClass}">${status}</td>\n`;
  boardRows += `                <td class="lineage"><button class="btn-mini btn-lineage">分子</button><button class="btn-mini btn-lineage">分母</button></td>\n`;
  boardRows += `              </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/board-rows-business.html', boardRows);

// --- monthly rows for business metrics ---
function monthlyBizValue(m, month) {
  const rate = Number(m.percent_rate) || 100;
  const prec = Number(m.percent_precision) || 2;
  const code = m.numerator_code || '';
  const s = code.split('').reduce((a, c) => a + c.charCodeAt(0), 0) + month * 7;
  const rand = ((s * 9301 + 49297) % 233280) / 233280;
  const v = rand * rate;
  return v.toFixed(prec);
}

let monthlyRows = '';
for (const m of visible) {
  const months = [];
  for (let i = 1; i <= 12; i++) months.push(monthlyBizValue(m, i));
  const numeric = months.map(Number);
  const q1 = (numeric.slice(0,3).reduce((a,b)=>a+b,0)/3).toFixed(Number(m.percent_precision)||2);
  const q2 = (numeric.slice(3,6).reduce((a,b)=>a+b,0)/3).toFixed(Number(m.percent_precision)||2);
  const q3 = (numeric.slice(6,9).reduce((a,b)=>a+b,0)/3).toFixed(Number(m.percent_precision)||2);
  const q4 = (numeric.slice(9,12).reduce((a,b)=>a+b,0)/3).toFixed(Number(m.percent_precision)||2);
  const total = (numeric.reduce((a,b)=>a+b,0)/12).toFixed(Number(m.percent_precision)||2);
  const maxVal = Math.max(...numeric);
  const minVal = Math.min(...numeric);
  const maxMonth = (numeric.indexOf(maxVal) + 1) + '月';
  const minMonth = (numeric.indexOf(minVal) + 1) + '月';
  
  monthlyRows += `      <tr data-rule="${m.id}" data-name="${m.count_name}">\n`;
  monthlyRows += `        <td class="name">${m.count_name}</td>\n`;
  monthlyRows += `        <td class="unit">${getUnit(m)}</td>\n`;
  for (let i = 0; i < 12; i++) {
    const v = months[i];
    const highlight = Number(v) === maxVal || Number(v) === minVal ? 'highlight' : '';
    monthlyRows += `        <td class="month ${highlight}" data-month="${i+1}">${v}</td>\n`;
  }
  monthlyRows += `        <td class="quarter">${q1}</td><td class="quarter">${q2}</td><td class="quarter">${q3}</td><td class="quarter">${q4}</td>\n`;
  monthlyRows += `        <td class="total">${total}</td><td class="max">${maxMonth}</td><td class="min">${minMonth}</td>\n`;
  monthlyRows += `      </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/monthly-rows-business.html', monthlyRows);

// trend options for monthly: first 12 visible metrics
let trendOptions = '';
for (const m of visible.slice(0, 12)) {
  trendOptions += `                <option value="${m.id}">${m.count_name}</option>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/trend-options-business.html', trendOptions);

console.log('Generated business rows for board and monthly.');
