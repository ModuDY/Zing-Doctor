const fs = require('fs');
function countRows(file, attr) {
  const html = fs.readFileSync(file, 'utf8');
  const re = new RegExp(`<tr[^>]*${attr}="`, 'g');
  const matches = html.match(re) || [];
  return matches.length;
}
const board = countRows('d:/work/ZING/quality-board-redesign/pages/quality-board.html', 'data-code');
const monthly = countRows('d:/work/ZING/quality-board-redesign/pages/quality-monthly.html', 'data-code');
const config = countRows('d:/work/ZING/quality-board-redesign/pages/quality-config.html', 'data-code');
const coverage = countRows('d:/work/ZING/quality-board-redesign/pages/quality-coverage.html', 'data-code');
const facts = countRows('d:/work/ZING/quality-board-redesign/pages/quality-facts.html', 'data-fact');
const runs = countRows('d:/work/ZING/quality-board-redesign/pages/quality-runs.html', 'data-run');
console.log('board metrics:', board);
console.log('monthly metrics:', monthly);
console.log('config metrics:', config);
console.log('coverage metrics:', coverage);
console.log('facts:', facts);
console.log('runs:', runs);
