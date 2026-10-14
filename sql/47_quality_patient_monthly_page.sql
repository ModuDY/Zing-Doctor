-- =====================================================================
-- 患者月度质控指标页注册（达梦 DM8）
--
-- 对应前端路由 /page/quality-patient-monthly 与接口 GET /api/quality/patient/metrics
-- （患者 → 指标 方向：某位患者某月触发了哪些质控项）。
--
-- /entry/quality-patient-monthly 只允许访问 sys_page_config 中已注册且启用的页面；
-- 前端路由已存在，老库升级时若缺少本注册，外链会被判定为“未注册的页面”。
-- 幂等：按 page_code 删除后重建，可重复执行。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" = 'quality-patient-monthly';

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('quality-patient-monthly', '患者月度质控指标', '/page/quality-patient-monthly',
     '单患者月度质控档案：患者切换（含已出科）、月份切换、命中指标与纳入情况、导出 Excel 与打印', 1);
