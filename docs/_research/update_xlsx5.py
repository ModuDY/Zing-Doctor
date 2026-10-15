# -*- coding: utf-8 -*-
"""第五次核对修正：院感分析页 / ARDS 全院筛查 / 镇痛药与评分关联。"""
import openpyxl, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
F = 'docs/功能清单验收对照表.xlsx'
wb = openpyxl.load_workbook(F)
ws = wb['验收标注']

FIXES = {
    154: ('已实现', '2026-10-15落地：ARDS 全院筛查页 /page/ards-screen（sql/54 注册 + /api/ards/hospital-screen），'
          '按周期「在院或已出科」×诊断（ARDS/急性呼吸窘迫综合征）全院清单（不限于俯卧位模块），含在科/已出科/涉及科室汇总、'
          '关键字过滤、患者跳转；柏林定义分级与肺保护通气达标在 ARDS 监测页（氧合指数自动分级 轻度/中度/重度）'),
    158: ('已实现', '2026-10-15落地：镇痛镇静监测页新增「镇痛药使用与评分关联」区块（/api/workbench/patients/{id}/analgesia），'
          '同窗口列出阿片类执行医嘱（芬太尼/瑞芬/舒芬、吗啡、地佐辛等：药品/剂量/泵速/起止）与 NRS/CPOT 评分同轴对照'),
}
for r, (status, ev) in FIXES.items():
    ws.cell(row=r, column=5).value = status
    ws.cell(row=r, column=6).value = ev
    ws.cell(row=r, column=7).value = ''
print('修正行:', sorted(FIXES))
wb.save(F)
print('saved', F)
