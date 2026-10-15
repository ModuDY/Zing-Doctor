# -*- coding: utf-8 -*-
"""列出全部部分实现行。"""
import openpyxl, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
F = 'docs/功能清单验收对照表.xlsx'
wb = openpyxl.load_workbook(F)
ws = wb['验收标注']
for i, row in enumerate(ws.iter_rows(min_row=2), start=2):
    vals = [str(cell.value or '') for cell in row]
    st = vals[4] if len(vals) > 4 else ''
    if st == '部分实现':
        print(i, '|', vals[3][:60])
