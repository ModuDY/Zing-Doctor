const fs = require('fs');
const path = require('path');

const PROJECT_DIR = 'd:/work/ZING/quality-board-redesign';
const PAGES_DIR = path.join(PROJECT_DIR, 'pages');
const DESIGN_PATH = path.join(PROJECT_DIR, 'quality-board-redesign.design');

function getCommonCss() {
  return `/* Quality Board Redesign — Warm Clean White */
/* Brand prefix: qb */

:root {
  /* --- Primary warm orange scale --- */
  --qb-orange-50: #fff7ed;
  --qb-orange-100: #ffedd5;
  --qb-orange-200: #fed7aa;
  --qb-orange-300: #fdba74;
  --qb-orange-400: #fb923c;
  --qb-orange-500: #f97316;
  --qb-orange-600: #ea580c;
  --qb-orange-700: #c2410c;
  --qb-orange-800: #9a3412;

  /* --- Neutral warm gray scale --- */
  --qb-stone-0: #ffffff;
  --qb-stone-50: #fafaf9;
  --qb-stone-100: #f5f5f4;
  --qb-stone-200: #e7e5e4;
  --qb-stone-300: #d6d3d1;
  --qb-stone-400: #a8a29e;
  --qb-stone-500: #78716c;
  --qb-stone-600: #57534e;
  --qb-stone-700: #44403c;
  --qb-stone-800: #292524;
  --qb-stone-900: #1c1917;

  /* --- Semantic state colors --- */
  --qb-state-success: #16a34a;
  --qb-state-success-soft: #dcfce7;
  --qb-state-warning: #d97706;
  --qb-state-warning-soft: #fef3c7;
  --qb-state-error: #dc2626;
  --qb-state-error-soft: #fee2e2;
  --qb-state-info: #0891b2;
  --qb-state-info-soft: #cffafe;

  /* --- Aliases required by head contract --- */
  --qb-background: var(--qb-stone-50);
  --qb-foreground: var(--qb-stone-900);
  --qb-card: var(--qb-stone-0);
  --qb-card-foreground: var(--qb-stone-900);
  --qb-popover: var(--qb-stone-0);
  --qb-popover-foreground: var(--qb-stone-900);
  --qb-primary: var(--qb-orange-600);
  --qb-primary-foreground: #ffffff;
  --qb-secondary: var(--qb-stone-100);
  --qb-secondary-foreground: var(--qb-stone-700);
  --qb-muted: var(--qb-stone-100);
  --qb-muted-foreground: var(--qb-stone-500);
  --qb-border: var(--qb-stone-200);
  --qb-input: var(--qb-stone-200);
  --qb-ring: var(--qb-orange-400);

  /* --- Radius scale (max 16px) --- */
  --qb-radius-small: 4px;
  --qb-radius-medium: 8px;
  --qb-radius-large: 12px;
  --qb-radius-full: 9999px;

  /* --- Shadows (static alpha <= 0.05) --- */
  --qb-shadow-sm: 0 1px 2px rgba(28, 25, 23, 0.04);
  --qb-shadow-md: 0 4px 6px -1px rgba(28, 25, 23, 0.05), 0 2px 4px -2px rgba(28, 25, 23, 0.05);
  --qb-shadow-lg: 0 10px 15px -3px rgba(28, 25, 23, 0.05), 0 4px 6px -4px rgba(28, 25, 23, 0.05);
  --qb-shadow-float: 0 20px 25px -5px rgba(28, 25, 23, 0.08), 0 8px 10px -6px rgba(28, 25, 23, 0.08);
}

:root {
  --qb-font-sans: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --qb-font-mono: "SF Mono", "Monaco", "Inconsolata", "Fira Code", "PingFang SC", monospace;
  --qb-text-xs: 12px;
  --qb-text-sm: 13px;
  --qb-text-base: 14px;
  --qb-text-md: 15px;
  --qb-text-lg: 18px;
  --qb-text-xl: 22px;
  --qb-text-2xl: 28px;
  --qb-leading-tight: 1.25;
  --qb-leading-snug: 1.4;
  --qb-leading-normal: 1.6;
}

html, body {
  font-family: var(--qb-font-sans);
  font-size: var(--qb-text-base);
  line-height: var(--qb-leading-normal);
  color: var(--qb-foreground);
  background: var(--qb-background);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* { box-sizing: border-box; }

.qb-page { padding: 24px; max-width: 1440px; margin: 0 auto; }

.qb-card {
  background: var(--qb-card);
  border: 1px solid var(--qb-border);
  border-radius: var(--qb-radius-large);
  box-shadow: var(--qb-shadow-sm);
}

.qb-card-hover:hover {
  box-shadow: var(--qb-shadow-md);
  transform: translateY(-1px);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.qb-btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--qb-primary);
  color: var(--qb-primary-foreground);
  border: 1px solid var(--qb-primary);
  border-radius: var(--qb-radius-medium);
  font-size: var(--qb-text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.qb-btn-primary:hover { background: var(--qb-orange-700); border-color: var(--qb-orange-700); }

.qb-btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--qb-stone-0);
  color: var(--qb-stone-700);
  border: 1px solid var(--qb-border);
  border-radius: var(--qb-radius-medium);
  font-size: var(--qb-text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.qb-btn-secondary:hover { background: var(--qb-stone-50); border-color: var(--qb-stone-300); }

.qb-btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 14px;
  background: transparent;
  color: var(--qb-stone-600);
  border: 1px solid transparent;
  border-radius: var(--qb-radius-medium);
  font-size: var(--qb-text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.qb-btn-ghost:hover { background: var(--qb-stone-100); color: var(--qb-stone-800); }

.qb-input, .qb-select {
  padding: 7px 12px;
  background: var(--qb-stone-0);
  color: var(--qb-foreground);
  border: 1px solid var(--qb-input);
  border-radius: var(--qb-radius-medium);
  font-size: var(--qb-text-sm);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.qb-input:focus, .qb-select:focus { border-color: var(--qb-ring); box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12); }

.qb-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: var(--qb-radius-full);
  font-size: var(--qb-text-xs);
  font-weight: 500;
  line-height: 1.4;
}

.qb-tag-success { background: var(--qb-state-success-soft); color: var(--qb-state-success); }
.qb-tag-warning { background: var(--qb-state-warning-soft); color: var(--qb-state-warning); }
.qb-tag-error   { background: var(--qb-state-error-soft);   color: var(--qb-state-error);   }
.qb-tag-info    { background: var(--qb-state-info-soft);    color: var(--qb-state-info);    }
.qb-tag-muted   { background: var(--qb-stone-100);          color: var(--qb-stone-500);     }

.qb-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: var(--qb-text-sm); }
.qb-table th, .qb-table td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--qb-border); }
.qb-table th {
  font-weight: 600; color: var(--qb-stone-600); background: var(--qb-stone-50);
  font-size: var(--qb-text-xs); text-transform: uppercase; letter-spacing: 0.02em;
}
.qb-table tbody tr:hover { background: var(--qb-stone-50); }
.qb-table td:last-child, .qb-table th:last-child { text-align: center; }

.qb-link { color: var(--qb-primary); font-weight: 600; text-decoration: none; cursor: pointer; }
.qb-link:hover { text-decoration: underline; }

.qb-mono { font-family: var(--qb-font-mono); font-size: 12px; }
.qb-empty { color: var(--qb-stone-400); }

.qb-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.qb-dot-success { background: var(--qb-state-success); }
.qb-dot-warning { background: var(--qb-state-warning); }
.qb-dot-error   { background: var(--qb-state-error);   }
.qb-dot-info    { background: var(--qb-state-info);    }
.qb-dot-muted   { background: var(--qb-stone-300);     }

/* Header */
.qb-header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 20px; }
.qb-header-title { font-size: var(--qb-text-xl); font-weight: 700; color: var(--qb-stone-900); margin: 0 0 4px; }
.qb-header-sub { font-size: var(--qb-text-sm); color: var(--qb-stone-500); margin: 0; }

/* Filter bar */
.qb-filter-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 14px 18px; margin-bottom: 20px;
}
.qb-filter-left, .qb-filter-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

/* Overview cards */
.qb-overview { padding: 20px; margin-bottom: 20px; }
.qb-overview-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 18px; flex-wrap: wrap; }
.qb-overview-title { font-size: var(--qb-text-lg); font-weight: 700; color: var(--qb-stone-900); margin: 0; }
.qb-overview-period { font-size: var(--qb-text-sm); color: var(--qb-stone-500); font-weight: 400; margin-left: 10px; }
.qb-overview-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.qb-overview-tag { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; background: var(--qb-stone-100); border-radius: var(--qb-radius-full); font-size: var(--qb-text-xs); color: var(--qb-stone-600); }
.qb-overview-tag strong { color: var(--qb-primary); }

.qb-stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.qb-stat-card { padding: 18px; border: 1px solid var(--qb-border); border-radius: var(--qb-radius-large); background: linear-gradient(180deg, #fff 0%, #fafaf9 100%); }
.qb-stat-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.qb-stat-label { font-size: var(--qb-text-sm); color: var(--qb-stone-500); font-weight: 500; }
.qb-stat-icon { width: 32px; height: 32px; border-radius: var(--qb-radius-medium); display: inline-flex; align-items: center; justify-content: center; }
.qb-stat-icon.blue  { color: var(--qb-state-info);  background: var(--qb-state-info-soft); }
.qb-stat-icon.green { color: var(--qb-state-success); background: var(--qb-state-success-soft); }
.qb-stat-icon.orange { color: var(--qb-state-warning); background: var(--qb-state-warning-soft); }
.qb-stat-icon.gray  { color: var(--qb-stone-500);  background: var(--qb-stone-100); }
.qb-stat-value { font-size: var(--qb-text-2xl); font-weight: 700; color: var(--qb-stone-900); line-height: 1.2; font-variant-numeric: tabular-nums; }
.qb-stat-value.green { color: var(--qb-state-success); }
.qb-stat-value.orange { color: var(--qb-state-warning); }
.qb-stat-value.gray { color: var(--qb-stone-500); }
.qb-stat-bar { height: 5px; border-radius: 3px; background: var(--qb-stone-100); margin-top: 12px; overflow: hidden; }
.qb-stat-bar i { display: block; height: 100%; border-radius: 3px; }
.qb-stat-bar i.green { background: var(--qb-state-success); }
.qb-stat-bar i.orange { background: var(--qb-state-warning); }
.qb-stat-bar i.gray { background: var(--qb-stone-300); }
.qb-stat-foot { font-size: var(--qb-text-xs); color: var(--qb-stone-400); margin-top: 8px; }

/* Tabs */
.qb-tabs { display: flex; gap: 4px; padding: 0 20px; border-bottom: 1px solid var(--qb-border); }
.qb-tab {
  padding: 12px 18px; font-size: var(--qb-text-sm); font-weight: 500; color: var(--qb-stone-500);
  background: transparent; border: none; border-bottom: 2px solid transparent; cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.qb-tab:hover { color: var(--qb-stone-700); }
.qb-tab.active { color: var(--qb-primary); border-bottom-color: var(--qb-primary); }

/* Tab panel */
.qb-tab-panel { padding: 20px; }
.qb-tab-note { font-size: var(--qb-text-sm); color: var(--qb-stone-500); margin-bottom: 16px; padding: 12px 16px; background: var(--qb-stone-50); border-radius: var(--qb-radius-medium); border-left: 3px solid var(--qb-orange-400); }

/* Actions */
.qb-actions { display: inline-flex; gap: 8px; }
.qb-btn-text { padding: 4px 10px; font-size: var(--qb-text-xs); font-weight: 500; border-radius: var(--qb-radius-small); border: none; background: transparent; color: var(--qb-stone-600); cursor: pointer; }
.qb-btn-text:hover { background: var(--qb-stone-100); }
.qb-btn-text.primary { color: var(--qb-primary); }
.qb-btn-text.primary:hover { background: var(--qb-orange-50); }
.qb-btn-text.success { color: var(--qb-state-success); }
.qb-btn-text.success:hover { background: var(--qb-state-success-soft); }

/* Responsive */
@media (max-width: 1100px) {
  .qb-stat-grid { grid-template-columns: repeat(2, 1fr); }
  .qb-filter-bar { flex-direction: column; align-items: flex-start; }
}
@media (max-width: 640px) {
  .qb-page { padding: 16px; }
  .qb-stat-grid { grid-template-columns: 1fr; }
  .qb-table th, .qb-table td { padding: 10px; }
}
`;
}

