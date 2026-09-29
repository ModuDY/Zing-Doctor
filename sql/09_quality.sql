-- =====================================================================
-- 医生决策系统 - 质控指标中台增量脚本（达梦 DM8）
--
-- 设计定位：与旧的「cal_custom_js 脚本集合」彻底区分，本方案是
--           「数仓分层 + 指标中台」：
--   ① quality_index          指标字典（127 条，含展示配置与实现状态，由 YAML 同步）
--   ② quality_calc_run       计算批次（血缘第 1 层）
--   ③ quality_metric_result  指标结果（血缘第 2 层：分子/分母/值 + 老系统对比值）
--   ④ quality_calc_trace     算子/ SQL 追溯（血缘第 3 层：SQL、扫描行数、表达式版本）
--   ⑤ quality_metric_patient 患者级命中明细（血缘第 4 层：谁进分子、谁进分母）
--   ⑥ quality_monthly_report 月度汇总宽表（1-12 月横排，页面与导出直读）
--
-- 幂等：DbInit 对 CREATE TABLE / CREATE INDEX 做存在性检查，可重复执行；
--       字典不在此处硬编码，由 QualityIndexSyncService 启动时从
--       classpath:quality/metrics/*.yaml 同步（改指标=改配置，不发版）。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/09_quality.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 指标字典表
--    指标编号 / 名称 / 分类 / 展示配置 / 实现状态 / 口径版本
--    数据来源：classpath:quality/metrics/*.yaml（引擎启动同步，避免 SQL 与配置双维护）
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_index" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_index" (
    "id"                  BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_index".NEXTVAL NOT NULL,
    "index_code"          VARCHAR(32)   NOT NULL,
    "index_name"          VARCHAR(200),
    "domain_code"         VARCHAR(32),
    "category_code"       VARCHAR(8),
    "group_code"          VARCHAR(8),
    "quality_type_code"   VARCHAR(8),
    "index_standard_code" VARCHAR(64),
    "unit"                VARCHAR(32),
    "value_type"          VARCHAR(16),
    "calc_mode"           VARCHAR(16),
    "fact_name"           VARCHAR(64),
    "impl_status"         VARCHAR(16),
    "expression_version"  INT           DEFAULT 1,
    "amount_show_type"    VARCHAR(8),
    "analysis_count_type" VARCHAR(8),
    "is_use_zero_show"    TINYINT       DEFAULT 0,
    "is_show_patient"     TINYINT       DEFAULT 0,
    "legacy_script"       VARCHAR(16),
    "legacy_source"       VARCHAR(300),
    "new_target"          VARCHAR(300),
    "reuse_level"         VARCHAR(16),
    "depend_keys"         VARCHAR(512),
    "sort_no"             INT           DEFAULT 1,
    "remark"              VARCHAR(1000),
    "status"              TINYINT       DEFAULT 1,
    "create_time"         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_index" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_index" IS '质控指标字典（127 条，由 quality/metrics/*.yaml 同步）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."index_code"          IS '指标编号 quality_xxx';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."domain_code"         IS '所属域：患者流转/脓毒症_感染性休克/抗菌药送检/DVT预防/评分_资源/评估依从/导管_管路_院感/ARDS专项';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."category_code"       IS '一级分类编码（老系统 categoryCode）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."group_code"          IS '分组编码（老系统 groupCode）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."quality_type_code"   IS '质控类别编码';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."index_standard_code" IS '指标标准编码（老系统 indexStandardCode）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."unit"                IS '单位：%/人/天/例';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."value_type"          IS '值类型：RATE 率 / COUNT 数 / DAYS 天数 / RATIO 比 / AMOUNT 均值';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."calc_mode"           IS '计算方式：DSL 声明式 / MANUAL 人工录入 / CUSTOM_SQL 自定义SQL';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."fact_name"           IS '绑定事实层（DWD）名称，如 fact_patient_stay';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."impl_status"         IS '实现状态：IMPL 已实现 / PLACEHOLDER 空壳占位 / PENDING_SOURCE 待接数据源 / MANUAL 人工录入';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."expression_version"  IS '口径版本，改口径即递增，用于回溯历史值的口径';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."legacy_script"       IS '老系统是否有脚本：是 / 否-空壳 / 不适用';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."depend_keys"         IS '依赖键（老系统 dependKeys），用于血缘追溯';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_index"."remark"              IS '备注（老系统缺陷、口径待定说明等）';
CREATE INDEX "zing_doctor_db_prod"."idx_quality_index_domain" ON "zing_doctor_db_prod"."quality_index" ("domain_code", "status");
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_quality_index_code" ON "zing_doctor_db_prod"."quality_index" ("index_code");

-- ---------------------------------------------------------------------
-- 2) 计算批次表（血缘第 1 层）
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_calc_run" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_calc_run" (
    "id"               BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_calc_run".NEXTVAL NOT NULL,
    "run_id"           VARCHAR(40)  NOT NULL,
    "period_type"      VARCHAR(16),
    "period_start"     TIMESTAMP,
    "period_end"       TIMESTAMP,
    "depart_code"      VARCHAR(64),
    "trigger_type"     VARCHAR(16),
    "engine_version"   VARCHAR(32),
    "data_snapshot_id" VARCHAR(64),
    "start_time"       TIMESTAMP,
    "end_time"         TIMESTAMP,
    "duration_ms"      BIGINT,
    "metric_total"     INT          DEFAULT 0,
    "metric_ok"        INT          DEFAULT 0,
    "metric_fail"      INT          DEFAULT 0,
    "metric_placeholder" INT        DEFAULT 0,
    "status"           VARCHAR(16),
    "operator"         VARCHAR(64),
    "message"          VARCHAR(1000),
    "create_time"      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_calc_run" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_calc_run" IS '质控计算批次（血缘第1层：谁触发、什么周期、耗时、数据快照）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."run_id"           IS '批次号（一次计算一个）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."period_type"      IS '周期类型：MONTH/QUARTER/YEAR/CUSTOM';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."depart_code"      IS '科室编码，空表示全院';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."trigger_type"     IS '触发方式：MANUAL 手动 / SCHEDULED 定时';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."engine_version"   IS '引擎版本';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."data_snapshot_id" IS '数据快照指纹（源表行数+最大更新时间），用于增量跳过';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_run"."status"           IS '状态：RUNNING/SUCCESS/PARTIAL/FAILED';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_quality_run_id" ON "zing_doctor_db_prod"."quality_calc_run" ("run_id");
CREATE INDEX "zing_doctor_db_prod"."idx_quality_run_period" ON "zing_doctor_db_prod"."quality_calc_run" ("period_start");

-- ---------------------------------------------------------------------
-- 3) 指标结果表（血缘第 2 层；含老系统对比值，供双跑核对）
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_metric_result" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_metric_result" (
    "id"               BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_metric_result".NEXTVAL NOT NULL,
    "run_id"           VARCHAR(40),
    "metric_code"      VARCHAR(32)   NOT NULL,
    "metric_name"      VARCHAR(200),
    "domain_code"      VARCHAR(32),
    "period_type"      VARCHAR(16),
    "period_start"     TIMESTAMP     NOT NULL,
    "period_end"       TIMESTAMP,
    "depart_code"      VARCHAR(64)   DEFAULT 'ALL',
    "numerator"        DECIMAL(20,4),
    "denominator"      DECIMAL(20,4),
    "metric_value"     DECIMAL(20,6),
    "unit"             VARCHAR(32),
    "ext_json"         TEXT,
    "calc_status"      VARCHAR(16),
    "error_msg"        VARCHAR(1000),
    "compare_value"    DECIMAL(20,6),
    "compare_diff"     DECIMAL(12,6),
    "expression_version" INT,
    "calc_time"        TIMESTAMP,
    "update_time"      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    -- 值的归属：AUTO = 引擎计算产出；MANUAL = 人工录入且已填值。
    -- 重算时的保护范围靠这一列显式判断，不再依赖「calc_status='MANUAL' 且 metric_value 非空」
    -- 这种隐含约定 —— 隐含约定一旦被后人调整状态写法，人工录入的值就会在下次重算时被静默清空。
    "value_source"     VARCHAR(16)   DEFAULT 'AUTO',
    "operator"         VARCHAR(64),
    "manual_note"      VARCHAR(500),
    CONSTRAINT "pk_quality_metric_result" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_metric_result" IS '质控指标结果（周期×指标×部门；含分子分母与老系统对比，血缘第2层）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."numerator"        IS '分子（命中数）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."denominator"      IS '分母（同期总数）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."metric_value"     IS '指标值（率类已×100）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."ext_json"         IS '扩展明细 JSON（分型/分层等）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."calc_status"      IS '计算状态：OK/PLACEHOLDER/PENDING_SOURCE/MANUAL/NO_DATA/ERROR';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."compare_value"    IS '老系统同周期值（双跑核对）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."compare_diff"     IS '与服务端值的相对差异率';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."expression_version" IS '计算时所用的口径版本';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."value_source"   IS '值归属：AUTO=引擎计算 / MANUAL=人工录入（重算时受保护，不被覆盖）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."operator"       IS '人工录入的操作人（审计留痕；引擎写入的行此列为空）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."manual_note"    IS '人工录入备注（说明取数依据，便于事后复核）';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_quality_result" ON "zing_doctor_db_prod"."quality_metric_result" ("metric_code", "period_type", "period_start", "depart_code");
CREATE INDEX "zing_doctor_db_prod"."idx_quality_result_period" ON "zing_doctor_db_prod"."quality_metric_result" ("period_start", "domain_code");

-- ---------------------------------------------------------------------
-- 4) 算子 / SQL 追溯表（血缘第 3 层）
--    因为所有计算都经统一引擎，血缘是引擎自动捕获，不靠人工标注
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_calc_trace" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_calc_trace" (
    "id"                 BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_calc_trace".NEXTVAL NOT NULL,
    "run_id"             VARCHAR(40),
    "metric_code"        VARCHAR(32),
    "dim_key"            VARCHAR(64),
    "fact_name"          VARCHAR(64),
    "expression_version" INT,
    "sql_hash"           VARCHAR(64),
    "sql_text"           CLOB,
    "source_tables"      VARCHAR(1000),
    "operators"          VARCHAR(500),
    "scanned_rows"       BIGINT,
    "num_rows"           INT,
    "den_rows"           INT,
    "duration_ms"        BIGINT,
    "calc_time"          TIMESTAMP,
    CONSTRAINT "pk_quality_calc_trace" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_calc_trace" IS '质控计算追溯（血缘第3层：SQL、算子、扫描行数、表达式版本）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_trace"."sql_hash"      IS '编译后 SQL 指纹，口径变化可比对';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_trace"."operators"     IS '本指标用到的算子链，如 filter>derive>bucket>pick>aggregate';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_calc_trace"."scanned_rows"  IS '事实层扫描行数（性能瓶颈定位）';
CREATE INDEX "zing_doctor_db_prod"."idx_quality_trace_run" ON "zing_doctor_db_prod"."quality_calc_trace" ("run_id", "metric_code");

-- ---------------------------------------------------------------------
-- 5) 患者级命中明细（血缘第 4 层：数字 → 到人）
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_metric_patient" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_metric_patient" (
    "id"             BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_metric_patient".NEXTVAL NOT NULL,
    "run_id"         VARCHAR(40),
    "metric_code"    VARCHAR(32),
    "period_start"   TIMESTAMP,
    "depart_code"    VARCHAR(64),
    "patient_id"     VARCHAR(64),
    "in_hospital_no" VARCHAR(64),
    "patient_name"   VARCHAR(64),
    "in_numerator"   TINYINT     DEFAULT 0,
    "in_denominator" TINYINT     DEFAULT 0,
    "exclude_reason" VARCHAR(300),
    "raw_json"       TEXT,
    "create_time"    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_metric_patient" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_metric_patient" IS '质控指标患者级明细（血缘第4层：谁进分子、谁进分母、未命中原因）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_patient"."in_numerator"   IS '是否计入分子：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_patient"."in_denominator" IS '是否计入分母：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_patient"."exclude_reason" IS '未计入原因';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_patient"."raw_json"       IS '该患者参与计算的原始取值明细 JSON';
CREATE INDEX "zing_doctor_db_prod"."idx_quality_patient_metric" ON "zing_doctor_db_prod"."quality_metric_patient" ("metric_code", "period_start");
CREATE INDEX "zing_doctor_db_prod"."idx_quality_patient_no" ON "zing_doctor_db_prod"."quality_metric_patient" ("in_hospital_no");

-- ---------------------------------------------------------------------
-- 6) 月度汇总宽表（1-12 月横排，页面直读 + xlsx 导出）
-- ---------------------------------------------------------------------
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_quality_monthly_report" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."quality_monthly_report" (
    "id"          BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_quality_monthly_report".NEXTVAL NOT NULL,
    "year"        INT           NOT NULL,
    "index_code"  VARCHAR(32)   NOT NULL,
    "index_name"  VARCHAR(200),
    "domain_code" VARCHAR(32),
    "unit"        VARCHAR(32),
    "value_type"  VARCHAR(16),
    "depart_code" VARCHAR(64)   DEFAULT 'ALL',
    "m01" DECIMAL(20,6), "m02" DECIMAL(20,6), "m03" DECIMAL(20,6),
    "m04" DECIMAL(20,6), "m05" DECIMAL(20,6), "m06" DECIMAL(20,6),
    "m07" DECIMAL(20,6), "m08" DECIMAL(20,6), "m09" DECIMAL(20,6),
    "m10" DECIMAL(20,6), "m11" DECIMAL(20,6), "m12" DECIMAL(20,6),
    "q1" DECIMAL(20,6), "q2" DECIMAL(20,6), "q3" DECIMAL(20,6), "q4" DECIMAL(20,6),
    "year_total"  DECIMAL(20,6),
    "year_avg"    DECIMAL(20,6),
    "max_month"   TINYINT,
    "min_month"   TINYINT,
    "status"      TINYINT       DEFAULT 1,
    "update_time" TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_quality_monthly_report" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."quality_monthly_report" IS '质控月度汇总宽表（一行一个指标，1-12 月横排）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_monthly_report"."m01"        IS '1 月指标值';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_monthly_report"."q1"         IS '一季度汇总值';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_monthly_report"."year_total" IS '全年合计（数类求和；率类为空）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_monthly_report"."year_avg"   IS '全年平均（率类为月度均值；数类为月均）';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_quality_monthly" ON "zing_doctor_db_prod"."quality_monthly_report" ("year", "index_code", "depart_code");
CREATE INDEX "zing_doctor_db_prod"."idx_quality_monthly_domain" ON "zing_doctor_db_prod"."quality_monthly_report" ("domain_code");

-- ---------------------------------------------------------------------
-- 7) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" IN ('quality-board', 'quality-monthly');
INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('quality-board', '质控指标看板', '/page/quality-board',
     '质控指标中台：127 条指标按域分组展示、周期切换、点数字下钻分子分母与患者明细', 1),
    ('quality-monthly', '质控月度汇总', '/page/quality-monthly',
     '质控月度汇总：1-12 月横排对比，支持同比/环比与 xlsx 导出', 1);
