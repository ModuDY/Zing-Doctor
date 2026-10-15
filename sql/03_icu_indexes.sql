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

-- ================================================================
-- 8. 质控事实层预热：逐患者 EXISTS 子查询的驱动列
--
-- 背景：质控事实层物化（CREATE TABLE AS SELECT）以 patient_info（5654 行）为驱动表，
--       每个患者行要跑十来个相关 EXISTS 子查询去大表里找诊断/医嘱/监测/管路。
--       下面这些表此前没有对应索引，每条 EXISTS 都退化成「按患者反复扫大表」：
--       实测单条 CTAS 最慢 144 秒（quality_fact_fact_dvt_20260901 = 当月+上月两张表），
--       16 张事实表预热总耗时 186 秒（日志：zing-doctor 启动后 quality-fact-warmup 线程）。
--
-- 索引列取自 DSL 里实际写的 WHERE 条件（src/main/resources/quality/facts/*.yaml），
-- 不是凭表名推测；列名都在已在生产跑通的 SQL 里出现过。
--
-- 回滚（如需）：DROP INDEX "zing_icu_db_prod"."<索引名>";（索引挂在 ICU 库上，只影响查询计划）
--
-- 只跑这一段也可以（第 1~7 节的索引多数已建，整文件重跑会报「索引已存在」）。
-- ⚠️ 执行前请让 DBA 先查一下这 7 张表的现有索引：DbInit 的幂等判断只按「索引名」去重，
--    若 ICU 厂商已经建过等价索引（同样列、不同名），这里会再建一条冗余索引，
--    白白增加 ICU 业务库的写入代价。等价即可跳过对应那条。
-- ================================================================

-- 8.1 patient_advice_original：医嘱原始表（DVT 机械预防 / 下腔静脉滤器判定）
--     谓词：in_hospital_serial_no = ? AND start_time < 周期末 AND (end_time IS NULL OR end_time >= 周期初)
--     此前无任何索引 —— DSL 里 9 处逐行 EXISTS 全表扫，是 144 秒那条的主因之一
CREATE INDEX "zing_icu_db_prod"."idx_pao_inhos_start"
  ON "zing_icu_db_prod"."patient_advice_original" ("in_hospital_serial_no", "start_time");

-- 8.2 patient_advice_execute_drug：医嘱药品明细，136 万行（DVT 药物预防判定）
--     谓词：patient_id = ? AND del_flag = 0；另有关联 execute_id = 执行单 id
CREATE INDEX "zing_icu_db_prod"."idx_paed_patient_flag"
  ON "zing_icu_db_prod"."patient_advice_execute_drug" ("patient_id", "del_flag");

CREATE INDEX "zing_icu_db_prod"."idx_paed_execute_id"
  ON "zing_icu_db_prod"."patient_advice_execute_drug" ("execute_id", "del_flag");

-- 8.3 patient_advice：医嘱计划表，551 万行（呼吸机/俯卧位/营养/抗菌药等 12 处逐行 EXISTS）
--     原有 idx_pa_in_hospital_no 只定位到患者，还要回表逐行判 del_flag / status；
--     复合索引把这两列一起带上，从索引就能判完
CREATE INDEX "zing_icu_db_prod"."idx_pa_inhos_flag_status"
  ON "zing_icu_db_prod"."patient_advice" ("in_hospital_no", "del_flag", "status");

-- 8.4 patient_observe_module_item：患者级监测项（镇静/镇痛/意识/俯卧位判定）
--     谓词：patient_id = ? AND del_flag = 0
CREATE INDEX "zing_icu_db_prod"."idx_pomi_patient_flag"
  ON "zing_icu_db_prod"."patient_observe_module_item" ("patient_id", "del_flag");

-- 8.5 patient_tube：管路记录（ARDS 事实层按管路天数汇总、插管相关指标）
--     谓词：patient_id = ? AND del_flag = 0 AND tube_set_start_time < 周期末
CREATE INDEX "zing_icu_db_prod"."idx_pt_patient_flag_start"
  ON "zing_icu_db_prod"."patient_tube" ("patient_id", "del_flag", "tube_set_start_time");

-- 8.6 顺带补上：patient_advice_execute（69 万行）缺 in_hospital_no 索引。
--     IcuPatientMapper 的 selectVasopressorExecuteByRange / selectOpioidExecuteByRange
--     都按 e.in_hospital_no = ? 过滤，而现有三个索引（idx_pae_pi / idx_pae_all / idx_pae_pp）
--     全部以 patient_id 打头，这两条查询用不上 → 只能全表扫。
CREATE INDEX "zing_icu_db_prod"."idx_pae_inhos_flag"
  ON "zing_icu_db_prod"."patient_advice_execute" ("in_hospital_no", "del_flag");

-- 说明：patient_info_diagnosis（4.8 万行）虽被引用 31 处，但已有 idx_pid_patient_id，
--       表本身很小，加复合索引收益有限，故不再追加，避免给 ICU 业务库增加无谓的写代价。