function getCommonScriptsAndFallback() {
  return `    <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4.3.1/dist/index.global.js"></script>
    <script src="https://unpkg.com/lucide@1.8.0/dist/umd/lucide.min.js"></script>
    <style type="text/tailwindcss">
  @theme inline {
    --color-background: var(--qb-background);
    --color-foreground: var(--qb-foreground);
    --color-card: var(--qb-card);
    --color-card-foreground: var(--qb-card-foreground);
    --color-popover: var(--qb-popover);
    --color-popover-foreground: var(--qb-popover-foreground);
    --color-primary: var(--qb-primary);
    --color-primary-foreground: var(--qb-primary-foreground);
    --color-secondary: var(--qb-secondary);
    --color-secondary-foreground: var(--qb-secondary-foreground);
    --color-muted: var(--qb-muted);
    --color-muted-foreground: var(--qb-muted-foreground);
    --color-border: var(--qb-border);
    --color-input: var(--qb-input);
    --color-ring: var(--qb-ring);
    --radius-sm: var(--qb-radius-small);
    --radius-md: var(--qb-radius-medium);
    --radius-lg: var(--qb-radius-large);
  }
  @layer base {
    body { background: var(--qb-background); color: var(--qb-foreground); }
    td, th { @apply break-words; word-break: break-all; word-break: auto-phrase; }
    th { @apply whitespace-nowrap; }
  }
    </style>
    <style id="semantic-token-fallback">
      .bg-background { background-color: var(--qb-background); }
      .text-background { color: var(--qb-background); }
      .border-background { border-color: var(--qb-background); }
      .ring-background { --tw-ring-color: var(--qb-background); }
      .bg-foreground { background-color: var(--qb-foreground); }
      .text-foreground { color: var(--qb-foreground); }
      .border-foreground { border-color: var(--qb-foreground); }
      .ring-foreground { --tw-ring-color: var(--qb-foreground); }
      .bg-card { background-color: var(--qb-card); }
      .text-card { color: var(--qb-card); }
      .border-card { border-color: var(--qb-card); }
      .ring-card { --tw-ring-color: var(--qb-card); }
      .bg-card-foreground { background-color: var(--qb-card-foreground); }
      .text-card-foreground { color: var(--qb-card-foreground); }
      .border-card-foreground { border-color: var(--qb-card-foreground); }
      .ring-card-foreground { --tw-ring-color: var(--qb-card-foreground); }
      .bg-popover { background-color: var(--qb-popover); }
      .text-popover { color: var(--qb-popover); }
      .border-popover { border-color: var(--qb-popover); }
      .ring-popover { --tw-ring-color: var(--qb-popover); }
      .bg-popover-foreground { background-color: var(--qb-popover-foreground); }
      .text-popover-foreground { color: var(--qb-popover-foreground); }
      .border-popover-foreground { border-color: var(--qb-popover-foreground); }
      .ring-popover-foreground { --tw-ring-color: var(--qb-popover-foreground); }
      .bg-primary { background-color: var(--qb-primary); }
      .text-primary { color: var(--qb-primary); }
      .border-primary { border-color: var(--qb-primary); }
      .ring-primary { --tw-ring-color: var(--qb-primary); }
      .bg-primary-foreground { background-color: var(--qb-primary-foreground); }
      .text-primary-foreground { color: var(--qb-primary-foreground); }
      .border-primary-foreground { border-color: var(--qb-primary-foreground); }
      .ring-primary-foreground { --tw-ring-color: var(--qb-primary-foreground); }
      .bg-secondary { background-color: var(--qb-secondary); }
      .text-secondary { color: var(--qb-secondary); }
      .border-secondary { border-color: var(--qb-secondary); }
      .ring-secondary { --tw-ring-color: var(--qb-secondary); }
      .bg-secondary-foreground { background-color: var(--qb-secondary-foreground); }
      .text-secondary-foreground { color: var(--qb-secondary-foreground); }
      .border-secondary-foreground { border-color: var(--qb-secondary-foreground); }
      .ring-secondary-foreground { --tw-ring-color: var(--qb-secondary-foreground); }
      .bg-muted { background-color: var(--qb-muted); }
      .text-muted { color: var(--qb-muted); }
      .border-muted { border-color: var(--qb-muted); }
      .ring-muted { --tw-ring-color: var(--qb-muted); }
      .bg-muted-foreground { background-color: var(--qb-muted-foreground); }
      .text-muted-foreground { color: var(--qb-muted-foreground); }
      .border-muted-foreground { border-color: var(--qb-muted-foreground); }
      .ring-muted-foreground { --tw-ring-color: var(--qb-muted-foreground); }
      .bg-border { background-color: var(--qb-border); }
      .text-border { color: var(--qb-border); }
      .border-border { border-color: var(--qb-border); }
      .ring-border { --tw-ring-color: var(--qb-border); }
      .bg-input { background-color: var(--qb-input); }
      .text-input { color: var(--qb-input); }
      .border-input { border-color: var(--qb-input); }
      .ring-input { --tw-ring-color: var(--qb-input); }
      .bg-ring { background-color: var(--qb-ring); }
      .text-ring { color: var(--qb-ring); }
      .border-ring { border-color: var(--qb-ring); }
      .ring-ring { --tw-ring-color: var(--qb-ring); }
    </style>
    <style>
      .no-scrollbar::-webkit-scrollbar { display: none; }
      .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
      [data-icon] {
        display: inline-flex; align-items: center; justify-content: center;
        -webkit-mask-size: contain; mask-size: contain;
        -webkit-mask-repeat: no-repeat; mask-repeat: no-repeat;
        -webkit-mask-position: center; mask-position: center;
        background-color: currentColor;
      }
    </style>
`;
}

