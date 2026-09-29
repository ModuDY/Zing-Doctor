-- 39_sidebar_logo.sql
-- 侧边栏医院logo显示开关（MySQL / MariaDB）
-- 对应达梦版：sql/39_sidebar_logo.sql（真源，改动请先改那边）
-- 方言差异：双引号 → 反引号；达梦版显式取 MAX(id)+1，MySQL 侧 id 为 AUTO_INCREMENT，不传。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'SIDEBAR_SHOW_LOGO', '侧边栏显示医院logo', '', 'system', 10, 1, 'switch',
       NULL, '1', 0, NULL,
       '侧边栏顶部是否显示医院logo。关闭后仅显示系统名称。logo图片复用「文书院徽」参数（DOC_HOSPITAL_LOGO），可在评分配置里上传替换。'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM `sys_param` WHERE `param_key` = 'SIDEBAR_SHOW_LOGO');

COMMIT;
