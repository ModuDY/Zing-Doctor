-- =====================================================================
-- 28) 质控每日批算参数（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/28_quality_daily_param.sql（真源，改动请先改那边）
-- 本文件由 tools/dm_to_mysql.py 转换并人工复核：脚本只含一条幂等 INSERT，
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，无需其它手工改写。
--
-- 用法（在 zing_doctor_db_prod 库上执行一次即可，幂等可重复）：
--   mysql -uroot -p zing_doctor_db_prod < 28_quality_daily_param.sql
--   部署时由 install-mariadb-debian.sh 的 MAIN_SQL 自动执行（排在 25/26 之后：
--   本脚本写的是新表名 sys_param，老库上要等 25/26 把 zing_sys_param 改名过来）
--
-- 背景与取值语义见达梦版脚本头部注释。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'QUALITY_BACKFILL_DAYS', '每日批算回溯天数', '3', 'quality', 10, 1, 'number',
       NULL, '3', 0, '^[1-9][0-9]{0,2}$',
       '质控每晚 03:30 重算 [今天-N天, 昨天] 覆盖到的所有月份，N 即本参数。默认 3：跨月后仍给 HIS 留 3 天补齐上月末数据的时间，避免最后一天的数据算不准。填 1 则只算前一天所在月。上限 31，调大后会成倍增加夜间批算耗时。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'QUALITY_BACKFILL_DAYS');

-- 校验：
--   SELECT COUNT(*) FROM `sys_param` WHERE `param_key` = 'QUALITY_BACKFILL_DAYS';  -- 应返回 1