function getHeader() {
  return `        <!-- Header -->
        <header class="qb-header">
          <div>
            <h1 class="qb-header-title">质控指标看板</h1>
            <p class="qb-header-sub">127 条 ICU 质控指标 · 可配置 · 可追溯 · 可解释</p>
          </div>
          <div class="qb-actions">
            <button class="qb-btn-ghost"><i data-lucide="help-circle" class="w-4 h-4"></i>帮助</button>
          </div>
        </header>
`;
}

function getFilterBar() {
  return `        <!-- Filter bar -->
        <section class="qb-card qb-filter-bar">
          <div class="qb-filter-left">
            <select class="qb-select" style="min-width: 90px;">
              <option>按月</option>
              <option>按季</option>
              <option>按年</option>
            </select>
            <input type="month" class="qb-input" value="2026-08" />
            <select class="qb-select" style="min-width: 160px;">
              <option>全院</option>
              <option>ICU 一病区</option>
              <option>ICU 二病区</option>
              <option>综合 ICU</option>
            </select>
            <button class="qb-btn-secondary"><i data-lucide="refresh-cw" class="w-4 h-4"></i>刷新</button>
          </div>
          <div class="qb-filter-right">
            <button class="qb-btn-secondary"><i data-lucide="cpu" class="w-4 h-4"></i>触发计算</button>
            <button class="qb-btn-secondary"><i data-lucide="upload" class="w-4 h-4"></i>同步字典</button>
          </div>
        </section>
`;
}

