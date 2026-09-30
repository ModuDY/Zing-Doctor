-- 43_workbench_score_display.sql
-- 患者工作台：评分显示类型参数（MySQL / MariaDB）
-- 对应达梦版：sql/43_workbench_score_display.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，以及 || → CONCAT。
-- 背景与取值的含义见达梦版头部注释。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'WORKBENCH_SCORE_DISPLAY', '工作台评分显示', '', 'workbench', 5, 1, 'select',
       CONCAT('[{"label":"SOFA（默认）","value":"SOFA"},',
              '{"label":"APACHE II","value":"APACHE2"}]'),
       'SOFA', 0, NULL,
       CONCAT('患者工作台列表与床头卡「评分」一格显示哪个评分。',
              'SOFA：显示 SOFA 总分，≥10 标红（Sepsis-3 器官功能障碍阈值）；',
              'APACHE2：显示 APACHE II 总分，≥25 标红。',
              '参数值留空时回退默认值 default_value（SOFA）。')
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'WORKBENCH_SCORE_DISPLAY');

COMMIT;
