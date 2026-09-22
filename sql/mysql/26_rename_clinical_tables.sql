-- ============================================================
-- MySQL / MariaDB 版本（达梦真源：sql/26_rename_clinical_tables.sql）
--
-- ⚠️ 本文件是**人工改写**，不是 tools/dm_to_mysql.py 的产物：
--    达梦版用 PL/SQL 游标遍历 ALL_TABLES 做判断，转换器处理不了游标，
--    自动生成的结果是语法碎片（已验证），故已在 dm_to_mysql.py 里登记跳过（MANUAL 列表），
--    避免重跑本工具时把人工版本覆盖掉。改动达梦源时请同步本文件。
--    改写口径与 25_rename_doctor_tables.sql 相同。
--
-- 第二批：临床业务表 11 张（俯卧位 / ApacheII / SOFA / 脓毒症）。
-- 这批表 ICU 源库里有同类载体且有数据（patient_prone_position_record /
-- patient_apache_record / patient_sofa_record / patient_sepsis_record），
-- 已确认口径为「医生系统自建表」，故按 patient_doc_* / config_* 落位。
--
-- 执行：mysql -uroot -p zing_doctor_db_prod < 26_rename_clinical_tables.sql
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

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

  IF v_old > 0 AND v_new = 0 THEN
    SET @s = CONCAT('RENAME TABLE `', p_old, '` TO `', p_new, '`');
    PREPARE st FROM @s;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

CALL zing_rename_table('ards_prone_record',    'patient_doc_prone_record');
CALL zing_rename_table('ards_prone_timepoint', 'patient_doc_prone_timepoint');
CALL zing_rename_table('ards_prone_cell',      'patient_doc_prone_cell');
CALL zing_rename_table('ards_prone_cell_log',  'patient_doc_prone_cell_log');
CALL zing_rename_table('ards_prone_config',    'config_prone_item');
CALL zing_rename_table('ards_prone_tp_tpl',    'config_prone_timepoint_tpl');
CALL zing_rename_table('apache2_config',       'config_apache2');
CALL zing_rename_table('sofa_config',          'config_sofa');
CALL zing_rename_table('apache2_score_record', 'patient_doc_apache2_score_record');
CALL zing_rename_table('sofa_score_record',    'patient_doc_sofa_score_record');
CALL zing_rename_table('sepsis_bundle_record', 'patient_doc_sepsis_bundle_record');

DROP PROCEDURE IF EXISTS zing_rename_table;
