-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
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
