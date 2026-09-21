-- ============================================================
-- MySQL/MariaDB 修复：给「单列 BIGINT 主键 id 但没自增」的表补 AUTO_INCREMENT
--
-- 背景：达梦版建表语句里 id 列带 DEFAULT "SEQ_xxx".NEXTVAL（省略该列时取序列值），
-- 早期转换版本把这个默认值直接删掉了，于是种子/配置类 INSERT（不写 id）在
-- MySQL 严格模式下直接失败：
--     ERROR 1364 (HY000): Field 'id' doesn't have a default value
--
-- 新建库：01_schema.sql ~ 23_*.sql 已直接带上 AUTO_INCREMENT，无需本脚本。
-- 已建好的库：执行本脚本补齐（无匹配表时是空操作）。
-- 应用侧主键始终由雪花算法（MyBatis-Plus ASSIGN_ID）显式赋值，AUTO_INCREMENT
-- 只在 SQL 省略 id 时兜底，不影响应用写入。
--
-- 幂等：只处理「主键是单列 id 且该列还没有 auto_increment」的表，可重复执行。
-- 执行：mysql -uroot -p zing_doctor_db_prod < 24_fix_id_auto_increment.sql
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

DROP PROCEDURE IF EXISTS zing_fix_id_auto_increment;

DELIMITER $$

CREATE PROCEDURE zing_fix_id_auto_increment()
BEGIN
    DECLARE done  INT DEFAULT 0;
    DECLARE tname VARCHAR(64);

    DECLARE cur CURSOR FOR
        SELECT c.TABLE_NAME
          FROM information_schema.COLUMNS c
          JOIN information_schema.STATISTICS s
            ON s.TABLE_SCHEMA  = c.TABLE_SCHEMA
           AND s.TABLE_NAME    = c.TABLE_NAME
           AND s.COLUMN_NAME   = c.COLUMN_NAME
           AND s.INDEX_NAME    = 'PRIMARY'
           AND s.SEQ_IN_INDEX  = 1
         WHERE c.TABLE_SCHEMA = DATABASE()
           AND c.COLUMN_NAME  = 'id'
           AND c.COLUMN_TYPE LIKE 'bigint%'
           AND (c.EXTRA IS NULL OR c.EXTRA NOT LIKE '%auto_increment%')
           -- 仅单列主键：AUTO_INCREMENT 要求该列被索引，多列主键交给人工处理
           AND (SELECT COUNT(*) FROM information_schema.STATISTICS z
                 WHERE z.TABLE_SCHEMA = c.TABLE_SCHEMA
                   AND z.TABLE_NAME   = c.TABLE_NAME
                   AND z.INDEX_NAME   = 'PRIMARY') = 1;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tname;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        SET @ddl = CONCAT('ALTER TABLE `', tname, '` MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END LOOP;
    CLOSE cur;
END$$

DELIMITER ;

CALL zing_fix_id_auto_increment();
DROP PROCEDURE zing_fix_id_auto_increment;
