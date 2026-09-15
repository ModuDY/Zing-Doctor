const fs = require('fs');
const raw = fs.readFileSync('c:\\Users\\11515\\.trae-cn\\attachments\\6aa7ec24bb782a3574629440\\be337828-8b31-4581-b21c-3cea4e7f8d54_dd410113-0c51-4e13-96ab-73a2452578d0_INSERT INT....txt', 'utf-8');
const rows = [];
const re = /INSERT INTO[\s\S]*?VALUES \((.*?)\);/g;
let m;
while ((m = re.exec(raw)) !== null) {
  const vals = m[1];
  let cur = '';
  let inQuote = false;
  let quoteChar = '';
  for (let i = 0; i < vals.length; i++) {
    const c = vals[i];
    if (!inQuote && (c === "'" || c === '"')) { inQuote = true; quoteChar = c; cur += c; }
    else if (inQuote && c === quoteChar) {
      if (vals[i + 1] === quoteChar) { cur += c + quoteChar; i++; }
      else { cur += c; inQuote = false; }
    } else { cur += c; }
  }
  const tokens = [];
  let token = '';
  let depth = 0;
  inQuote = false;
  quoteChar = '';
  for (let i = 0; i < cur.length; i++) {
    const c = cur[i];
    if (!inQuote && (c === "'" || c === '"')) { inQuote = true; quoteChar = c; token += c; }
    else if (inQuote && c === quoteChar) {
      if (cur[i + 1] === quoteChar) { token += c + quoteChar; i++; }
      else { token += c; inQuote = false; }
    }
    else if (!inQuote && c === '(') { depth++; token += c; }
    else if (!inQuote && c === ')') { depth--; token += c; }
    else if (!inQuote && c === ',' && depth === 0) { tokens.push(token.trim()); token = ''; }
    else { token += c; }
  }
  if (token.trim()) tokens.push(token.trim());
  function stripQuotes(s) {
    if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith('"') && s.endsWith('"'))) return s.slice(1, -1).replace(/''/g, "'").replace(/""/g, '"');
    if (s === 'NULL') return null;
    return s;
  }
  const cols = tokens.map(stripQuotes);
  rows.push({ id: cols[0], count_name: cols[1], quality_type_code: cols[2], is_visible: cols[3], is_show_page: cols[4], sort_no: cols[5], remark: cols[6], numerator_code: cols[7], numerator_unit: cols[8], numerator_precision: cols[9], numerator_rate: cols[10], denominator_code: cols[11], denominator_unit: cols[12], denominator_precision: cols[13], denominator_rate: cols[14], percent_unit: cols[15], percent_rate: cols[16], percent_precision: cols[17], depart_code: cols[18], status: cols[19], del_flag: cols[20] });
}
console.log('Total rows:', rows.length);
console.log('is_show_page=1:', rows.filter(r => r.is_show_page === '1').length);
console.log('is_show_page=0:', rows.filter(r => r.is_show_page === '0').length);
const showRows = rows.filter(r => r.is_show_page === '1');
showRows.forEach((r, i) => console.log(`${i + 1}. ${r.count_name} | ${r.numerator_code} / ${r.denominator_code} | ${r.percent_unit} | prec=${r.percent_precision}`));
fs.writeFileSync('d:\\work\\ZING\\quality-board-redesign\\.preflight\\metrics.json', JSON.stringify(rows, null, 2));
