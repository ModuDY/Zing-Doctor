-- =====================================================================
-- 医生决策系统 - 质控指标「可视化配置」增量脚本（达梦 DM8）
--
-- 定位：09_quality.sql 建的是「结果与血缘」表，本脚本建的是「配置真源」表。
--       原先指标口径只存在于 jar 内的 quality/metrics/*.yaml，运行时不可写，
--       因此页面无法编辑。这里把配置真源搬到 DB：可读写、可版本回溯、可热生效。
--
--   A) quality_metric_def   指标定义（含分子/分母/维度表达式，可编辑）
--   B) quality_fact_def     事实层 DWD 定义（select/derive/where，可编辑）
--   C) quality_def_history  配置变更历史（每次保存留一份快照，支持回滚）
--
-- 与 09 的关系：
--   quality_index        仍是「字典」，供看板查询，由配置同步而来（只读副本）
--   quality_metric_def   是「真源」，页面在此编辑，保存后 reload 并同步字典
--
-- 幂等：DbInit 对 CREATE TABLE / CREATE INDEX 做存在性检查，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/10_quality_config.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) 指标定义表（配置真源，页面可编辑）
--    与 YAML 的 MetricDefinition 一一对应；字段增减需同步 Repository 映射。
--    expr_version 由服务端在口径变化时自动 +1，页面不手填。
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_metric_def" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_metric_def" (
    "id"                      BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_metric_def".NEXTVAL NOT NULL,
    "index_code"              VARCHAR(32)   NOT NULL,
    "index_name"              VARCHAR(200),
    "domain_code"             VARCHAR(32),
    "fact_name"               VARCHAR(64),
    "unit"                    VARCHAR(32),
    "value_type"              VARCHAR(16)   DEFAULT 'COUNT',
    "agg"                     VARCHAR(16)   DEFAULT 'PT_COUNT',
    "calc_mode"               VARCHAR(16)   DEFAULT 'DSL',
    "impl_status"             VARCHAR(16)   DEFAULT 'IMPL',
    "scale"                   INT           DEFAULT 100,
    -- 口径三要素：这就是「可视化配置」真正编辑的内容
    "expr_where"              TEXT,
    "expr_numerator"          TEXT,
    "expr_denominator_where"  TEXT,
    "dims"                    VARCHAR(512),
    -- 患者明细的展示列（JSON 数组，有序），如 [{"key":"patientName","label":"","width":null},
    -- {"key":"gender","label":"性别","width":70}]。
    -- key 有两种：默认列的保留 key（patientName / inHospitalNo / patientId / departCode /
    -- inNumerator / inDenominator，由引擎固定产出）与事实层已投影的列名。
    -- 数组顺序即页面显示顺序；空表示不做配置、直接用默认六列。
    -- 注意：列名不要写 patient_id / depart_code 这类默认列的实际列名，会与引擎产出的列重名，
    -- 导致明细 SQL「列名不明确」而整份为空（保存校验会拦下）。
    "patient_fields"          VARCHAR(2000),
    "expr_version"            INT           DEFAULT 1,
    "sort_no"                 INT           DEFAULT 1,
    "remark"                  VARCHAR(1000),
    -- 老系统映射信息（字典展示与双跑核对用，页面只读）
    "category_code"           VARCHAR(8),
    "group_code"              VARCHAR(8),
    "quality_type_code"       VARCHAR(8),
    "index_standard_code"     VARCHAR(64),
    "amount_show_type"        VARCHAR(8),
    "analysis_count_type"     VARCHAR(8),
    "legacy_script"           VARCHAR(16),
    "legacy_source"           VARCHAR(300),
    "new_target"              VARCHAR(300),
    "reuse_level"             VARCHAR(16),
    "status"                  TINYINT       DEFAULT 1,
    "operator"                VARCHAR(64),
    "create_time"             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_metric_def" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_metric_def" IS '质控指标定义（配置真源，页面可编辑；保存后热生效）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."index_code"             IS '指标编号 quality_xxx';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."fact_name"              IS '绑定事实层（quality_fact_def.fact_name）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."agg"                    IS '聚合方式：PT_COUNT 去重患者数 / SUM 求和 / AVG 均值';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."expr_where"             IS '分子过滤条件（DSL 片段，不含分号/注释/DML）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."expr_numerator"         IS '分子表达式：PT_COUNT 时为布尔条件；SUM/AVG 时为数值表达式；空则默认 1';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."expr_denominator_where" IS '分母过滤条件；为空时分母=同期全部对象';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."dims"                   IS '分组维度列名 JSON 数组，如 ["depart_code"]；空表示仅全院 ALL 一行';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."expr_version"           IS '口径版本：口径变化时服务端自动 +1，用于历史值回溯';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."patient_fields"         IS '患者明细补充字段 JSON 数组，如 [{"key":"gender","label":"性别"}]；只能引用事实层已投影列，空表示只用默认列';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."status"                 IS '1 启用 / 0 停用（停用不物理删除，保留历史结果可回溯）';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_qmd_code" ON "zing_doctor_db_prod"."quality_metric_def" ("index_code");
CREATE INDEX "zing_doctor_db_prod"."idx_qmd_domain" ON "zing_doctor_db_prod"."quality_metric_def" ("domain_code", "status");

-- ---------------------------------------------------------------------
-- B) 事实层定义表（配置真源，页面可编辑）
--    与 YAML 的 FactDefinition 一一对应；List<String> 统一以 JSON 数组存 TEXT。
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_fact_def" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_fact_def" (
    "id"                 BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_fact_def".NEXTVAL NOT NULL,
    "fact_name"          VARCHAR(64) NOT NULL,
    "domain_code"        VARCHAR(32),
    "status"             VARCHAR(16) DEFAULT 'ACTIVE',
    "source_table"       VARCHAR(64),
    "alias"              VARCHAR(16) DEFAULT 't',
    "patient_key"        VARCHAR(64) DEFAULT 'patient_id',
    "in_hospital_no_key" VARCHAR(64) DEFAULT 'in_hospital_no',
    "patient_name_key"   VARCHAR(64) DEFAULT 'patient_name',
    "depart_key"         VARCHAR(64) DEFAULT 'depart_code',
    "select_cols"        TEXT,
    "derive_cols"        TEXT,
    "where_conds"        TEXT,
    "group_cols"         TEXT,
    "note"               VARCHAR(500),
    "operator"           VARCHAR(64),
    "create_time"        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_fact_def" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_fact_def" IS '质控事实层（DWD）定义（配置真源，页面可编辑）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."fact_name"    IS '事实层名，同时作为物化表名后缀（qc_ + fact_name）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."status"       IS 'ACTIVE 可计算 / PENDING_SOURCE 待接数据源 / PLACEHOLDER 空壳';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."source_table" IS '来源逻辑表名（对应 quality/sources.yaml 的 tables key）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."select_cols"  IS '选列表达式 JSON 数组（表达式 + AS 别名）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."derive_cols"  IS '派生列表达式 JSON 数组（与 select 合并输出，供指标引用）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."where_conds"  IS '过滤条件 JSON 数组，逐条 AND';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_fact_def"."group_cols"   IS '分组列 JSON 数组，空表示患者级（不聚合）';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_qfd_name" ON "zing_doctor_db_prod"."quality_fact_def" ("fact_name");
CREATE INDEX "zing_doctor_db_prod"."idx_qfd_domain" ON "zing_doctor_db_prod"."quality_fact_def" ("domain_code");

-- ---------------------------------------------------------------------
-- C) 配置变更历史（口径回溯；与编辑功能同期上线，不能后补）
--    snapshot 存变更后的完整 JSON —— 任何一版口径都能原样还原。
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_def_history" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_def_history" (
    "id"           BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_def_history".NEXTVAL NOT NULL,
    "def_type"     VARCHAR(16) NOT NULL,
    "def_key"      VARCHAR(64) NOT NULL,
    "expr_version" INT,
    "change_type"  VARCHAR(16),
    "snapshot"     TEXT,
    "operator"     VARCHAR(64),
    "create_time"  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_def_his" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_def_history" IS '质控配置变更历史（每次保存留快照，支持版本对比与回滚）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_def_history"."def_type"    IS 'METRIC 指标 / FACT 事实层';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_def_history"."def_key"     IS '指标编号或事实层名';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_def_history"."change_type" IS 'CREATE 新增 / UPDATE 修改 / DISABLE 停用 / ROLLBACK 回滚';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_def_history"."snapshot"    IS '变更后的完整 JSON 快照（可原样还原该版本）';
CREATE INDEX "zing_doctor_db_prod"."idx_qdh_key" ON "zing_doctor_db_prod"."quality_def_history" ("def_type", "def_key", "id");

-- ---------------------------------------------------------------------
-- D) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" IN ('quality-config');
INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('quality-config', '质控指标配置', '/page/quality-config',
     '质控指标可视化配置：指标口径编辑（简单/高级，支持「或」条件组）、事实层配置、变更历史与回滚、批量导入导出，保存即热生效', 1);
