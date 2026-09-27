-- =====================================================================
-- 36) 患者工作台：评分待办判定规则参数（达梦 DM8）
--
-- 背景：
--   工作台「今日待办」里的 SOFA / APACHE II 未评分提醒，口径此前写死在代码里 ——
--   按「今天有没有评分记录」判断。这带来两个方向相反的问题：
--     1. 入科当天立刻就被催（哪怕夜里 23 点入科），新患者还没到评估时点；
--     2. APACHE II 在本系统是「入科满 24h 自动初评、一人一条」，评过之后
--        第二天起仍会被反复催 —— 医生连着几天看到同一批假待办，很快就不看这个数了。
--
--   两种口径对应两种排班习惯，故做成参数由现场选：
--     TODAY_NO_SCORE（默认）—— 当天没有评分记录即计入（含从未评分）。
--                              适合每天晨间把全科过一遍的科室。
--     ADMIT_24H_NEVER      —— 入科满 24 小时且从未评分才计入。
--                              适合「新入科先观察、在科一天以上必须评估」的科室。
--
--   两种口径的共同前置：都必须「入科满 24 小时」—— 当天入科的患者一律不催，
--   留出评估时间窗。注意这里按小时判断，不用「在科天数 ≥1」：
--   后者对当天入科的患者也算 1，条件恒真，等于没有前置。
--
-- 依赖：30_user_depart_scope.sql（workbench 分组）
--       14_param_framework.sql（param_type / options / default_value 扩展列）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 不执行的后果：
--   后端读不到该参数时回退默认值 TODAY_NO_SCORE —— 功能正常，但现场只能在页面
--   外改口径。另：即使不执行本脚本，代码里那两处误报也已按「入科满 24 小时」修正。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/36_workbench_score_todo_rule.sql
-- =====================================================================

-- 注意：remark 列是 VARCHAR(500)，达梦按**字节**计，一个汉字 3 字节 ——
-- 说明文字要压在 160 个汉字左右。本项目踩过：文案写长了直接报「列[remark]长度超出范围」。
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'WORKBENCH_SCORE_TODO_RULE', '评分待办判定规则', '', 'workbench', 3, 1, 'select',
       '[{"label":"当日无评分记录","value":"TODAY_NO_SCORE"},'
       || '{"label":"入科超 24 小时从未评分","value":"ADMIT_24H_NEVER"}]',
       'TODAY_NO_SCORE', 0, NULL,
       '工作台「今日待办」中 SOFA / APACHE II 未评分提醒的判定口径，全院统一。'
       || 'TODAY_NO_SCORE：当日无评分记录即计入（含从未评分）；'
       || 'ADMIT_24H_NEVER：入科满 24 小时且从未评分才计入。'
       || '两者都要求入科满 24 小时，当天入科不催。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'WORKBENCH_SCORE_TODO_RULE');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'WORKBENCH_SCORE_TODO_RULE';
--   -- 应返回 1 行
-- ---------------------------------------------------------------------
