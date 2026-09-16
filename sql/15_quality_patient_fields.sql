-- =====================================================================
-- 质控：患者明细字段可配（增量升级，幂等，可重复执行）
--
-- 背景：明细一直只有固定的 5 列（姓名/住院号/科室/入分子/入分母），
--       而各指标真正想看的补充信息不同 —— 感染类看培养时间、评分类看分值。
--       本列为指标级配置：JSON 数组 [{"key":"gender","label":"性别","width":70}]，
--       只允许引用该事实层已投影的列（保存时校验，报错在配置页而不是明细页）。
--
-- 老库升级：install.sh 的增量列表已包含本文件，会自动套用；
--           手动执行时重复跑也不会报错（DbInit 会先查列是否存在）。
-- =====================================================================

ALTER TABLE "zing_doctor_db_prod"."quality_metric_def" ADD COLUMN "patient_fields" VARCHAR(2000);

COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."patient_fields" IS '患者明细展示列 JSON 数组（有序），如 [{"key":"patientName","label":"","width":null},{"key":"gender","label":"性别"}]；key 用默认列保留 key（patientName/inHospitalNo/patientId/departCode/inNumerator/inDenominator）或事实层已投影列名；数组顺序即显示顺序，空表示用默认六列';
