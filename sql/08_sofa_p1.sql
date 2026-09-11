-- =====================================================================
-- zing-doctor SOFA 评分 P1 增量（达梦 DM8）
--
-- 内容：
--   1) sofa_score_record 文书 PDF 大字段（pdf_data / pdf_name）的列注释
--      —— 这两列已**直接定义在 07_sofa.sql 的 CREATE TABLE** 中，
--         本脚本不再重复 ALTER，避免「07 已建表 + 08 再 ADD COLUMN」重复报错。
--   2) 注册 SOFA 配置管理后台页面（sofa-config）
--
-- 幂等：COMMENT ON COLUMN 可重复执行；页面注册用 DELETE + INSERT（先删后插，不产生重复行）。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/...
--   SQL> start /opt/zing-doctor/sql/08_sofa_p1.sql
--
-- 【老环境升级说明】仅当你的库是在本次改动**之前**建的 sofa_score_record
-- （即执行过旧版 07_sofa.sql，表中没有 pdf_data / pdf_name）时，需先手工执行：
--   ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "pdf_data" TEXT;
--   ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "pdf_name" VARCHAR(200);
-- 全新部署无需执行上述 ALTER。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 文书 PDF 列注释（列本身在 07_sofa.sql 中定义）
-- ---------------------------------------------------------------------
COMMENT ON COLUMN "zing_doctor_db_prod"."sofa_score_record"."pdf_data" IS '评分文书PDF的Base64（不含data前缀）；列表查询不返回';
COMMENT ON COLUMN "zing_doctor_db_prod"."sofa_score_record"."pdf_name" IS 'PDF文件名';

-- ---------------------------------------------------------------------
-- 2) 注册 SOFA 配置管理后台页面
-- ---------------------------------------------------------------------
DELETE FROM "zing_doctor_db_prod"."zing_page_config"
 WHERE "page_code" = 'sofa-config';
INSERT INTO "zing_doctor_db_prod"."zing_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('sofa-config', 'SOFA 配置管理', '/page/sofa-config',
     'SOFA 取数项映射（检验/监护/出入量）、血管活性药阈值、换算系数、默认体重维护', 1);
