#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update quality-config.html to the latest 3-tab visual config design."""

from pathlib import Path
import re

BASE = Path(r'd:\work\ZING\quality-board-redesign')
PAGE = BASE / 'pages' / 'quality-config.html'
PREFLIGHT = BASE / '.preflight'

html = PAGE.read_text(encoding='utf-8')

metric_rows = (PREFLIGHT / 'config-metric-rows-127.html').read_text(encoding='utf-8')
fact_rows = (PREFLIGHT / 'config-fact-rows.html').read_text(encoding='utf-8')
history_rows = (PREFLIGHT / 'config-history-rows.html').read_text(encoding='utf-8')

# Helper to trim leading/trailing blank lines while preserving inner indentation
def trim_block(s):
    return s.strip('\n')

metric_rows = trim_block(metric_rows)
fact_rows = trim_block(fact_rows)
history_rows = trim_block(history_rows)

# 1. Replace the existing data-source banner text with the new top banner text.
html = re.sub(
    r'(<div class="qb-banner qb-banner-info">\s*<span class="qb-dot qb-dot-info"></span>\s*<span>)[^<]*(</span>\s*</div>)',
    r'\1配置真源：数据库 ｜ 保存即生效 ｜ 操作人：张质控\2',
    html,
    flags=re.DOTALL
)

# 2. Update metric count.
html = re.sub(
    r'(<span data-dom-id="config-metric-count"[^>]*>)共 \d+ 条(</span>)',
    r'\1共 127 条\2',
    html
)

# 3. Replace domain filter options (8 domains).
new_domain_options = '''<option value="">全部域</option>
                <option>患者流转</option>
                <option>脓毒症_感染性休克</option>
                <option>抗菌药送检</option>
                <option>DVT预防</option>
                <option>评分_资源</option>
                <option>导管_管路_院感</option>
                <option>评估依从</option>
                <option>ARDS专项</option>'''
html = re.sub(
    r'(<select class="qb-select" data-dom-id="config-filter-domain"[^>]*>).*?(</select>)',
    r'\1\n                ' + new_domain_options.replace('\n', '\n                ') + '\n              \2',
    html,
    flags=re.DOTALL
)

# 4. Replace status filter options.
new_status_options = '''<option value="">全部状态</option>
                <option value="IMPL">已实现</option>
                <option value="PENDING_SOURCE">待接数据源</option>
                <option value="PLACEHOLDER">口径待定</option>
                <option value="MANUAL">人工录入</option>'''
html = re.sub(
    r'(<select class="qb-select" data-dom-id="config-filter-status"[^>]*>).*?(</select>)',
    r'\1\n                ' + new_status_options.replace('\n', '\n                ') + '\n              \2',
    html,
    flags=re.DOTALL
)

# 5. Replace metric table body, keeping the existing table tag and data-dom-id.
#    Also update thead to match the new row semantics.
metric_thead = '''<thead><tr><th style="width: 110px;">编号</th><th>指标名称</th><th style="width: 130px;">所属域</th><th style="width: 100px;">事实层</th><th style="width: 90px; text-align: center;">计算类型</th><th style="width: 90px; text-align: center;">状态</th><th style="width: 80px; text-align: center;">版本</th><th style="width: 130px; text-align: center;">操作</th></tr></thead>'''
html = re.sub(
    r'(<table class="qb-table" data-dom-id="config-table-metric">\s*)<thead>.*?</thead>\s*<tbody>.*?</tbody>(\s*</table>)',
    r'\1' + metric_thead + '\n              <tbody>\n' + metric_rows + '\n              </tbody>\2',
    html,
    flags=re.DOTALL
)

# 6. Replace fact table head + body.
fact_thead = '''<thead><tr><th style="width: 170px;">事实层</th><th style="width: 120px;">所属域</th><th style="width: 90px; text-align: center;">状态</th><th style="width: 150px;">来源</th><th style="width: 130px; text-align: center;">操作</th></tr></thead>'''
html = re.sub(
    r'(<div data-dom-id="config-panel-fact"[^>]*>.*?<table class="qb-table">\s*)<thead>.*?</thead>\s*<tbody>.*?</tbody>(\s*</table>)',
    r'\1' + fact_thead + '\n              <tbody>\n' + fact_rows + '\n              </tbody>\2',
    html,
    flags=re.DOTALL
)

# 7. Replace history table head + body.
history_thead = '''<thead><tr><th style="width: 170px;">时间</th><th style="width: 80px;">类型</th><th style="width: 180px;">对象</th><th style="width: 100px;">变更</th><th style="width: 80px; text-align: center;">版本</th><th style="width: 120px;">操作人</th><th>备注</th><th style="width: 130px; text-align: center;">操作</th></tr></thead>'''
html = re.sub(
    r'(<div data-dom-id="config-panel-history"[^>]*>.*?<table class="qb-table">\s*)<thead>.*?</thead>\s*<tbody>.*?</tbody>(\s*</table>)',
    r'\1' + history_thead + '\n              <tbody>\n' + history_rows + '\n              </tbody>\2',
    html,
    flags=re.DOTALL
)

PAGE.write_text(html, encoding='utf-8')
print('quality-config.html updated successfully.')
