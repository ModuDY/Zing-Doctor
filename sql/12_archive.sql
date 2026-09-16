-- =====================================================================
-- 医生决策系统 - 系统参数 + 评分文书归档增量（达梦 DM8）
--
-- 内容：
--   1) zing_sys_param        系统参数表（归档接口地址 / 归档目录）
--   2) zing_archive_log      归档推送流水表（跟踪每次推送与结果）
--   3) sofa_score_record     新增归档字段 archive_status / archive_time / file_path
--   4) apache2_score_record  同上
--   5) zing_page_config      页面注册（参数设置 /page/param-config）
--
-- 幂等：ALTER TABLE ADD COLUMN 由部署脚本做列存在检查，可重复执行；
--       页面注册与默认参数用「先删后插 / NOT EXISTS」守卫，重复执行不产生重复行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/12_archive.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 系统参数表
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_sys_param" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."zing_sys_param" (
    "id"           BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_zing_sys_param".NEXTVAL NOT NULL,
    "param_key"    VARCHAR(64)   NOT NULL,
    "param_name"   VARCHAR(128)  NOT NULL,
    "param_value"  VARCHAR(1000),
    "param_group"  VARCHAR(64)   DEFAULT 'archive',
    "sort_no"      INT           DEFAULT 0,
    "status"       TINYINT       DEFAULT 1,
    "remark"       VARCHAR(500),
    "create_by"    VARCHAR(64),
    "create_time"  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"    VARCHAR(64),
    "update_time"  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_sys_param" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_zing_sys_param_key" ON "zing_doctor_db_prod"."zing_sys_param" ("param_key");

COMMENT ON TABLE  "zing_doctor_db_prod"."zing_sys_param" IS '系统参数表（参数设置页面维护，如文书归档接口地址、归档目录）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."param_key"   IS '参数键（唯一，如 ARCHIVE_API_URL / ARCHIVE_DIR）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."param_name"  IS '参数名称（页面展示）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."param_value" IS '参数值';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."param_group" IS '参数分组（archive=文书归档）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."sort_no"     IS '排序号';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."status"      IS '状态：1启用 0停用';

-- ---------------------------------------------------------------------
-- 2) 归档推送流水表：记录每次归档/撤销的时间、file_path 与对方响应，便于追溯
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_archive_log" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."zing_archive_log" (
    "id"             BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_zing_archive_log".NEXTVAL NOT NULL,
    "biz"            VARCHAR(32),
    "record_id"      BIGINT,
    "in_hospital_no" VARCHAR(64),
    "patient_name"   VARCHAR(64),
    "doc_code"       VARCHAR(32),
    "score_date"     VARCHAR(10),
    "file_path"      VARCHAR(500),
    "api_url"        VARCHAR(500),
    "op_type"        VARCHAR(16),
    "success"        TINYINT       DEFAULT 0,
    "http_status"    INT,
    "resp_code"      INT,
    "resp_message"   VARCHAR(1000),
    "operator"       VARCHAR(64),
    "create_time"    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_archive_log" PRIMARY KEY ("id")
);

CREATE INDEX "idx_zing_archive_log_rec" ON "zing_doctor_db_prod"."zing_archive_log" ("biz", "record_id");
CREATE INDEX "idx_zing_archive_log_no"  ON "zing_doctor_db_prod"."zing_archive_log" ("in_hospital_no");

COMMENT ON TABLE  "zing_doctor_db_prod"."zing_archive_log" IS '文书归档推送流水';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."biz"          IS '业务：SOFA / APACHE2';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."record_id"    IS '评分记录 ID';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."doc_code"     IS '文书编码：sofa / apache2';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."score_date"   IS '评分日期（yyyy-MM-dd）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."file_path"    IS '按归档目录规则拼出的文件路径';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."op_type"      IS '操作：push=归档推送 unmark=撤销标记';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."success"      IS '是否成功：1成功 0失败';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_archive_log"."resp_message" IS '失败原因 / 对方返回报文摘要';

-- ---------------------------------------------------------------------
-- 3) 评分记录归档字段
--    archive_status：0 待归档（默认） 1 已归档
--    archive_time  ：最近一次归档成功时间
--    file_path     ：按归档目录规则拼出的文件路径（每次归档刷新为最新）
-- ---------------------------------------------------------------------
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "archive_status" TINYINT DEFAULT 0;
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "archive_time" TIMESTAMP;
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "file_path" VARCHAR(500);

COMMENT ON COLUMN "zing_doctor_db_prod"."sofa_score_record"."archive_status" IS '归档状态：0待归档 1已归档';
COMMENT ON COLUMN "zing_doctor_db_prod"."sofa_score_record"."archive_time"   IS '最近一次归档成功时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."sofa_score_record"."file_path"      IS '文书归档路径（按参数设置页的归档目录规则生成）';

ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "archive_status" TINYINT DEFAULT 0;
ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "archive_time" TIMESTAMP;
ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "file_path" VARCHAR(500);

COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."archive_status" IS '归档状态：0待归档 1已归档';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."archive_time"   IS '最近一次归档成功时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."file_path"      IS '文书归档路径（按参数设置页的归档目录规则生成）';

-- ---------------------------------------------------------------------
-- 4) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM "zing_doctor_db_prod"."zing_page_config" WHERE "page_code" = 'param-config';
INSERT INTO "zing_doctor_db_prod"."zing_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('param-config', '参数设置', '/page/param-config',
     '系统参数设置：文书归档接口地址、归档目录等', 1);

-- ---------------------------------------------------------------------
-- 5) 默认参数（不存在才写入，不覆盖院内已改配置）
--    ARCHIVE_API_URL：归档接口地址（完整 URL，含 jodName）
--    ARCHIVE_DIR    ：归档目录规则，占位符 #in_hospital_no# / #doc_code# / #score_date#
--                     也支持 #patient_id# / #patient_name#
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no", "status", "remark")
SELECT 'ARCHIVE_API_URL', '文书归档接口地址', '', 'archive', 1, 1,
       '评分文书归档推送地址（完整 URL，含 jodName），SOFA 与 APACHE II 共用'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'ARCHIVE_API_URL'
  );

INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no", "status", "remark")
SELECT 'ARCHIVE_DIR', '归档目录', '/ICU/#in_hospital_no#/#doc_code#/#score_date#', 'archive', 2, 1,
       '文书存放目录规则：占位符 #in_hospital_no# 住院号 / #doc_code# 文书编码(sofa|apache2) / #score_date# 评分日期，也支持 #patient_id# / #patient_name#'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'ARCHIVE_DIR'
  );
