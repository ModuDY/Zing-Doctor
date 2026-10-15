-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_icu_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- ================================================================
-- ICU 信息系统（zing_icu_db_prod）性能优化索引
-- 用途：医生决策系统只读查询 ICU 库时的性能加速
-- 说明：这些索引加在 ICU 业务库上，不影响 ICU 系统原有功能
-- 达梦 DM8 方言，表名/列名双引号保小写
-- 注意：DbInit 工具的 ensureIndex 已内置幂等检查（索引存在自动跳过），
--       此处不写 IF NOT EXISTS（DbInit 不识别该语法会解析错误）
-- ================================================================

-- 1. patient_info_lis_item：检验明细表（医生决策系统核心查询，原无 in_hospital_no 索引导致单条 SQL 10s+）
CALL zing_add_index('patient_info_lis_item', 'idx_pili_in_hospital_no', 0, '`in_hospital_no`');

CALL zing_add_index('patient_info_lis_item', 'idx_pili_inhos_checktime', 0, '`in_hospital_no`, `check_time`');

CALL zing_add_index('patient_info_lis_item', 'idx_pili_item_name', 0, '`lis_item_name`');

-- 2. patient_info_lis：检验主表（按 lis_code 关联明细）
CALL zing_add_index('patient_info_lis', 'idx_pil_lis_code', 0, '`lis_code`');

-- 3. patient_advice_pda：PDA 医嘱执行表（当前抗菌药双表关联查询）
CALL zing_add_index('patient_advice_pda', 'idx_pap_group_serial', 0, '`group_id_mark`, `in_hospital_serial_no`');

CALL zing_add_index('patient_advice_pda', 'idx_pap_in_hospital_no', 0, '`in_hospital_no`');

-- 4. patient_advice：医嘱表（当前抗菌药双表关联查询）
CALL zing_add_index('patient_advice', 'idx_pa_group_serial', 0, '`group_id_mark`, `in_hospital_serial_no`');

CALL zing_add_index('patient_advice', 'idx_pa_in_hospital_no', 0, '`in_hospital_no`');

-- 5. patient_info_diagnosis：诊断表（休克类型推理、感染类型推断）
CALL zing_add_index('patient_info_diagnosis', 'idx_pid_patient_id', 0, '`patient_id`');

-- 6. patient_advice_execute：医嘱执行表（旧版抗菌药查询，兼容保留）
CALL zing_add_index('patient_advice_execute', 'idx_pae_patient_id', 0, '`patient_id`');

-- 7. config_staff：职工字典表（医生下拉框搜索）
CALL zing_add_index('config_staff', 'idx_cs_pinyin', 0, '`pinyin`');

CALL zing_add_index('config_staff', 'idx_cs_work_no', 0, '`work_no`');

-- ================================================================
-- 8. 质控事实层预热：逐患者 EXISTS 子查询的驱动列
--
-- 背景：质控事实层物化（CREATE TABLE AS SELECT）以 patient_info（5654 行）为驱动表，
--       每个患者行要跑十来个相关 EXISTS 子查询去大表里找诊断/医嘱/监测/管路。
--       下面这些表此前没有对应索引，每条 EXISTS 都退化成「按患者反复扫大表」：
--       实测单条 CTAS 最慢 144 秒（quality_fact_fact_dvt_20260901），16 张表预热共 186 秒。
--
-- 索引列取自 DSL 里实际写的 WHERE 条件（src/main/resources/quality/facts/*.yaml）。
-- 达梦版见 sql/03_icu_indexes.sql，两版必须保持一致。
--
-- 只跑这一段也可以（第 1~7 节的索引多数已建，zing_add_index 会自动跳过已存在的同名索引）。
-- ⚠️ 执行前请让 DBA 先查一下这 7 张表的现有索引：zing_add_index 只按「索引名」去重，
--    若 ICU 厂商已建过等价索引（同样列、不同名），这里会再建一条冗余索引，白白增加写入代价。
-- ================================================================

-- 8.1 patient_advice_original：医嘱原始表（DVT 机械预防 / 下腔静脉滤器判定），此前无任何索引
CALL zing_add_index('patient_advice_original', 'idx_pao_inhos_start', 0, '`in_hospital_serial_no`, `start_time`');

-- 8.2 patient_advice_execute_drug：医嘱药品明细，136 万行（DVT 药物预防判定 + 血管活性药泵速查询）
CALL zing_add_index('patient_advice_execute_drug', 'idx_paed_patient_flag', 0, '`patient_id`, `del_flag`');
CALL zing_add_index('patient_advice_execute_drug', 'idx_paed_execute_id', 0, '`execute_id`, `del_flag`');

-- 8.3 patient_advice：医嘱计划表，551 万行（呼吸机/俯卧位/营养/抗菌药等 12 处逐行 EXISTS）
CALL zing_add_index('patient_advice', 'idx_pa_inhos_flag_status', 0, '`in_hospital_no`, `del_flag`, `status`');

-- 8.4 patient_observe_module_item：患者级监测项（镇静/镇痛/意识/俯卧位判定）
CALL zing_add_index('patient_observe_module_item', 'idx_pomi_patient_flag', 0, '`patient_id`, `del_flag`');

-- 8.5 patient_tube：管路记录（ARDS 事实层按管路天数汇总、插管相关指标）
CALL zing_add_index('patient_tube', 'idx_pt_patient_flag_start', 0, '`patient_id`, `del_flag`, `tube_set_start_time`');

-- 8.6 顺带补上：patient_advice_execute（69 万行）缺 in_hospital_no 索引，
--     血管活性药 / 阿片类泵速查询按该列过滤，现有索引全部以 patient_id 打头，用不上
CALL zing_add_index('patient_advice_execute', 'idx_pae_inhos_flag', 0, '`in_hospital_no`, `del_flag`');

-- 说明：patient_info_diagnosis（4.8 万行）已有 patient_id 索引且表很小，不追加复合索引，
--       避免给 ICU 业务库增加无谓的写代价。
