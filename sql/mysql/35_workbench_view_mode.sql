-- 35_workbench_view_mode.sql
-- 患者工作台：默认视图模式参数（MySQL / MariaDB）
-- 对应达梦版：sql/35_workbench_view_mode.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，以及 || → CONCAT。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'WORKBENCH_VIEW_MODE', '工作台默认视图', '', 'workbench', 2, 1, 'select',
       CONCAT('[{"label":"列表视图","value":"table"},',
              '{"label":"床头卡视图","value":"cards"}]'),
       'table', 0, NULL,
       CONCAT('患者工作台打开时的默认视图。',
              'table：列表视图，信息密度高，适合逐个核对与搜索；',
              'cards：床头卡视图，一张卡片一个患者，适合床旁查房与大屏观看。',
              '用户在页面上的手动切换只对本次会话生效（存 sessionStorage），不改变这里的全院默认值。',
              '参数值留空时回退默认值 default_value。')
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'WORKBENCH_VIEW_MODE');

COMMIT;
