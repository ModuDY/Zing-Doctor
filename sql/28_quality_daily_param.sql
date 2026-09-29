-- =====================================================================
-- 28) 质控每日批算参数
--
-- 背景：
--   质控夜间批算从「每月 1 日重算上月」（QualityMonthlyTask，已下线）改为
--   「每天重算 + 回溯窗口」（QualityDailyTask）。
--
--   改动动机：月度一次性定稿时，月末几天的出院 / 结算记录常常要拖到次月初
--   才由 HIS 补齐，定稿之后补录的数据永远进不了已经算完的月份 ——
--   这就是「每月最后一天的数据算不准」的来源。
--
--   现在每晚 03:30 重算 [今天 - N 天, 昨天] 覆盖到的所有月份，
--   N 即本脚本注册的参数 QUALITY_BACKFILL_DAYS。
--
-- 依赖：
--   必须在 14_param_framework.sql 之后执行 —— 本参数挂在它预置的
--   'quality' 分组下，且用到它扩展的 param_type / default_value 等列。
--
-- 幂等说明：
--   与 14 一致，可被 install.sh 重复执行；已存在则跳过，不会重复插入。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/28_quality_daily_param.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 质控每日批算：回溯天数
--
-- 值语义：每天重算 [今天 - N 天, 昨天] 这段窗口覆盖到的所有月份。
--   N=3（默认）→ 例：10/3 跑批，窗口 9/30~10/2，命中 9 月与 10 月，
--                上月数据在次月前 3 天持续被追平，之后自然定稿
--   N=1         → 只算「昨天所在月」，退化为最小粒度
--
-- 取值 1~999；Java 侧（QualityDailyTask）再收一道上限 31，
-- 因为每跨一个月就要多跑一次事实层重建，窗口不能无限放大。
-- 未配置或配错时 Java 侧回退默认值 3，因此本脚本漏执行也不会让批算停摆。
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT 'QUALITY_BACKFILL_DAYS', '每日批算回溯天数', '3', 'quality', 10, 1, 'number',
       NULL, '3', 0, '^[1-9][0-9]{0,2}$',
       '质控每晚 03:30 重算 [今天-N天, 昨天] 覆盖到的所有月份，N 即本参数。默认 3：跨月后仍给 HIS 留 3 天补齐上月末数据的时间，避免“最后一天的数据算不准”。填 1 则只算前一天所在月。上限 31，调大后会成倍增加夜间批算耗时。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'QUALITY_BACKFILL_DAYS');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "param_type", "default_value"
--     FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_group" = 'quality' ORDER BY "sort_no";
-- ---------------------------------------------------------------------