function buildMonthlyHtml() {
  const extraCss = `/* Monthly specific */
.qb-monthly-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 20px; }
.qb-monthly-card { padding: 18px; border: 1px solid var(--qb-border); border-radius: var(--qb-radius-large); background: linear-gradient(180deg, #fff 0%, #fafaf9 100%); }
.qb-monthly-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.qb-monthly-card-title { font-size: var(--qb-text-sm); color: var(--qb-stone-500); font-weight: 500; }
.qb-monthly-card-value { font-size: var(--qb-text-2xl); font-weight: 700; color: var(--qb-stone-900); font-variant-numeric: tabular-nums; }
.qb-monthly-card-value.green { color: var(--qb-state-success); }
.qb-monthly-card-value.orange { color: var(--qb-state-warning); }
.qb-monthly-trend { font-size: var(--qb-text-xs); color: var(--qb-stone-500); margin-top: 8px; }
.qb-monthly-trend.up { color: var(--qb-state-success); }
.qb-monthly-trend.down { color: var(--qb-state-error); }

@media (max-width: 1100px) {
  .qb-monthly-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .qb-monthly-grid { grid-template-columns: 1fr; }
}
`;
  const body = `        <!-- Overview -->
        <section class="qb-card qb-overview">
          <div class="qb-overview-head">
            <h2 class="qb-overview-title">质控月度汇总<span class="qb-overview-period">2026 年度 · 科室 全院</span></h2>
            <div class="qb-overview-tags">
              <span class="qb-overview-tag"><span class="qb-dot qb-dot-info"></span>12 个月度报告</span>
              <span class="qb-overview-tag"><strong>已归档 8</strong></span>
            </div>
          </div>
          <div class="qb-monthly-grid">
            <div class="qb-monthly-card">
              <div class="qb-monthly-card-head"><span class="qb-monthly-card-title">本年度平均出数率</span><span class="qb-stat-icon green"><i data-lucide="trending-up" class="w-4 h-4"></i></span></div>
              <div class="qb-monthly-card-value green">68.4%</div>
              <div class="qb-monthly-trend up">较上年度 +4.2%</div>
            </div>
            <div class="qb-monthly-card">
              <div class="qb-monthly-card-head"><span class="qb-monthly-card-title">已生成月度报告</span><span class="qb-stat-icon blue"><i data-lucide="file-text" class="w-4 h-4"></i></span></div>
              <div class="qb-monthly-card-value">8</div>
              <div class="qb-monthly-trend">2026-01 至 2026-08</div>
            </div>
            <div class="qb-monthly-card">
              <div class="qb-monthly-card-head"><span class="qb-monthly-card-title">待生成 / 草稿</span><span class="qb-stat-icon orange"><i data-lucide="clock" class="w-4 h-4"></i></span></div>
              <div class="qb-monthly-card-value orange">4</div>
              <div class="qb-monthly-trend down">2026-09 至 2026-12</div>
            </div>
          </div>
        </section>

        <!-- Tabs -->
        <nav class="qb-card qb-tabs" style="border-radius: 12px 12px 0 0; border-bottom: 1px solid var(--qb-border); margin-bottom: 0;">
          <button class="qb-tab" data-dom-id="tab-board">指标看板</button>
          <button class="qb-tab" data-dom-id="tab-coverage">覆盖率报告</button>
          <button class="qb-tab" data-dom-id="tab-facts">事实层</button>
          <button class="qb-tab" data-dom-id="tab-runs">计算批次</button>
          <button class="qb-tab active" data-dom-id="tab-monthly">质控月度汇总</button>
          <button class="qb-tab" data-dom-id="tab-config">质控指标配置</button>
        </nav>

        <!-- Tab panel: Monthly -->
        <section class="qb-card qb-tab-panel" style="border-radius: 0 0 12px 12px; border-top: none;">
          <div class="qb-tab-note">
            月度汇总按「指标域」聚合关键结果，支持导出 Word / PDF 格式质控月报。
          </div>

          <table class="qb-table">
            <thead><tr><th style="width: 100px;">月份</th><th style="width: 120px;">报告状态</th><th style="width: 110px; text-align: right;">出数率</th><th style="width: 110px; text-align: right;">已出数</th><th style="width: 110px; text-align: right;">无数据</th><th style="width: 140px;">主要异常域</th><th style="width: 160px; text-align: center;">生成时间</th><th style="width: 140px; text-align: center;">操作</th></tr></thead>
            <tbody>
              <tr>
                <td>2026-08</td>
                <td style="text-align: center;"><span class="qb-tag qb-tag-success">已归档</span></td>
                <td style="text-align: right;">67.0%</td>
                <td style="text-align: right;">85</td>
                <td style="text-align: right;">12</td>
                <td><span class="qb-empty">无异常</span></td>
                <td style="text-align: center;">2026-08-31 06:05</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">查看</button><button class="qb-btn-text">导出</button></div></td>
              </tr>
              <tr>
                <td>2026-07</td>
                <td style="text-align: center;"><span class="qb-tag qb-tag-success">已归档</span></td>
                <td style="text-align: right;">66.2%</td>
                <td style="text-align: right;">84</td>
                <td style="text-align: right;">14</td>
                <td>DVT 预防</td>
                <td style="text-align: center;">2026-07-31 06:03</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">查看</button><button class="qb-btn-text">导出</button></div></td>
              </tr>
              <tr>
                <td>2026-06</td>
                <td style="text-align: center;"><span class="qb-tag qb-tag-success">已归档</span></td>
                <td style="text-align: right;">65.8%</td>
                <td style="text-align: right;">83</td>
                <td style="text-align: right;">15</td>
                <td>评分_资源</td>
                <td style="text-align: center;">2026-06-30 06:08</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">查看</button><button class="qb-btn-text">导出</button></div></td>
              </tr>
              <tr>
                <td>2026-09</td>
                <td style="text-align: center;"><span class="qb-tag qb-tag-info">草稿</span></td>
                <td style="text-align: right;"><span class="qb-empty">—</span></td>
                <td style="text-align: right;"><span class="qb-empty">—</span></td>
                <td style="text-align: right;"><span class="qb-empty">—</span></td>
                <td><span class="qb-empty">—</span></td>
                <td style="text-align: center;"><span class="qb-empty">—</span></td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text success">生成</button><button class="qb-btn-text" disabled>导出</button></div></td>
              </tr>
            </tbody>
          </table>
        </section>
`;
  return `<!DOCTYPE html>
<html lang="zh-CN" class="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>质控月度汇总 · 质控指标看板</title>
    <style id="theme-vars">
${getCommonCss()}${extraCss}    </style>
${getCommonScriptsAndFallback()}</head>
<body class="min-h-screen font-sans antialiased">
    <main>
      <div class="qb-page">
${getHeader()}${getFilterBar()}${body}      </div>
    </main>
    <script>lucide.createIcons();</script>
</body>
</html>`;
}

