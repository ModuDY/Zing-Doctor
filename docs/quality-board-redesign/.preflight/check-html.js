const fs = require('fs');
const path = require('path');
const pagesDir = 'd:/work/ZING/quality-board-redesign/pages';
const files = fs.readdirSync(pagesDir).filter(f => f.endsWith('.html'));
for (const f of files) {
  const p = path.join(pagesDir, f);
  const buf = fs.readFileSync(p);
  const first = buf.slice(0, 20).toString();
  const hasBom = buf[0] === 0xEF && buf[1] === 0xBB && buf[2] === 0xBF;
  const startsOk = first.startsWith('<!DOCTYPE') || first.startsWith('<html') || first.startsWith('<?xml');
  console.log(f, 'size', buf.length, 'bom', hasBom, 'startsOk', startsOk, 'first:', JSON.stringify(first));
}
