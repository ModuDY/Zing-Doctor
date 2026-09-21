-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- ================================================================
-- APACHE II 评分文书 PDF 存储增量（已部署环境升级用）
-- 达梦 DM8；DbInit 对 ALTER TABLE ADD COLUMN 做了列存在检查，可重复执行
-- ================================================================

CALL zing_add_column('apache2_score_record', 'pdf_data', 'TEXT COMMENT ''评分文书PDF的Base64（不含data前缀）''');
CALL zing_add_column('apache2_score_record', 'pdf_name', 'VARCHAR(200) COMMENT ''PDF文件名''');
