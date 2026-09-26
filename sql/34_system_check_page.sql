-- 34_system_check_page.sql
-- 交付自检页面注册（达梦 DM8）
--
-- /entry/{pageCode} 只允许访问 sys_page_config 中已注册且启用的页面。
-- 页面路由已存在，但老库若漏登记会被 ExternalLinkService 拒绝为“页面未注册”。
-- 幂等：按 page_code 删除后重建，重复执行不会产生重复注册。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" = 'system-check';

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('system-check', '交付自检', '/page/system-check',
     '交付验收入口：检查医生库、ICU 数据源、复评表和当前运行包构建信息', 1);
