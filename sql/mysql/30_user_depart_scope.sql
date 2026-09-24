-- 30_user_depart_scope.sql
-- 患者工作台：科室边界与管理员名单（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/30_user_depart_scope.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，以及 NVL → COALESCE。
-- =====================================================================

-- 分组：患者工作台（供 WORKBENCH_* 参数挂载）
INSERT INTO `sys_param_group`
    (`id`, `group_code`, `group_name`, `sort_no`, `icon`, `remark`, `status`)
SELECT (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `sys_param_group`),
       'workbench', '患者工作台', 50, 'User',
       '患者工作台：科室可见范围、待办规则等相关参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'workbench');

-- 参数：工作台管理员名单（逗号分隔的本系统用户名）
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'WORKBENCH_SUPER_USERS', '工作台管理员名单', 'admin,zing', 'workbench', 1, 1, 'text',
       NULL, 'admin,zing', 0, NULL,
       '逗号分隔的本系统用户名。名单内账号在患者工作台上豁免科室限制、全院可见；名单外账号按其重症侧账号授权科室收敛，重症侧查不到该账号则视为无科室。多个科室时进页面让用户自选。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'WORKBENCH_SUPER_USERS');