function buildConfigHtml() {
  const extraCss = `/* Toggle switch */
.qb-switch {
  position: relative; display: inline-block; width: 40px; height: 22px;
}
.qb-switch input { opacity: 0; width: 0; height: 0; }
.qb-switch-slider {
  position: absolute; cursor: pointer; inset: 0;
  background: var(--qb-stone-300); border-radius: var(--qb-radius-full);
  transition: background 0.2s ease;
}
.qb-switch-slider:before {
  content: ""; position: absolute; height: 16px; width: 16px; left: 3px; bottom: 3px;
  background: white; border-radius: 50%; transition: transform 0.2s ease;
}
.qb-switch input:checked + .qb-switch-slider { background: var(--qb-primary); }
.qb-switch input:checked + .qb-switch-slider:before { transform: translateX(18px); }

/* Config editable */
.qb-config-input {
  width: 80px; padding: 4px 8px; font-size: var(--qb-text-xs);
  border: 1px solid var(--qb-border); border-radius: var(--qb-radius-small);
  outline: none;
}
.qb-config-input:focus { border-color: var(--qb-ring); box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.12); }

/* Frequency tag */
.qb-freq-tag { display: inline-flex; padding: 2px 8px; border-radius: var(--qb-radius-small); font-size: var(--qb-text-xs); background: var(--qb-stone-100); color: var(--qb-stone-600); }
`;
  const body = `        <!-- Overview -->
        <section class="qb-card qb-overview">
          <div class="qb-overview-head">
            <h2 class="qb-overview-title">质控指标配置<span class="qb-overview-period">指标字典 · 127 条</span></h2>
            <div class="qb-overview-tags">
              <span class="qb-overview-tag"><span class="qb-dot qb-dot-info"></span>127 条指标</span>
              <span class="qb-overview-tag"><strong>启用 118</strong></span>
            </div>
          </div>
          <div class="qb-stat-grid">
            <div class="qb-stat-card">
              <div class="qb-stat-head"><span class="qb-stat-label">指标总数</span><span class="qb-stat-icon blue"><i data-lucide="grid-3x3" class="w-4 h-4"></i></span></div>
              <div class="qb-stat-value">127</div>
              <div class="qb-stat-foot">来自质控指标字典</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head"><span class="qb-stat-label">已启用</span><span class="qb-stat-icon green"><i data-lucide="check-circle-2" class="w-4 h-4"></i></span></div>
              <div class="qb-stat-value green">118</div>
              <div class="qb-stat-bar"><i class="green" style="width: 92.9%"></i></div>
              <div class="qb-stat-foot">参与计算与展示</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head"><span class="qb-stat-label">已停用</span><span class="qb-stat-icon orange"><i data-lucide="alert-triangle" class="w-4 h-4"></i></span></div>
              <div class="qb-stat-value orange">9</div>
              <div class="qb-stat-bar"><i class="orange" style="width: 7.1%"></i></div>
              <div class="qb-stat-foot">暂不纳入看板统计</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head"><span class="qb-stat-label">本月变更</span><span class="qb-stat-icon gray"><i data-lucide="edit-3" class="w-4 h-4"></i></span></div>
              <div class="qb-stat-value gray">3</div>
              <div class="qb-stat-foot">阈值 / 责任科室调整</div>
            </div>
          </div>
        </section>

        <!-- Tabs -->
        <nav class="qb-card qb-tabs" style="border-radius: 12px 12px 0 0; border-bottom: 1px solid var(--qb-border); margin-bottom: 0;">
          <button class="qb-tab" data-dom-id="tab-board">指标看板</button>
          <button class="qb-tab" data-dom-id="tab-coverage">覆盖率报告</button>
          <button class="qb-tab" data-dom-id="tab-facts">事实层</button>
          <button class="qb-tab" data-dom-id="tab-runs">计算批次</button>
          <button class="qb-tab" data-dom-id="tab-monthly">质控月度汇总</button>
          <button class="qb-tab active" data-dom-id="tab-config">质控指标配置</button>
        </nav>

        <!-- Tab panel: Config -->
        <section class="qb-card qb-tab-panel" style="border-radius: 0 0 12px 12px; border-top: none;">
          <div class="qb-tab-note">
            修改配置后需点击「保存」生效；停用指标将从看板与月度汇总中隐藏，但不会删除字典记录。
          </div>

          <table class="qb-table">
            <thead><tr><th style="width: 110px;">指标编号</th><th>指标名称</th><th style="width: 120px;">所属域</th><th style="width: 90px; text-align: center;">启用</th><th style="width: 120px;">计算周期</th><th style="width: 100px; text-align: right;">阈值</th><th style="width: 120px;">责任科室</th><th style="width: 140px; text-align: center;">操作</th></tr></thead>
            <tbody>
              <tr>
                <td class="qb-mono">quality_420</td>
                <td>收治的 ARDS 患者数</td>
                <td>ARDS 专项</td>
                <td style="text-align: center;"><label class="qb-switch"><input type="checkbox" checked><span class="qb-switch-slider"></span></label></td>
                <td style="text-align: center;"><span class="qb-freq-tag">按月</span></td>
                <td style="text-align: right;"><input class="qb-config-input" value="—"></td>
                <td>重症医学科</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">编辑</button><button class="qb-btn-text">日志</button></div></td>
              </tr>
              <tr>
                <td class="qb-mono">quality_421</td>
                <td>ARDS 患者机械通气率</td>
                <td>ARDS 专项</td>
                <td style="text-align: center;"><label class="qb-switch"><input type="checkbox" checked><span class="qb-switch-slider"></span></label></td>
                <td style="text-align: center;"><span class="qb-freq-tag">按月</span></td>
                <td style="text-align: right;"><input class="qb-config-input" value="≥ 80%"></td>
                <td>重症医学科</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">编辑</button><button class="qb-btn-text">日志</button></div></td>
              </tr>
              <tr>
                <td class="qb-mono">quality_210</td>
                <td>脓毒症休克 1H 集束化达标率</td>
                <td>脓毒症_感染性休克</td>
                <td style="text-align: center;"><label class="qb-switch"><input type="checkbox" checked><span class="qb-switch-slider"></span></label></td>
                <td style="text-align: center;"><span class="qb-freq-tag">按月</span></td>
                <td style="text-align: right;"><input class="qb-config-input" value="≥ 90%"></td>
                <td>重症医学科</td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">编辑</button><button class="qb-btn-text">日志</button></div></td>
              </tr>
              <tr>
                <td class="qb-mono">quality_900</td>
                <td>保留指标（示例）</td>
                <td>评分_资源</td>
                <td style="text-align: center;"><label class="qb-switch"><input type="checkbox"><span class="qb-switch-slider"></span></label></td>
                <td style="text-align: center;"><span class="qb-freq-tag">按季</span></td>
                <td style="text-align: right;"><input class="qb-config-input" value="—"></td>
                <td><span class="qb-empty">未指定</span></td>
                <td style="text-align: center;"><div class="qb-actions"><button class="qb-btn-text primary">编辑</button><button class="qb-btn-text">日志</button></div></td>
              </tr>
            </tbody>
          </table>
        </section>
`;
  return `<!DOCTYPE html>
<html lang="zh-CN" class="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>质控指标配置 · 质控指标看板</title>
    <style id="theme-vars">
${getCommonCss()}${extraCss}    </style>
${getCommonScriptsAndFallback()}</head>
<body class="min-h-screen font-sans antialiased">
    <main>
      <div class="qb-page">
${getHeader()}${getFilterBar()}${body}      </div>
    </main>
    <script>lucide.createIcons();</script>
</body>
</html>`;
}

