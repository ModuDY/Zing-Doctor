-- =====================================================================
-- 肾功能与电解质趋势页注册（达梦 DM8）
-- 对应 MySQL / MariaDB 版：sql/mysql/49_lab_renal_page.sql
--
-- 左侧菜单新增「肾功能与电解质趋势」须在 sys_page_config 登记：
-- /entry/{pageCode} 只放行已注册且启用的页面，未登记会判「未注册的页面」404，
-- 参数设置页的「外链页面」一览也看不到它。
--
-- 幂等：按 page_code 删除后重建，可重复执行。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" IN ('lab-renal');

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('lab-renal', '肾功能与电解质趋势', '/page/lab-renal', '单患者肾功能与电解质趋势：肌酐 / 尿素氮 / 钾 / 钠 / 氯 / 钙 / 镁 / 磷，已排除降钙素原、脑钠肽、肌钙蛋白等同名异物', 1);
