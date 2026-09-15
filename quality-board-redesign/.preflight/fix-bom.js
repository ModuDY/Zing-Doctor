const fs = require('fs');
const path = 'd:/work/ZING/quality-board-redesign/pages/quality-config.html';
let buf = fs.readFileSync(path);
if (buf[0] === 0xEF && buf[1] === 0xBB && buf[2] === 0xBF) {
  buf = buf.slice(3);
  console.log('BOM removed');
} else if (buf[0] !== 0x3C) {
  // find first '<'
  let i = 0;
  while (i < buf.length && buf[i] !== 0x3C) i++;
  if (i < buf.length) {
    buf = buf.slice(i);
    console.log('Removed ' + i + ' leading garbage bytes');
  }
}
fs.writeFileSync(path, buf);
console.log('First 20 chars:', buf.slice(0, 20).toString());
