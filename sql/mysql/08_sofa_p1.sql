-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- zing-doctor SOFA 评分 P1 增量（达梦 DM8）
--
-- 内容：
--   1) sofa_score_record 文书 PDF 大字段（pdf_data / pdf_name）的列注释
--      —— 这两列已**直接定义在 07_sofa.sql 的 CREATE TABLE** 中，
--         本脚本不再重复 ALTER，避免「07 已建表 + 08 再 ADD COLUMN」重复报错。
--   2) 注册 SOFA 配置管理后台页面（sofa-config）
--
-- 幂等：--   CALL zing_add_column('sofa_score_record', 'pdf_name', 'VARCHAR(200) COMMENT ''PDF文件名''');
-- 全新部署无需执行上述 ALTER。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 文书 PDF 列注释（列本身在 07_sofa.sql 中定义）
-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- 2) 注册 SOFA 配置管理后台页面
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config`
 WHERE `page_code` = 'sofa-config';
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('sofa-config', 'SOFA 配置管理', '/page/sofa-config',
     'SOFA 取数项映射（检验/监护/出入量）、血管活性药阈值、换算系数、默认体重维护', 1);
