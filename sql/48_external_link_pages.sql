-- =====================================================================
-- 外链页面批量补注册（达梦 DM8）
--
-- 左侧菜单近期新增的功能页此前未在 sys_page_config 登记：外链
-- /entry/{pageCode} 会判「未注册的页面」404，参数设置页的「外链页面」
-- 一览也看不到它们。本脚本一次性补齐。
--
-- /entry/{pageCode} 只放行 sys_page_config 中已注册且启用的页面；
-- 幂等：按 page_code 删除后重建，可重复执行。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" IN (
    'history-patients', 'vital-signs', 'fluid-balance',
    'lab-liver', 'lab-coag', 'department-cockpit',
    'abx-ddd-patients', 'abx-mdro-patients',
    'sedation-monitor', 'neuro-monitor', 'nutrition-monitor',
    'tube-stats');

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('history-patients',   '历史患者',             '/page/history-patients',   '历史在科 / 已出科患者检索', 1),
    ('vital-signs',        '生命体征趋势',         '/page/vital-signs',        '单患者生命体征多指标同时间轴叠加，时间窗 6h / 24h / 3天 / 7天', 1),
    ('fluid-balance',      '体液平衡分析',         '/page/fluid-balance',      '单患者体液平衡：入量 / 出量 / 净平衡按时间窗汇总与明细', 1),
    ('lab-liver',          '肝功能趋势',           '/page/lab-liver',          '单患者肝功能检验趋势：ALT / AST / 胆红素 / 白蛋白等按参考范围展示', 1),
    ('lab-coag',           '凝血功能趋势',         '/page/lab-coag',           '单患者凝血检验趋势：PLT / PT / INR / APTT / Fib / D-二聚体', 1),
    ('department-cockpit', '科室驾驶舱',           '/page/department-cockpit', '科室运行总览：患者负荷、器官支持、床位与设备、评分分布、本月质控', 1),
    ('abx-ddd-patients',   '抗菌药物使用患者明细', '/page/abx-ddd-patients',   '抗菌药物使用强度分析的患者明细清单', 1),
    ('abx-mdro-patients',  '细菌培养患者明细',     '/page/abx-mdro-patients',  '细菌培养检出监测的患者明细清单', 1),
    ('sedation-monitor',   '镇痛镇静监测',         '/page/sedation-monitor',   '单患者镇痛镇静监测：RASS / CPOT 趋势与用药', 1),
    ('neuro-monitor',      '神经功能监测',         '/page/neuro-monitor',      '单患者神经功能监测趋势', 1),
    ('nutrition-monitor',  '营养支持分析',         '/page/nutrition-monitor',  '单患者营养支持：热量 / 蛋白目标达成与营养制剂', 1),
    ('tube-stats',         '导管统计分析',         '/page/tube-stats',         '三管（CVC / 导尿 / 气道）置管、拔管、非计划拔管与留置日统计', 1);
