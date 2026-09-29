-- ============================================================
-- MySQL / MariaDB 版本（达梦真源：sql/25_rename_doctor_tables.sql）
--
-- ⚠️ 本文件是**人工改写**，不是 tools/dm_to_mysql.py 的产物：
--    达梦版用 PL/SQL 游标遍历 ALL_TABLES 做判断，转换器处理不了游标，
--    自动生成的结果是语法碎片（已验证），故已在 dm_to_mysql.py 里登记跳过（MANUAL 列表），
--    避免重跑本工具时把人工版本覆盖掉。改动达梦源时请同步本文件。
--    改写口径：
--      · ALL_TABLES + OWNER='ZING_DOCTOR_DB_PROD'  →  information_schema.TABLES + TABLE_SCHEMA=DATABASE()
--      · EXECUTE IMMEDIATE 'DDL'                   →  SET @s=...; PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st
--      · ALTER TABLE "x" RENAME TO "y"             →  RENAME TABLE `x` TO `y`
--    幂等语义与达梦版一致（旧表存在 且 新表不存在 才改名），可重复执行。
--
-- 执行：mysql -uroot -p zing_doctor_db_prod < 25_rename_doctor_tables.sql
--       （install-mariadb-debian.sh 里 run_sql_file 以 < 重定向方式调用，支持 DELIMITER）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---------- 1) 静态表 13 张：单表幂等改名 ----------
DROP PROCEDURE IF EXISTS zing_rename_table;
DELIMITER $$
CREATE PROCEDURE zing_rename_table(IN p_old VARCHAR(64), IN p_new VARCHAR(64))
BEGIN
  DECLARE v_old INT DEFAULT 0;
  DECLARE v_new INT DEFAULT 0;

  SELECT COUNT(*) INTO v_old FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_old;
  SELECT COUNT(*) INTO v_new FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_new;

  -- 全新库由 01_schema.sql 直接按新名建表 → v_old=0 → 整段跳过
  IF v_old > 0 AND v_new = 0 THEN
    SET @s = CONCAT('RENAME TABLE `', p_old, '` TO `', p_new, '`');
    PREPARE st FROM @s;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

CALL zing_rename_table('zing_sys_user',            'sys_user');
CALL zing_rename_table('zing_sys_param',           'sys_param');
CALL zing_rename_table('zing_param_group',         'sys_param_group');
CALL zing_rename_table('zing_page_config',         'sys_page_config');
CALL zing_rename_table('zing_archive_log',         'sys_archive_log');
CALL zing_rename_table('zing_external_access_log', 'sys_access_log');
CALL zing_rename_table('zing_abx_drug_dict',       'config_abx_drug_dict');
CALL zing_rename_table('zing_abx_word_config',     'config_abx_word');
CALL zing_rename_table('zing_ddd_config',          'config_ddd');
CALL zing_rename_table('zing_mdro_config',         'config_mdro');
CALL zing_rename_table('zing_decision_record',     'patient_doc_decision_record');
CALL zing_rename_table('zing_advice_log',          'patient_doc_advice_log');
CALL zing_rename_table('zing_doctor_handover',     'patient_doc_handover_record');

DROP PROCEDURE IF EXISTS zing_rename_table;

-- ---------- 2) 运行时事实表：qc_fact_* → quality_fact_* ----------
-- 表名带批次后缀（…_20260701_dac46），各环境不同，故按前缀通配处理而不是写死 8 个名字。
DROP PROCEDURE IF EXISTS zing_rename_fact_tables;
DELIMITER $$
CREATE PROCEDURE zing_rename_fact_tables()
BEGIN
  DECLARE v_done INT DEFAULT 0;
  DECLARE v_old  VARCHAR(64);
  DECLARE v_new  VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'qc\_fact%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_old;
    IF v_done THEN
      LEAVE read_loop;
    END IF;
    SET v_new = REPLACE(v_old, 'qc_fact_', 'quality_fact_');
    SET @s = CONCAT('RENAME TABLE `', v_old, '` TO `', v_new, '`');
    PREPARE st FROM @s;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL zing_rename_fact_tables();
DROP PROCEDURE IF EXISTS zing_rename_fact_tables;
