-- =====================================================================
-- 肾功能与电解质趋势页注册（MySQL / MariaDB）
-- 对应达梦版：sql/49_lab_renal_page.sql
--
-- 左侧菜单新增「肾功能与电解质趋势」须在 sys_page_config 登记，
-- 否则外链 /entry/{pageCode} 会判「未注册的页面」404。
--
-- 幂等：按 page_code 删除后重建，可重复执行。
-- =====================================================================

DELETE FROM `sys_page_config`
 WHERE `page_code` IN ('lab-renal');

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('lab-renal', '肾功能与电解质趋势', '/page/lab-renal', '单患者肾功能与电解质趋势：肌酐 / 尿素氮 / 钾 / 钠 / 氯 / 钙 / 镁 / 磷，已排除降钙素原、脑钠肽、肌钙蛋白等同名异物', 1);
