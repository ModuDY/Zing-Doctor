-- ================================================================
-- APACHE II 评分文书 PDF 存储增量（已部署环境升级用）
-- 达梦 DM8；DbInit 对 ALTER TABLE ADD COLUMN 做了列存在检查，可重复执行
-- ================================================================

ALTER TABLE "zing_doctor_db_prod"."patient_doc_apache2_score_record" ADD COLUMN "pdf_data" TEXT;
ALTER TABLE "zing_doctor_db_prod"."patient_doc_apache2_score_record" ADD COLUMN "pdf_name" VARCHAR(200);

COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_apache2_score_record"."pdf_data" IS '评分文书PDF的Base64（不含data前缀）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_apache2_score_record"."pdf_name" IS 'PDF文件名';
