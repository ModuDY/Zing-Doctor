-- 41_workbench_bed_sort_mode.sql
-- 患者工作台：床位排序口径参数（MySQL / MariaDB）
-- 对应达梦版：sql/41_workbench_bed_sort_mode.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，以及 || → CONCAT。
-- 背景与三种口径的含义见达梦版头部注释。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'WORKBENCH_BED_SORT_MODE', '工作台床位排序', '', 'workbench', 4, 1, 'select',
       CONCAT('[{"label":"默认（提取数值升序）","value":"numeric"},',
              '{"label":"纯数字在前，含字母在后","value":"digitFirst"},',
              '{"label":"含字母在前，纯数字在后","value":"letterFirst"}]'),
       'numeric', 0, NULL,
       CONCAT('患者工作台按床位排序时的口径。',
              'numeric：提取床号中的数字按数值升序，不区分是否含字母（9 床排在 10 床前）；',
              'digitFirst：纯数字床号在前、含字母的在后，两组内部各自按数值升序；',
              'letterFirst：含字母的在前、纯数字在后，两组内部各自按数值升序。',
              '无床位的患者（刚入科未分床、转科中）在三种口径下都排在最后。',
              '参数值留空时回退默认值 default_value（numeric）。')
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'WORKBENCH_BED_SORT_MODE');

COMMIT;