function updateExistingPageTabs(htmlPath, activeTab) {
  let content = fs.readFileSync(htmlPath, 'utf-8');

  // Use regex to match the tab nav block regardless of line endings
  const tabNavRegex = /(<nav class="qb-card qb-tabs"[^>]*>[\s\S]*?)(<button class="qb-tab[^"]*" data-dom-id="tab-board">指标看板<\/button>\s*<button class="qb-tab[^"]*" data-dom-id="tab-coverage">覆盖率报告<\/button>\s*<button class="qb-tab[^"]*" data-dom-id="tab-facts">事实层<\/button>\s*<button class="qb-tab[^"]*" data-dom-id="tab-runs">计算批次<\/button>)(\s*<\/nav>)/;

  if (!tabNavRegex.test(content)) {
    console.log('Warning: tab nav not found in ' + htmlPath);
    return;
  }

  const newTabs = `<button class="qb-tab" data-dom-id="tab-board">指标看板</button>
          <button class="qb-tab" data-dom-id="tab-coverage">覆盖率报告</button>
          <button class="qb-tab" data-dom-id="tab-facts">事实层</button>
          <button class="qb-tab" data-dom-id="tab-runs">计算批次</button>
          <button class="qb-tab" data-dom-id="tab-monthly">质控月度汇总</button>
          <button class="qb-tab" data-dom-id="tab-config">质控指标配置</button>`;

  content = content.replace(tabNavRegex, (match, prefix, tabs, suffix) => {
    return prefix + newTabs + suffix;
  });

  const tabs = ['board', 'coverage', 'facts', 'runs', 'monthly', 'config'];
  for (const tab of tabs) {
    content = content.replace(new RegExp(`class="qb-tab active" data-dom-id="tab-${tab}"`, 'g'), `class="qb-tab" data-dom-id="tab-${tab}"`);
  }
  content = content.replace(new RegExp(`class="qb-tab" data-dom-id="tab-${activeTab}"`), `class="qb-tab active" data-dom-id="tab-${activeTab}"`);

  fs.writeFileSync(htmlPath, content, 'utf-8');
  console.log('Updated tabs in ' + path.basename(htmlPath));
}

