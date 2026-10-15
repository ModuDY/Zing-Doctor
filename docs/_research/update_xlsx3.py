# -*- coding: utf-8 -*-
"""第三次核对修正：8 行状态落后于实际交付（关键词未匹配），逐条修正。"""
import openpyxl, re, io, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
F = 'docs/功能清单验收对照表.xlsx'
wb = openpyxl.load_workbook(F)

FIXES = [
    (r'DVT（深静脉血栓）预防指标自动统计', '已实现', '2026-10-15落地：fact_dvt 接医嘱侧（药物经 patient_advice_execute 关联、机械与滤器按 patient_advice_original），按「执行区间∩本周期」过滤，关键词按2024版ICU-07官方口径；患者/科室月度质控与规则层预防率（14÷403）全部出数'),
    (r'气管插管相关指标自动统计', '已实现', '2026-10-15落地：quality_17/18/19 接 patient_tube（非计划拔管/拔管数/48h再插管，2024版ICU-12/13口径），患者月度/科室月度/驾驶舱同步生效'),
    (r'ICU床位使用率指标自动统计', '已实现', '2026-10-15落地：quality_301/304 激活（config_bed 实库验证字段），床位使用率规则 2÷301 自动出数（2024版ICU-01口径）'),
    (r'深静脉血栓（DVT）预防率指标自动统计', '已实现', '2026-10-15落地：同上，规则层 DVT预防率（quality_14÷quality_403×100）自动出数'),
    (r'按年度统计每个月份各类导管的使用情况', '已实现', '2026-10-15落地：导管统计页新增年度视图（/api/tube/yearly，12月×类型趋势图+矩阵表，口径与月度一致）+年度Excel导出'),
    (r'柱状图形式直观展示各类导管的插管量', '已实现', '2026-10-15落地：年度视图含按月堆叠柱状图（echarts，按导管类型分色）'),
    (r'统计年度导管插管总次数及非计划拔管次数', '已实现', '2026-10-15落地：年度视图含全年汇总（yearSum：置管/拔管/非计划/再插管）'),
    (r'肾功能分析功能.*肌酐', '已实现', '已落地：lab-renal 肾功能与电解质趋势页（肌酐/尿素氮/钾钠氯钙镁磷，sql/49 注册），已排除降钙素原等同名异物'),
]

ws = wb['验收标注']
changed = 0
for row in ws.iter_rows(min_row=2):
    point = str(row[3].value or '') if len(row) > 3 else ''
    if not point:
        continue
    for pat, status, ev in FIXES:
        if re.search(pat, point):
            row[4].value = status
            row[5].value = ev
            row[6].value = ''
            changed += 1
            break
print('修正行数:', changed)

wb.save(F)
print('saved', F)
