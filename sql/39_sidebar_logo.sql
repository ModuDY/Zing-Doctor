-- =====================================================================
-- 39) 侧边栏医院logo显示开关（达梦 DM8）
--
-- 背景：
--   侧边栏顶部的医院logo此前硬编码显示（/logo.png）。不同医院对是否
--   显示logo、用哪个logo要求不同。做成系统参数，在「参数设置 → 系统」
--   里直接开关。
--
--   SIDEBAR_SHOW_LOGO  侧边栏显示医院logo：switch，默认开（1）。
--                      关闭后侧边栏仅显示「医生决策系统」文字。
--                      logo图片复用「文书院徽」参数 DOC_HOSPITAL_LOGO
--                     （image 类型，默认 /logo.png），在评分配置里替换。
--
-- 依赖：14_param_framework.sql（system 分组 / switch 类型由前端支持）
--       38_doc_header.sql（DOC_HOSPITAL_LOGO 参数）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/39_sidebar_logo.sql
-- =====================================================================

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'SIDEBAR_SHOW_LOGO', '侧边栏显示医院logo', '', 'system', 10, 1, 'switch',
       NULL, '1', 0, NULL,
       '侧边栏顶部是否显示医院logo。关闭后仅显示系统名称。logo图片复用「文书院徽」参数（DOC_HOSPITAL_LOGO），可在评分配置里上传替换。'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'SIDEBAR_SHOW_LOGO');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'SIDEBAR_SHOW_LOGO';
-- ---------------------------------------------------------------------
