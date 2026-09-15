-- ================================================================
-- ICU 信息系统（zing_icu_db_prod）性能优化索引
-- 用途：医生决策系统只读查询 ICU 库时的性能加速
-- 说明：这些索引加在 ICU 业务库上，不影响 ICU 系统原有功能
-- 达梦 DM8 方言，表名/列名双引号保小写
-- 注意：DbInit 工具的 ensureIndex 已内置幂等检查（索引存在自动跳过），
--       此处不写 IF NOT EXISTS（DbInit 不识别该语法会解析错误）
-- ================================================================

-- 1. patient_info_lis_item：检验明细表（医生决策系统核心查询，原无 in_hospital_no 索引导致单条 SQL 10s+）
CREATE INDEX "zing_icu_db_prod"."idx_pili_in_hospital_no"
  ON "zing_icu_db_prod"."patient_info_lis_item" ("in_hospital_no");

CREATE INDEX "zing_icu_db_prod"."idx_pili_inhos_checktime"
  ON "zing_icu_db_prod"."patient_info_lis_item" ("in_hospital_no", "check_time");

CREATE INDEX "zing_icu_db_prod"."idx_pili_item_name"
  ON "zing_icu_db_prod"."patient_info_lis_item" ("lis_item_name");

-- 2. patient_info_lis：检验主表（按 lis_code 关联明细）
CREATE INDEX "zing_icu_db_prod"."idx_pil_lis_code"
  ON "zing_icu_db_prod"."patient_info_lis" ("lis_code");

-- 3. patient_advice_pda：PDA 医嘱执行表（当前抗菌药双表关联查询）
CREATE INDEX "zing_icu_db_prod"."idx_pap_group_serial"
  ON "zing_icu_db_prod"."patient_advice_pda" ("group_id_mark", "in_hospital_serial_no");

CREATE INDEX "zing_icu_db_prod"."idx_pap_in_hospital_no"
  ON "zing_icu_db_prod"."patient_advice_pda" ("in_hospital_no");

-- 4. patient_advice：医嘱表（当前抗菌药双表关联查询）
CREATE INDEX "zing_icu_db_prod"."idx_pa_group_serial"
  ON "zing_icu_db_prod"."patient_advice" ("group_id_mark", "in_hospital_serial_no");

CREATE INDEX "zing_icu_db_prod"."idx_pa_in_hospital_no"
  ON "zing_icu_db_prod"."patient_advice" ("in_hospital_no");

-- 5. patient_info_diagnosis：诊断表（休克类型推理、感染类型推断）
CREATE INDEX "zing_icu_db_prod"."idx_pid_patient_id"
  ON "zing_icu_db_prod"."patient_info_diagnosis" ("patient_id");

-- 6. patient_advice_execute：医嘱执行表（旧版抗菌药查询，兼容保留）
CREATE INDEX "zing_icu_db_prod"."idx_pae_patient_id"
  ON "zing_icu_db_prod"."patient_advice_execute" ("patient_id");

-- 7. config_staff：职工字典表（医生下拉框搜索）
CREATE INDEX "zing_icu_db_prod"."idx_cs_pinyin"
  ON "zing_icu_db_prod"."config_staff" ("pinyin");

CREATE INDEX "zing_icu_db_prod"."idx_cs_work_no"
  ON "zing_icu_db_prod"."config_staff" ("work_no");
