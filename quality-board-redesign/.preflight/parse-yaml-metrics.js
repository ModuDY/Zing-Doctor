const fs = require('fs');
const path = require('path');

const base = 'd:/work/ZING/src/main/resources/quality/metrics';
const files = [
  '01_patient_flow.yaml',
  '02_sepsis.yaml',
  '03_abx_culture.yaml',
  '04_dvt.yaml',
  '05_score_resource.yaml',
  '06_assessment.yaml',
  '07_device.yaml',
  '08_ards.yaml'
];

const domainOrder = ['患者流转','脓毒症_感染性休克','抗菌药送检','DVT预防','评分_资源','评估依从','导管_管路_院感','ARDS专项'];

function parseYamlLine(line) {
  const m = line.match(/^-\s*\{(.+)\}\s*$/);
  if (!m) return null;
  const inner = m[1];
  const obj = {};
  const tokens = [];
  let cur = '';
  let inQuote = false;
  let quoteChar = '';
  for (let i = 0; i < inner.length; i++) {
    const ch = inner[i];
    if (!inQuote && (ch === '"' || ch === "'")) {
      inQuote = true;
      quoteChar = ch;
      cur += ch;
    } else if (inQuote && ch === quoteChar) {
      inQuote = false;
      cur += ch;
      quoteChar = '';
    } else if (!inQuote && ch === ',') {
      tokens.push(cur.trim());
      cur = '';
    } else {
      cur += ch;
    }
  }
  if (cur.trim()) tokens.push(cur.trim());

  for (const t of tokens) {
    const colon = t.indexOf(':');
    if (colon === -1) continue;
    const key = t.slice(0, colon).trim();
    let val = t.slice(colon + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (val === 'true') val = true;
    else if (val === 'false') val = false;
    else if (/^\d+$/.test(val)) val = parseInt(val, 10);
    else if (val.startsWith('[') && val.endsWith(']')) {
      val = val.slice(1, -1).split(',').map(s => s.trim()).filter(Boolean);
    }
    obj[key] = val;
  }
  return obj;
}

let metrics = [];
for (const f of files) {
  const content = fs.readFileSync(path.join(base, f), 'utf8');
  const lines = content.split('\n');
  for (const line of lines) {
    const m = parseYamlLine(line);
    if (m && m.code) {
      if (!m.implStatus) m.implStatus = 'IMPL';
      if (!m.valueType) m.valueType = 'COUNT';
      if (!m.unit) m.unit = '';
      if (!m.fact) m.fact = '';
      if (!m.version) m.version = 1;
      if (!m.sortNo) m.sortNo = 0;
      if (!m.remark) m.remark = '';
      metrics.push(m);
    }
  }
}

metrics.sort((a, b) => a.sortNo - b.sortNo);

const byDomain = {};
const byStatus = {};
for (const m of metrics) {
  byDomain[m.domain] = (byDomain[m.domain] || 0) + 1;
  byStatus[m.implStatus] = (byStatus[m.implStatus] || 0) + 1;
}

console.log('Total metrics:', metrics.length);
console.log('By domain:', byDomain);
console.log('By status:', byStatus);

fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/metrics-127.json', JSON.stringify(metrics, null, 2));

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

let boardRows = '';
for (const domain of domainOrder) {
  const list = grouped[domain];
  boardRows += `<details class="domain-group" open data-domain="${domain}">\n`;
  boardRows += `  <summary class="domain-summary"><span class="domain-name">▼ ${domain}</span><span class="domain-meta">${list.length} 条 · 已出数 <span class="domain-ok">0</span></span></summary>\n`;
  boardRows += `  <div class="table-wrap"><table class="metric-table">\n`;
  boardRows += `    <thead><tr><th>指标编号</th><th>指标名称</th><th>单位</th><th>本期值</th><th>分子 / 分母</th><th>计算状态</th><th>实现状态</th><th>操作</th></tr></thead>\n`;
  boardRows += `    <tbody>\n`;
  for (const m of list) {
    const sc = statusClass(m.implStatus);
    const sl = statusLabel(m.implStatus);
    const cl = calcLabel(m.implStatus);
    const value = m.implStatus === 'IMPL' ? `<a href="#" class="metric-value" data-code="${m.code}">12</a>` : '—';
    const numDem = m.valueType === 'RATE' ? `<span class="numdem">8 / 24</span>` : '—';
    const action = m.implStatus === 'MANUAL' ? `<button class="btn-mini btn-manual">录入</button>` : `<button class="btn-mini btn-lineage">口径血缘</button>`;
    boardRows += `      <tr data-code="${m.code}" data-status="${m.implStatus}">\n`;
    boardRows += `        <td class="code">${m.code}</td>\n`;
    boardRows += `        <td class="name" title="${m.remark}">${m.name}</td>\n`;
    boardRows += `        <td class="unit">${m.unit}</td>\n`;
    boardRows += `        <td class="value">${value}</td>\n`;
    boardRows += `        <td class="fraction">${numDem}</td>\n`;
    boardRows += `        <td class="calc ${sc}">${cl}</td>\n`;
    boardRows += `        <td class="impl ${sc}">${sl}</td>\n`;
    boardRows += `        <td class="action">${action}</td>\n`;
    boardRows += `      </tr>\n`;
  }
  boardRows += `    </tbody>\n  </table></div>\n</details>\n`;
}

fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/board-rows-127.html', boardRows);

let coverageRows = '';
for (const domain of domainOrder) {
  const list = grouped[domain];
  const impl = list.filter(x => x.implStatus === 'IMPL').length;
  const pending = list.filter(x => x.implStatus === 'PENDING_SOURCE').length;
  const placeholder = list.filter(x => x.implStatus === 'PLACEHOLDER').length;
  const manual = list.filter(x => x.implStatus === 'MANUAL').length;
  coverageRows += `<tr class="domain-row" data-domain="${domain}"><td class="domain-name">${domain}</td><td>${list.length}</td><td>${impl}</td><td>${pending}</td><td>${placeholder}</td><td>${manual}</td></tr>\n`;
  for (const m of list) {
    const sc = statusClass(m.implStatus);
    const sl = statusLabel(m.implStatus);
    coverageRows += `<tr data-domain="${domain}" data-status="${m.implStatus}"><td class="code">${m.code}</td><td class="name" title="${m.remark}">${m.name}</td><td>${m.fact || '—'}</td><td>${m.valueType}</td><td class="${sc}">${sl}</td><td>v${m.version}</td></tr>\n`;
  }
}
fs.writeFileSync('d:/work/ZING/quality-board-redesign/.preflight/coverage-rows-127.html', coverageRows);

console.log('Generated metrics-127.json, board-rows-127.html, coverage-rows-127.html');
