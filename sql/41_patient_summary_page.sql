-- =====================================================================
-- 患者诊疗摘要页面注册（达梦 DM8）
--
-- /entry/patient-summary 只允许访问 sys_page_config 中已注册且启用的页面。
-- 前端路由已存在；老库升级时若缺少本注册，外链会被判定为“未注册的页面”。
-- 幂等：按 page_code 删除后重建，可重复执行。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" = 'patient-summary';

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('patient-summary', '患者诊疗摘要', '/page/patient-summary',
     '单患者诊疗摘要：基本信息、生命支持、评分、感染指标、当前抗菌药、待办与快捷操作', 1);

