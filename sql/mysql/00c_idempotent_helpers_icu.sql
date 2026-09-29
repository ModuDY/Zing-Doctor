-- ============================================================
-- MySQL/MariaDB 幂等 DDL 辅助存储过程（ICU 只读库）
--   zing_add_column(table, column, coldef)  列不存在才 ADD
--   zing_add_index(table, index, unique, cols)  索引不存在才 CREATE
-- 存储过程是库级对象，故按库分别提供：
--   00b_idempotent_helpers_doctor.sql  建在 zing_doctor_db_prod
--   00c_idempotent_helpers_icu.sql     建在 zing_icu_db_prod（异机部署时用）
-- 全新部署 / 增量升级通用，可重复执行。需用 mysql/mariadb 客户端执行（含 DELIMITER）。
-- ============================================================

USE `zing_icu_db_prod`;

DROP PROCEDURE IF EXISTS zing_add_column;
DROP PROCEDURE IF EXISTS zing_add_index;

DELIMITER $$

CREATE PROCEDURE zing_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$

CREATE PROCEDURE zing_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64),
                                IN p_unique TINYINT, IN p_cols VARCHAR(500))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @s = CONCAT('CREATE ', IF(p_unique = 1, 'UNIQUE ', ''),
                        'INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;
