-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- =====================================================================
-- 质控：事实层补「床号 / 诊断」两列，供患者明细默认列取用
--       （增量升级，幂等，可重复执行）
--
-- 背景：患者明细默认列调整为
--         姓名 / 床号 / 住院号 / 诊断 / 入科时间 / 出科时间 / 入分子 / 入分母
--       其中「床号 / 诊断」不是指标表达式要用的列，而是明细要显示的列 ——
--       明细 SQL 只会 select 事实层投影过的列，事实层不带出来，页面上就是整列空白。
--       入科 / 出科时间多数事实层本来就有投影，无需处理。
--
--       两列在源表里的形态（已核对 patient_info 结构）：
--         bed_code          VARCHAR(20)  —— 直接取
--         diagnosis_content CLOB         —— 必须 CAST；CAST 写法与 IcuPatientMapper 一致
--       为什么 CLOB 必须 CAST：事实层是 CREATE TABLE AS SELECT 物化成物理表的，
--       而明细会统一套 MAX(...)，达梦不允许对 CLOB 做 MAX。
--
-- 为什么必须走 SQL：生产 config-source=db，事实层定义的真源是 quality_fact_def 表，
--       classpath 的 facts/*.yaml 只在配置表为空时作为「出厂种子」导入一次。
--       因此只改 YAML 不生效，必须同步这张表（两边列名保持一致）。
--       表为空的全新库不受影响：本脚本影响 0 行，随后由种子导入直接写入含新列的 YAML。
--
-- 影响面：仅向 select_cols（JSON 数组）插入两项，不动 where / derive，
--         分子分母的计算结果完全不变；下一次访问明细时物化表会按新定义重建
--         （配置版本变化即失效缓存，重建时先 DROP 再 CREATE TABLE AS）。
-- 幂等：已处理过的行被 NOT LIKE '%AS bed_code%' 跳过，反复执行不会重复加列。
--
-- 锚点为什么用 out_depart_time 而不是「数组最后一项」：这一列 6 个事实层都有，
--       而「最后一项」各不相同（fact_patient_stay 末尾是 out_hospital_time），
--       且库里的定义很可能比当前 YAML 旧（历史上多次只改 YAML 未同步 DB），
--       按末尾匹配会静默不生效。插在 out_depart_time 之后与 YAML 的末尾顺序
--       略有差异，不影响任何功能。REPLACE 只命中一次（JSON 数组内列名唯一）。
--
-- 若某事实层在配置页被改过 select 列表、已不含该锚点，本行不会被更新（也不报错），
--       需在配置页手工补这两列；执行后务必按下方的核对语句确认应返回 6 行。
-- =====================================================================

UPDATE `quality_fact_def`
   SET `select_cols` = REPLACE(
           `select_cols`,
           '"pi.out_depart_time AS out_depart_time"',
           '"pi.out_depart_time AS out_depart_time","pi.bed_code AS bed_code","CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"')
 WHERE `fact_name` IN ('fact_patient_stay','fact_abx_culture','fact_dvt',
                       'fact_sepsis_bundle','fact_assessment','fact_ards')
   AND `select_cols` LIKE '%"pi.out_depart_time AS out_depart_time"%'
   AND `select_cols` NOT LIKE '%AS bed_code%';

-- 核对：应返回 6 行
-- SELECT `fact_name` FROM `quality_fact_def`
--  WHERE `fact_name` IN ('fact_patient_stay','fact_abx_culture','fact_dvt',
--                        'fact_sepsis_bundle','fact_assessment','fact_ards')
--    AND `select_cols` LIKE '%AS bed_code%';
