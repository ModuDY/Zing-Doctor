# -*- coding: utf-8 -*-
"""重新核对功能清单验收对照表：状态分布、剩余缺口清单。"""
import openpyxl, io, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
wb = openpyxl.load_workbook('docs/功能清单验收对照表.xlsx', read_only=True)

ws = wb['验收标注']
rows = []
header = None
for i, row in enumerate(ws.iter_rows(values_only=True)):
    if i == 0:
        header = row
        continue
    if row and any(v is not None for v in row):
        rows.append(row)

print('总功能点:', len(rows))
from collections import Counter
status_c = Counter(str(r[4]) for r in rows if len(r) > 4)
print('状态分布:', dict(status_c))

# 按模块统计
mod_c = {}
for r in rows:
    if len(r) > 4:
        m, s = str(r[1]), str(r[4])
        mod = mod_c.setdefault(m, Counter())
        mod[s] += 1
print('\n按模块:')
for m, c in mod_c.items():
    total = sum(c.values())
    done = c.get('已实现', 0)
    print(f'  {m}: {done}/{total} 已实现（未完 {total-done}）')

# 未完清单
print('\n=== 未完全落地清单 ===')
for r in rows:
    if len(r) > 4 and str(r[4]) != '已实现':
        pri = str(r[8]) if len(r) > 8 and r[8] else ''
        print(f"[{str(r[4])}|{pri}] {r[1]}/{r[2]}: {str(r[3])[:58]}")
