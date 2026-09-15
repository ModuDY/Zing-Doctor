import json
import re
from pathlib import Path

WORK = Path('d:/work/ZING/quality-board-redesign')
PREFLIGHT = WORK / '.preflight'
PAGE = WORK / 'pages' / 'quality-monthly.html'

html = PAGE.read_text(encoding='utf-8')
rows_html = (PREFLIGHT / 'monthly-rows-127.html').read_text(encoding='utf-8')
options_html = (PREFLIGHT / 'trend-options-127.html').read_text(encoding='utf-8')
metrics = json.loads((PREFLIGHT / 'metrics-127.json').read_text(encoding='utf-8'))

# 1. Insert summary cards after filter bar section (before annual trend section)
summary_section = '''        <!-- Monthly summary overview -->
        <section class="qb-card qb-overview" data-dom-id="summary-overview">
          <div class="qb-overview-head">
            <div style="display:flex;align-items:baseline;gap:12px;">
              <h2 class="qb-overview-title">月度汇总概览</h2>
              <span class="qb-overview-period">2026 年度 · 全院</span>
            </div>
            <div class="qb-overview-tags">
              <span class="qb-overview-tag">原子指标 <strong>127</strong></span>
              <span class="qb-overview-tag">覆盖域 <strong>8</strong></span>
            </div>
          </div>
          <div class="qb-stat-grid">
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">汇总指标数</span>
                <span class="qb-stat-icon orange"><i data-lucide="list" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">127</div>
              <div class="qb-stat-bar"><i class="orange" style="width:100%;"></i></div>
              <div class="qb-stat-foot">本年度全部原子指标</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">本年度有数据</span>
                <span class="qb-stat-icon green"><i data-lucide="database" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value green">85</div>
              <div class="qb-stat-bar"><i class="green" style="width:66.9%;"></i></div>
              <div class="qb-stat-foot">已接入或已汇总指标</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">覆盖域</span>
                <span class="qb-stat-icon blue"><i data-lucide="layout-grid" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">8</div>
              <div class="qb-stat-bar"><i class="gray" style="width:100%;"></i></div>
              <div class="qb-stat-foot">患者流转 / 脓毒症 / 抗菌药 / DVT / 评分 / 评估 / ARDS / 导管</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">当前展示</span>
                <span class="qb-stat-icon gray"><i data-lucide="eye" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">127</div>
              <div class="qb-stat-bar"><i class="orange" style="width:100%;"></i></div>
              <div class="qb-stat-foot">宽表已展开全部指标</div>
            </div>
          </div>
        </section>
'''

# Insert after the filter bar closing section tag
html = html.replace(
    '        </section>\n        <!-- Annual trend (collapsible drawer) -->',
    '        </section>\n' + summary_section + '        <!-- Annual trend (collapsible drawer) -->'
)

# 2. Replace trend select options
html = re.sub(
    r'(<select class="qb-select" data-dom-id="trend-select"[^>]*>).*?(</select>)',
    r'\1\n' + options_html.strip() + r'\n              \2',
    html,
    flags=re.DOTALL
)

# 3. Replace table header and tbody
new_thead = '''            <thead><tr>
              <th style="min-width: 120px; position: sticky; left: 0; z-index: 2; background: var(--qb-stone-50);">指标编码</th>
              <th style="min-width: 260px; position: sticky; left: 120px; z-index: 2; background: var(--qb-stone-50);">指标名称</th>
              <th style="width: 130px; position: sticky; left: 380px; z-index: 2; background: var(--qb-stone-50);">域</th>
              <th style="width: 60px; text-align: center;">单位</th>
              <th style="width: 84px; text-align: right;">1月</th><th style="width: 84px; text-align: right;">2月</th><th style="width: 84px; text-align: right;">3月</th><th style="width: 84px; text-align: right;">4月</th><th style="width: 84px; text-align: right;">5月</th><th style="width: 84px; text-align: right;">6月</th>
              <th style="width: 84px; text-align: right;">7月</th><th style="width: 84px; text-align: right;">8月</th><th style="width: 84px; text-align: right;">9月</th><th style="width: 84px; text-align: right;">10月</th><th style="width: 84px; text-align: right;">11月</th><th style="width: 84px; text-align: right;">12月</th>
              <th style="width: 86px; text-align: right;">Q1</th><th style="width: 86px; text-align: right;">Q2</th><th style="width: 86px; text-align: right;">Q3</th><th style="width: 86px; text-align: right;">Q4</th>
              <th style="width: 100px; text-align: right;">全年合计</th>
              <th style="width: 86px; text-align: right;">月均</th>
              <th style="width: 76px; text-align: center;">最高月</th>
              <th style="width: 76px; text-align: center;">最低月</th>
            </tr></thead>'''

html = re.sub(
    r'<table class="qb-table qb-wide-table" style="min-width: 1900px;">\s*<thead>.*?</thead>\s*<tbody>.*?</tbody>\s*</table>',
    '<table class="qb-table qb-wide-table" style="min-width: 2400px;">\n' + new_thead + '\n            <tbody>\n' + rows_html.strip() + '\n            </tbody>\n          </table>',
    html,
    flags=re.DOTALL
)

# 4. Update trend chart dataset for the 7 selected options
trend_codes = []
for line in options_html.strip().split('\n'):
    m = re.search(r'<option value="([^"]+)"', line)
    if m:
        trend_codes.append(m.group(1))

# Extract month values from rows for each trend code
def extract_month_values(code):
    m = re.search(
        rf'<tr data-code="{re.escape(code)}"[^>]*>.*?</tr>',
        rows_html,
        flags=re.DOTALL
    )
    if not m:
        return None
    values = re.findall(r'<td class="month[^"]*" data-month="\d+">([^<]+)</td>', m.group(0))
    if len(values) != 12:
        return None
    return values

dataset = {}
unit_map = {}
for line in options_html.strip().split('\n'):
    m = re.search(r'<option value="([^"]+)"[^>]*data-unit="([^"]*)"[^>]*>(.*?)</option>', line)
    if m:
        code, unit, label = m.groups()
        unit_map[code] = unit
        vals = extract_month_values(code)
        if vals:
            dataset[code] = vals

# Build new dataset JS
new_dataset_lines = []
for code in trend_codes:
    vals = dataset.get(code)
    unit = unit_map.get(code, '')
    if vals:
        new_dataset_lines.append(f'            {code}: [{", ".join(vals)}], // {unit}')
    else:
        new_dataset_lines.append(f'            {code}: [0,0,0,0,0,0,0,0,0,0,0,0], // {unit}')
new_dataset_js = '\n'.join(new_dataset_lines)

# Replace dataset object in JS
html = re.sub(
    r'const dataset = \{[^}]+\};',
    'const dataset = {\n' + new_dataset_js + '\n          };',
    html,
    flags=re.DOTALL
)

# Update table min-width inline style if not already changed
html = html.replace('min-width: 1900px;', 'min-width: 2400px;')

PAGE.write_text(html, encoding='utf-8')
print(f'Updated: {PAGE}')
print(f'Rows found in monthly-rows: {rows_html.count("<tr data-code=")}')
print(f'Trend codes: {trend_codes}')
print(f'Dataset entries: {list(dataset.keys())}')
