-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 质控：患者明细字段可配（增量升级，幂等，可重复执行）
--
-- 背景：明细一直只有固定的 5 列（姓名/住院号/科室/入分子/入分母），
--       而各指标真正想看的补充信息不同 —— 感染类看培养时间、评分类看分值。
--       本列为指标级配置：JSON 数组 [{`key`:`gender`,`label`:`性别`,`width`:70}]，
--       只允许引用该事实层已投影的列（保存时校验，报错在配置页而不是明细页）。
--
-- 老库升级：install.sh 的增量列表已包含本文件，会自动套用；
--           手动执行时重复跑也不会报错（DbInit 会先查列是否存在）。
-- =====================================================================

CALL zing_add_column('quality_metric_def', 'patient_fields', 'VARCHAR(2000) COMMENT ''患者明细展示列 JSON 数组（有序），如 [{`key`:`patientName`,`label`:"",`width`:null},{`key`:`gender`,`label`:`性别`}]；key 用默认列保留 key（patientName/inHospitalNo/patientId/departCode/inNumerator/inDenominator）或事实层已投影列名；数组顺序即显示顺序，空表示用默认六列''');
