-- 31_abx_pending_rule.sql
-- 抗感染：新建「抗感染治疗」参数分组 + 「待决策判定规则」参数
--
-- 依赖：14_param_framework.sql（sys_param / sys_param_group 及 param_type、options 等扩展列）
-- 幂等：分组与参数都带 WHERE NOT EXISTS，可重复执行
--
-- 漏执行的表现：参数设置页看不到「抗感染治疗」分组，疑似感染患者列表的
--               「待决策」计数与筛选固定按「当日无决策记录」口径，无法切换成
--               「入科超 24 小时且从未决策」。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 分组：抗感染治疗（供 ABX_* 参数挂载）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param_group"
    ("id", "group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param_group"),
       'antibiotic', '抗感染治疗', 55, 'FirstAidKit',
       '抗感染治疗模块：疑似感染患者列表的待决策判定规则等参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param_group" WHERE "group_code" = 'antibiotic');

-- ---------------------------------------------------------------------
-- 参数：待决策判定规则
--
-- TODAY_NO_DECISION：当天没有决策记录即计入（含从未决策）——每天早上从头盯一遍
-- ADMIT_24H_NEVER ：入科满 24 小时且从未做过抗感染决策才计入——新入科先观察，
--                   已经在科一天以上还没评估的才提醒
-- 参数值留空时回退默认值 default_value（页面清空输入框即恢复出厂设置）。
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'ABX_PENDING_DECISION_RULE', '待决策判定规则', '', 'antibiotic', 1, 1, 'select',
       '[{"label":"当日无决策记录（今日待决策）","value":"TODAY_NO_DECISION"},'
       || '{"label":"入科超 24 小时且从未决策","value":"ADMIT_24H_NEVER"}]',
       'TODAY_NO_DECISION', 0, NULL,
       '疑似感染患者列表顶部「待决策」计数与筛选的口径，全院统一。'
       || 'TODAY_NO_DECISION：当天没有决策记录即计入（含从未决策），适合每天晨间集中评估；'
       || 'ADMIT_24H_NEVER：入科满 24 小时且从未做过抗感染决策才计入，适合「新入科先观察、'
       || '在科一天以上必须评估」的科室。入科时间缺失的患者在 ADMIT_24H_NEVER 下不计入。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'ABX_PENDING_DECISION_RULE');
