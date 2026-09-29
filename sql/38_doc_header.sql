-- =====================================================================
-- 38) 评分文书抬头参数：院徽 + 院名（达梦 DM8）
--
-- 背景：
--   APACHE II / SOFA 评分文书（含导出 PDF）抬头的院徽与三行院名此前硬编码
--   在前端（/logo.png 与「福州市第二总医院 / 福州市第二医院 /
--   福建省福州中西医结合医院」）。换院徽、改院名都要改代码重新构建。
--
--   做成两个系统参数，在「参数设置 → 评分配置」里直接改：
--     DOC_HOSPITAL_LOGO  文书院徽：图片，可上传（转 base64）或填图片地址。
--     DOC_HOSPITAL_NAME  文书院名：多行文本，每行一个院名，字号自上而下
--                        递减（20/18/16px，超出 14px）。
--
--   两参数 param_value 留空时，后端回退 default_value（见 ParamStore.raw），
--   前端 useDocHeader 另有内置默认兜底（未执行本脚本也能正常出文书）。
--
-- 依赖：14_param_framework.sql（score 分组 / image 类型由前端支持）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/38_doc_header.sql
-- =====================================================================

-- 1) 文书院名（多行，CHR(10) 换行）
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'DOC_HOSPITAL_NAME', '文书医院抬头', '', 'score', 10, 1, 'textarea',
       NULL,
       '福州市第二总医院' || CHR(10) || '福州市第二医院' || CHR(10) || '福建省福州中西医结合医院',
       0, NULL,
       'APACHE II / SOFA 评分文书抬头院名，每行一个，字号自上而下递减（20/18/16px）。留空恢复默认。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'DOC_HOSPITAL_NAME');

-- 2) 文书院徽（图片）
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'DOC_HOSPITAL_LOGO', '文书院徽', '', 'score', 11, 1, 'image',
       NULL, '/logo.png', 0, NULL,
       'APACHE II / SOFA 评分文书抬头院徽，可上传图片（转 base64）或填图片地址。留空恢复默认 /logo.png。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'DOC_HOSPITAL_LOGO');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" IN ('DOC_HOSPITAL_NAME','DOC_HOSPITAL_LOGO');
--   -- 应返回 2 行
-- ---------------------------------------------------------------------
