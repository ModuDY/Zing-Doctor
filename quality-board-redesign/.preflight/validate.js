const fs = require('fs');
const path = require('path');

const projectDir = 'd:\\work\\ZING\\quality-board-redesign';
const designPath = path.join(projectDir, 'quality-board-redesign.design');

const results = {
  designFileValid: false,
  pageNodes: 0,
  htmlFilesExist: true,
  missingHtmlFiles: [],
  interactionsValid: true,
  missingSelectors: [],
  duplicateIds: [],
  cssFileExists: false,
  errors: []
};

// Check CSS file
const cssPath = path.join(projectDir, 'colors_and_type.css');
results.cssFileExists = fs.existsSync(cssPath);

// Parse design file
let design;
try {
  const raw = fs.readFileSync(designPath, 'utf-8');
  design = JSON.parse(raw);
  results.designFileValid = true;
} catch (e) {
  results.errors.push(`Design file parse error: ${e.message}`);
}

if (design && Array.isArray(design.data)) {
  results.pageNodes = design.data.length;

  // Check for duplicate IDs
  const ids = design.data.map(n => n.id);
  const seen = new Set();
  ids.forEach(id => {
    if (seen.has(id)) results.duplicateIds.push(id);
    seen.add(id);
  });

  for (const node of design.data) {
    if (node.type !== 'page') continue;
    const htmlSrc = node.devMetadata?.htmlSrc;
    if (!htmlSrc) {
      results.errors.push(`Page ${node.id} missing htmlSrc`);
      continue;
    }
    const htmlPath = path.join(projectDir, htmlSrc);
    if (!fs.existsSync(htmlPath)) {
      results.htmlFilesExist = false;
      results.missingHtmlFiles.push(htmlSrc);
      continue;
    }

    const html = fs.readFileSync(htmlPath, 'utf-8');
    const interactions = node.devMetadata?.interactions || [];
    for (const interaction of interactions) {
      const selector = interaction.selector;
      if (!selector) {
        results.interactionsValid = false;
        results.missingSelectors.push({ page: node.id, reason: 'missing selector' });
        continue;
      }
      // Simple selector check for data-dom-id
      const match = selector.match(/data-dom-id='([^']+)'/);
      if (match) {
        const domId = match[1];
        const found = html.includes(`data-dom-id="${domId}"`) || html.includes(`data-dom-id='${domId}'`);
        if (!found) {
          results.interactionsValid = false;
          results.missingSelectors.push({ page: node.id, selector, domId });
        }
      }
    }
  }
}

const passed = results.designFileValid && results.htmlFilesExist && results.interactionsValid && results.cssFileExists && results.duplicateIds.length === 0;

console.log(JSON.stringify({ passed, ...results }, null, 2));
process.exit(passed ? 0 : 1);