function main() {
  fs.writeFileSync(path.join(PAGES_DIR, 'quality-monthly.html'), buildMonthlyHtml(), 'utf-8');
  fs.writeFileSync(path.join(PAGES_DIR, 'quality-config.html'), buildConfigHtml(), 'utf-8');
  console.log('Created quality-monthly.html and quality-config.html');

  updateExistingPageTabs(path.join(PAGES_DIR, 'quality-board.html'), 'board');
  updateExistingPageTabs(path.join(PAGES_DIR, 'quality-coverage.html'), 'coverage');
  updateExistingPageTabs(path.join(PAGES_DIR, 'quality-facts.html'), 'facts');
  updateExistingPageTabs(path.join(PAGES_DIR, 'quality-runs.html'), 'runs');
  updateExistingPageTabs(path.join(PAGES_DIR, 'quality-detail.html'), 'board');

  const designRaw = fs.readFileSync(DESIGN_PATH, 'utf-8');
  const design = JSON.parse(designRaw);

  // Check if nodes already exist
  const existingIds = new Set(design.data.map(n => n.id));
  const baseTime = 1789390203000;

  if (!existingIds.has('page-quality-monthly')) {
    design.data.push({
      id: 'page-quality-monthly',
      title: '质控月度汇总',
      type: 'page',
      version: 1,
      createdAt: baseTime,
      canvasData: { x: 0, y: 1492, group: 0 },
      devMetadata: { htmlSrc: 'pages/quality-monthly.html', interactions: [] }
    });
  }
  if (!existingIds.has('page-quality-config')) {
    design.data.push({
      id: 'page-quality-config',
      title: '质控指标配置',
      type: 'page',
      version: 1,
      createdAt: baseTime,
      canvasData: { x: 480, y: 1492, group: 0 },
      devMetadata: { htmlSrc: 'pages/quality-config.html', interactions: [] }
    });
  }

  const allPageIds = design.data.filter(n => n.type === 'page').map(n => n.id);
  const tabMappings = {
    'page-quality-board': 'tab-board',
    'page-quality-coverage': 'tab-coverage',
    'page-quality-facts': 'tab-facts',
    'page-quality-runs': 'tab-runs',
    'page-quality-monthly': 'tab-monthly',
    'page-quality-config': 'tab-config'
  };

  function nodeTitle(pageId) {
    const node = design.data.find(n => n.id === pageId);
    return node ? node.title : pageId;
  }

  for (const node of design.data) {
    if (node.type !== 'page') continue;
    const pageId = node.id;
    const interactions = [];

    if (pageId === 'page-quality-detail') {
      interactions.push(
        { targetPageId: 'page-quality-board', trigger: 'click', selector: "[data-dom-id='drawer-backdrop']", hidden: true, label: '关闭抽屉' },
        { targetPageId: 'page-quality-board', trigger: 'click', selector: "[data-dom-id='drawer-close']", hidden: true, label: '关闭抽屉' }
      );
      node.devMetadata.interactions = interactions;
      continue;
    }

    for (const targetId of allPageIds) {
      if (targetId === pageId || targetId === 'page-quality-detail') continue;
      const tabId = tabMappings[targetId];
      interactions.push({
        targetPageId: targetId,
        trigger: 'click',
        selector: `[data-dom-id='${tabId}']`,
        hidden: true,
        label: `切换到${nodeTitle(targetId)}`
      });
    }

    if (pageId === 'page-quality-board') {
      interactions.push({
        targetPageId: 'page-quality-detail',
        trigger: 'click',
        selector: "[data-dom-id='metric-value-link']",
        hidden: false,
        label: '查看指标详情'
      });
    }

    node.devMetadata.interactions = interactions;
  }

  fs.writeFileSync(DESIGN_PATH, JSON.stringify(design, null, 2), 'utf-8');
  console.log('Updated ' + path.basename(DESIGN_PATH));
}

main();
