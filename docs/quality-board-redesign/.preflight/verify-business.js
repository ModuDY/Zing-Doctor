const fs = require('fs');
const board = fs.readFileSync('d:/work/ZING/quality-board-redesign/pages/quality-board.html', 'utf8');
const monthly = fs.readFileSync('d:/work/ZING/quality-board-redesign/pages/quality-monthly.html', 'utf8');
const boardRows = (board.match(/<tr data-rule=/g) || []).length;
const monthlyRows = (monthly.match(/<tr data-rule=/g) || []).length;
console.log('board business rows:', boardRows);
console.log('monthly business rows:', monthlyRows);
console.log('board has domain-group:', board.includes('domain-group'));
console.log('monthly has data-code quality_:', monthly.includes('data-code="quality_'));
