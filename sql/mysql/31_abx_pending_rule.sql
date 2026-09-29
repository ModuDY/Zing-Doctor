-- 31_abx_pending_rule.sql
-- 抗感染：新建「抗感染治疗」参数分组 + 「待决策判定规则」参数（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/31_abx_pending_rule.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，以及 NVL → COALESCE。
-- =====================================================================

-- 分组：抗感染治疗（供 ABX_* 参数挂载）
INSERT INTO `sys_param_group`
    (`id`, `group_code`, `group_name`, `sort_no`, `icon`, `remark`, `status`)
SELECT (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `sys_param_group`),
       'antibiotic', '抗感染治疗', 55, 'FirstAidKit',
       '抗感染治疗模块：疑似感染患者列表的待决策判定规则等参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'antibiotic');

-- 参数：待决策判定规则
-- TODAY_NO_DECISION：当天没有决策记录即计入（含从未决策）
-- ADMIT_24H_NEVER：入科满 24 小时且从未做过抗感染决策才计入
-- 参数值留空时回退默认值 default_value。
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'ABX_PENDING_DECISION_RULE', '待决策判定规则', '', 'antibiotic', 1, 1, 'select',
       CONCAT('[{"label":"当日无决策记录（今日待决策）","value":"TODAY_NO_DECISION"},',
              '{"label":"入科超 24 小时且从未决策","value":"ADMIT_24H_NEVER"}]'),
       'TODAY_NO_DECISION', 0, NULL,
       CONCAT('疑似感染患者列表顶部「待决策」计数与筛选的口径，全院统一。',
              'TODAY_NO_DECISION：当天没有决策记录即计入（含从未决策），适合每天晨间集中评估；',
              'ADMIT_24H_NEVER：入科满 24 小时且从未做过抗感染决策才计入，适合「新入科先观察、',
              '在科一天以上必须评估」的科室。入科时间缺失的患者在 ADMIT_24H_NEVER 下不计入。')
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'ABX_PENDING_DECISION_RULE');
