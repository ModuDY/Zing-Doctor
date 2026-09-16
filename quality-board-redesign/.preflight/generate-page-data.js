const fs = require('fs');

const metrics = JSON.parse(fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/metrics-127.json', 'utf8'));
const domainOrder = ['患者流转','脓毒症_感染性休克','抗菌药送检','DVT预防','评分_资源','评估依从','导管_管路_院感','ARDS专项'];

function statusClass(status) {
  if (status === 'IMPL') return 'status-impl';
  if (status === 'PENDING_SOURCE') return 'status-pending';
  if (status === 'PLACEHOLDER') return 'status-placeholder';
  if (status === 'MANUAL') return 'status-manual';
  return 'status-impl';
}
function statusLabel(status) {
  if (status === 'IMPL') return '已实现';
  if (status === 'PENDING_SOURCE') return '待接数据源';
  if (status === 'PLACEHOLDER') return '口径待定';
  if (status === 'MANUAL') return '人工录入';
  return status;
}
function calcLabel(status) {
  if (status === 'IMPL') return '已出数';
  if (status === 'PENDING_SOURCE') return '待接源';
  if (status === 'PLACEHOLDER') return '口径待定';
  if (status === 'MANUAL') return '待录入';
  return status;
}

const grouped = {};
for (const d of domainOrder) grouped[d] = [];
for (const m of metrics) {
  if (!grouped[m.domain]) grouped[m.domain] = [];
  grouped[m.domain].push(m);
}

// --- monthly rows: 1-12 + Q1-Q4 + total + avg + max/min months ---
function monthlyValue(m, month) {
  if (m.implStatus !== 'IMPL') return '—';
  // deterministic pseudo-random based on code + month
  const seed = m.code.split('').reduce((a, c) => a + c.charCodeAt(0), 0) + month;
  const rand = ((seed * 9301 + 49297) % 233280) / 233280;
  if (m.valueType === 'RATE') {
    const v = Math.round(rand * 100 * 100) / 100;
    return v.toFixed(2);
  }
  const v = Math.floor(rand * 50);
  return String(v);
}

let monthlyRows = '';
for (const m of metrics) {
  const months = [];
  for (let i = 1; i <= 12; i++) months.push(monthlyValue(m, i));
  const numeric = months.filter(v => v !== '—').map(Number);
  const q1 = numeric.length >= 3 ? (m.valueType === 'RATE' ? (numeric.slice(0,3).reduce((a,b)=>a+b,0)/3).toFixed(2) : numeric.slice(0,3).reduce((a,b)=>a+b,0)) : '—';
  const q2 = numeric.length >= 6 ? (m.valueType === 'RATE' ? (numeric.slice(3,6).reduce((a,b)=>a+b,0)/3).toFixed(2) : numeric.slice(3,6).reduce((a,b)=>a+b,0)) : '—';
  const q3 = numeric.length >= 9 ? (m.valueType === 'RATE' ? (numeric.slice(6,9).reduce((a,b)=>a+b,0)/3).toFixed(2) : numeric.slice(6,9).reduce((a,b)=>a+b,0)) : '—';
  const q4 = numeric.length >= 12 ? (m.valueType === 'RATE' ? (numeric.slice(9,12).reduce((a,b)=>a+b,0)/3).toFixed(2) : numeric.slice(9,12).reduce((a,b)=>a+b,0)) : '—';
  const total = numeric.length ? (m.valueType === 'RATE' ? (numeric.reduce((a,b)=>a+b,0)/numeric.length).toFixed(2) : numeric.reduce((a,b)=>a+b,0)) : '—';
  const avg = numeric.length ? (m.valueType === 'RATE' ? total : (numeric.reduce((a,b)=>a+b,0)/numeric.length).toFixed(1)) : '—';
  let maxMonth = '—', minMonth = '—';
  if (numeric.length) {
    const maxVal = Math.max(...numeric);
    const minVal = Math.min(...numeric);
    maxMonth = (numeric.indexOf(maxVal) + 1) + '月';
    minMonth = (numeric.indexOf(minVal) + 1) + '月';
  }
  const allDash = months.every(v => v === '—');
  const rowClass = allDash ? 'row-empty' : '';
  monthlyRows += `      <tr data-code="${m.code}" data-domain="${m.domain}" class="${rowClass}">\n`;
  monthlyRows += `        <td class="code">${m.code}</td><td class="name">${m.name}</td><td class="domain">${m.domain}</td><td class="unit">${m.unit}</td>\n`;
  for (let i = 0; i < 12; i++) {
    const v = months[i];
    const highlight = v !== '—' && numeric.length && (Number(v) === Math.max(...numeric) || Number(v) === Math.min(...numeric)) ? 'highlight' : '';
    monthlyRows += `        <td class="month ${highlight}" data-month="${i+1}">${v}</td>\n`;
  }
  monthlyRows += `        <td class="quarter">${q1}</td><td class="quarter">${q2}</td><td class="quarter">${q3}</td><td class="quarter">${q4}</td>\n`;
  monthlyRows += `        <td class="total">${total}</td><td class="avg">${avg}</td><td class="max">${maxMonth}</td><td class="min">${minMonth}</td>\n`;
  monthlyRows += `      </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/monthly-rows-127.html', monthlyRows);

// --- trend options for monthly page (first impl metric per domain, up to 12) ---
const trendOptions = [];
const seenDomains = new Set();
for (const m of metrics) {
  if (m.implStatus === 'IMPL' && !seenDomains.has(m.domain)) {
    seenDomains.add(m.domain);
    trendOptions.push({code: m.code, name: m.name, domain: m.domain});
  }
}
let trendOptionsHtml = '';
for (const opt of trendOptions.slice(0, 12)) {
  trendOptionsHtml += `                <option value="${opt.code}" data-domain="${opt.domain}">${opt.domain} · ${opt.name}</option>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/trend-options-127.html', trendOptionsHtml);

// --- config metric rows ---
let configMetricRows = '';
for (const m of metrics) {
  const sc = statusClass(m.implStatus);
  const sl = statusLabel(m.implStatus);
  const expr = m.where || m.numerator || '—';
  configMetricRows += `              <tr data-code="${m.code}" data-domain="${m.domain}" data-status="${m.implStatus}">\n`;
  configMetricRows += `                <td class="code">${m.code}</td>\n`;
  configMetricRows += `                <td class="name">${m.name}</td>\n`;
  configMetricRows += `                <td class="domain">${m.domain}</td>\n`;
  configMetricRows += `                <td class="fact">${m.fact || '—'}</td>\n`;
  configMetricRows += `                <td class="type">${m.valueType}</td>\n`;
  configMetricRows += `                <td class="status ${sc}">${sl}</td>\n`;
  configMetricRows += `                <td class="version">v${m.version}</td>\n`;
  configMetricRows += `                <td class="action"><button class="btn-mini btn-edit">编辑</button></td>\n`;
  configMetricRows += `              </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/config-metric-rows-127.html', configMetricRows);

// --- facts from YAML ---
const factFiles = ['patient.yaml','clinical.yaml','support.yaml'];
const facts = [];
for (const f of factFiles) {
  const content = fs.readFileSync(`d:/work/ZING/src/main/resources/quality/facts/${f}`, 'utf8');
  const lines = content.split('\n');
  let current = null;
  for (const line of lines) {
    const factMatch = line.match(/^-\s*fact:\s*(\S+)/);
    if (factMatch) {
      if (current) facts.push(current);
      current = {fact: factMatch[1], domain: '', status: 'ACTIVE', source: '', note: '', file: f};
    }
    if (current) {
      const dm = line.match(/^\s*domain:\s*(.+)$/);
      if (dm) current.domain = dm[1].trim();
      const sm = line.match(/^\s*status:\s*(.+)$/);
      if (sm) current.status = sm[1].trim();
      const som = line.match(/^\s*source:\s*(.+)$/);
      if (som) current.source = som[1].trim();
      const nm = line.match(/^\s*note:\s*(.+)$/);
      if (nm) current.note = nm[1].trim();
    }
  }
  if (current) facts.push(current);
}
console.log('Facts:', facts.length, facts.map(f=>f.fact));

let factsRows = '';
for (const f of facts) {
  const sc = statusClass(f.status);
  const sl = statusLabel(f.status);
  factsRows += `        <tr data-fact="${f.fact}">\n`;
  factsRows += `          <td class="fact-name">${f.fact}</td>\n`;
  factsRows += `          <td class="domain">${f.domain}</td>\n`;
  factsRows += `          <td class="status ${sc}">${sl}</td>\n`;
  factsRows += `          <td class="source">${f.source}</td>\n`;
  factsRows += `          <td class="note">${f.note}</td>\n`;
  factsRows += `          <td class="action"><button class="btn-mini btn-sql">查看 SQL</button></td>\n`;
  factsRows += `        </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/facts-rows.html', factsRows);

let configFactRows = '';
for (const f of facts) {
  const sc = statusClass(f.status);
  const sl = statusLabel(f.status);
  configFactRows += `              <tr data-fact="${f.fact}">\n`;
  configFactRows += `                <td class="fact-name">${f.fact}</td>\n`;
  configFactRows += `                <td class="domain">${f.domain}</td>\n`;
  configFactRows += `                <td class="status ${sc}">${sl}</td>\n`;
  configFactRows += `                <td class="source">${f.source}</td>\n`;
  configFactRows += `                <td class="action"><button class="btn-mini btn-edit">编辑</button></td>\n`;
  configFactRows += `              </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/config-fact-rows.html', configFactRows);

// --- config history rows sample ---
const historyRows = [
  {time:'2026-09-14 10:23', type:'METRIC', key:'quality_12', change:'UPDATE', version:2, operator:'张质控', note:'修正送检时间比较逻辑，改为首次培养/药敏/鉴定时间'},
  {time:'2026-09-13 16:45', type:'FACT', key:'fact_ards', change:'UPDATE', version:1, operator:'李信息', note:'补充俯卧位识别观察项关键词'},
  {time:'2026-09-12 09:10', type:'METRIC', key:'quality_311', change:'CREATE', version:1, operator:'系统', note:'初始化指标配置'},
  {time:'2026-09-10 11:02', type:'METRIC', key:'quality_312', change:'UPDATE', version:2, operator:'王主任', note:'调整中重度ARDS判定条件'},
  {time:'2026-09-08 14:30', type:'FACT', key:'fact_sepsis_bundle', change:'CREATE', version:1, operator:'系统', note:'初始化事实层配置'}
];
let configHistoryRows = '';
for (const h of historyRows) {
  configHistoryRows += `              <tr>\n`;
  configHistoryRows += `                <td class="time">${h.time}</td>\n`;
  configHistoryRows += `                <td class="type">${h.type}</td>\n`;
  configHistoryRows += `                <td class="key">${h.key}</td>\n`;
  configHistoryRows += `                <td class="change">${h.change}</td>\n`;
  configHistoryRows += `                <td class="version">v${h.version}</td>\n`;
  configHistoryRows += `                <td class="operator">${h.operator}</td>\n`;
  configHistoryRows += `                <td class="note">${h.note}</td>\n`;
  configHistoryRows += `                <td class="action"><button class="btn-mini btn-rollback">回滚</button></td>\n`;
  configHistoryRows += `              </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/config-history-rows.html', configHistoryRows);

// --- runs sample rows ---
const runs = [
  {runId:'20260915093001', period:'2026-08', dept:'全院', trigger:'手动', status:'SUCCESS', total:127, ok:85, fail:0, placeholder:42, duration:'3m 24s', operator:'张质控', time:'2026-09-15 09:33:24', msg:'完成'},
  {runId:'20260914020000', period:'2026-08', dept:'ICU一病区', trigger:'定时', status:'SUCCESS', total:127, ok:82, fail:0, placeholder:45, duration:'4m 12s', operator:'系统', time:'2026-09-14 02:04:12', msg:'完成'},
  {runId:'20260913094500', period:'2026-07', dept:'全院', trigger:'手动', status:'SUCCESS', total:127, ok:85, fail:0, placeholder:42, duration:'3m 55s', operator:'李信息', time:'2026-09-13 09:48:55', msg:'完成'},
  {runId:'20260912180000', period:'2026-07', dept:'ICU二病区', trigger:'定时', status:'PARTIAL', total:127, ok:80, fail:3, placeholder:44, duration:'5m 01s', operator:'系统', time:'2026-09-12 18:05:01', msg:'3条指标SQL超时'},
  {runId:'20260911083000', period:'2026-06', dept:'全院', trigger:'手动', status:'SUCCESS', total:127, ok:85, fail:0, placeholder:42, duration:'3m 18s', operator:'王主任', time:'2026-09-11 08:33:18', msg:'完成'}
];
let runsRows = '';
for (const r of runs) {
  const sc = r.status === 'SUCCESS' ? 'status-impl' : (r.status === 'PARTIAL' ? 'status-warning' : 'status-error');
  const sl = r.status === 'SUCCESS' ? '成功' : (r.status === 'PARTIAL' ? '部分成功' : '失败');
  runsRows += `        <tr data-run="${r.runId}">\n`;
  runsRows += `          <td class="run-id">${r.runId}</td>\n`;
  runsRows += `          <td class="period">${r.period}</td>\n`;
  runsRows += `          <td class="dept">${r.dept}</td>\n`;
  runsRows += `          <td class="trigger">${r.trigger}</td>\n`;
  runsRows += `          <td class="status ${sc}">${sl}</td>\n`;
  runsRows += `          <td class="counts">${r.ok}/${r.total}</td>\n`;
  runsRows += `          <td class="placeholder">${r.placeholder}</td>\n`;
  runsRows += `          <td class="duration">${r.duration}</td>\n`;
  runsRows += `          <td class="operator">${r.operator}</td>\n`;
  runsRows += `          <td class="time">${r.time}</td>\n`;
  runsRows += `          <td class="msg">${r.msg}</td>\n`;
  runsRows += `          <td class="action"><button class="btn-mini btn-view">查看</button></td>\n`;
  runsRows += `        </tr>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/runs-rows.html', runsRows);

// --- detail drawer sample for quality_420 ---
const sample = metrics.find(m => m.code === 'quality_420');
let detailContent = '';
if (sample) {
  detailContent += `<div class="drawer-meta">\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">指标编号</span><span class="meta-value">${sample.code}</span></div>\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">所属域</span><span class="meta-value">${sample.domain}</span></div>\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">单位</span><span class="meta-value">${sample.unit}</span></div>\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">事实层</span><span class="meta-value">${sample.fact}</span></div>\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">实现状态</span><span class="meta-value status-impl">已实现</span></div>\n`;
  detailContent += `  <div class="meta-row"><span class="meta-label">口径版本</span><span class="meta-value">v${sample.version}</span></div>\n`;
  detailContent += `</div>\n`;
  detailContent += `<div class="drawer-desc"><strong>口径说明：</strong>${sample.remark}</div>\n`;
  detailContent += `<div class="result-cards">\n`;
  detailContent += `  <div class="result-card"><div class="result-label">指标值</div><div class="result-value">12</div></div>\n`;
  detailContent += `  <div class="result-card"><div class="result-label">分子 / 分母</div><div class="result-value">— / —</div></div>\n`;
  detailContent += `  <div class="result-card"><div class="result-label">计算状态</div><div class="result-value status-impl">OK</div></div>\n`;
  detailContent += `  <div class="result-card"><div class="result-label">数据批次</div><div class="result-value">202608…rk1</div></div>\n`;
  detailContent += `</div>\n`;
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/detail-sample.html', detailContent);

console.log('Generated all data files.');
