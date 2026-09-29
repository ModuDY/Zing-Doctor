-- 34_system_check_page.sql
-- 交付自检页面注册（MySQL / MariaDB）
-- 对应达梦版：sql/34_system_check_page.sql
-- =====================================================================

DELETE FROM `sys_page_config`
 WHERE `page_code` = 'system-check';

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('system-check', '交付自检', '/page/system-check',
     '交付验收入口：检查医生库、ICU 数据源、复评表和当前运行包构建信息', 1);
