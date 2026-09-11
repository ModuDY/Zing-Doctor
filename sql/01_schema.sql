-- =====================================================================
-- zing-doctor 医生系统 - 自有数据库结构（达梦 DM8）
--
-- 说明：
--   1. 本库存放医生系统自身产生的业务数据（决策记录、外链访问日志、页面配置等）
--   2. 分析判断所需的患者/医嘱/检验/微生物数据来源于 ICU 系统库 zing_icu_db_prod，
--      本库不复制 ICU 业务主数据，仅通过只读数据源访问。
--   3. 数据库为达梦 DM8。医生系统统一用 SYSDBA 连接达梦（与 ICU 系统访问方式一致），
--      通过显式模式前缀访问各模式。本脚本建表于模式 zing_doctor_db_prod 下
--      （SQL 中的 "zing_doctor_db_prod"."xxx" 即显式模式限定）。
--      部署前先用 00_init_user.sql（SYSDBA 执行）创建该模式。
--   4. 执行方式（disql，SYSDBA 执行）：
--        disql SYSDBA/Sa_20250815@100.120.1.102:14236
--        SQL> start /opt/zing-doctor/sql/01_schema.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 页面注册表：每个可外链打开的功能页面在此登记（外链 pageCode 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_page_config" (
    "id"            BIGINT       IDENTITY(1,1) NOT NULL,
    "page_code"     VARCHAR(64)  NOT NULL,
    "page_name"     VARCHAR(128) NOT NULL,
    "frontend_path" VARCHAR(255) NOT NULL,
    "remark"        VARCHAR(255),
    "status"        TINYINT      DEFAULT 1 NOT NULL,
    "create_time"   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_page_config" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_page_config" IS '页面注册表';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."page_code"     IS '页面编码（外链 pageCode，唯一）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."page_name"     IS '页面名称';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."frontend_path" IS '前端路由路径，如 /page/abx-patient-list';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."remark"        IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."status"        IS '状态：1 启用 0 停用';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."create_time"   IS '创建时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_page_config"."update_time"   IS '更新时间';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_zing_page_config_page_code"
    ON "zing_doctor_db_prod"."zing_page_config" ("page_code");

-- ---------------------------------------------------------------------
-- 外链访问日志：记录外部系统（ICU）外链进入医生系统的访问
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_external_access_log" (
    "id"            BIGINT      IDENTITY(1,1) NOT NULL,
    "page_code"     VARCHAR(64) NOT NULL,
    "source_system" VARCHAR(64) DEFAULT 'icu' NOT NULL,
    "ip"            VARCHAR(64),
    "biz_params"    TEXT,
    "access_time"   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_external_access_log" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_external_access_log" IS '外链访问日志';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_external_access_log"."page_code"     IS '被访问页面编码';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_external_access_log"."source_system" IS '来源系统标识';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_external_access_log"."ip"            IS '来源 IP';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_external_access_log"."biz_params"    IS '业务参数（JSON）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_external_access_log"."access_time"   IS '访问时间';
CREATE INDEX "zing_doctor_db_prod"."idx_zing_external_access_log_page" ON "zing_doctor_db_prod"."zing_external_access_log" ("page_code");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_external_access_log_time" ON "zing_doctor_db_prod"."zing_external_access_log" ("access_time");

-- ---------------------------------------------------------------------
-- 抗感染决策记录：第一维度（经验性抗感染治疗决策）医生决策留痕
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_decision_record" (
    "id"               BIGINT       IDENTITY(1,1) NOT NULL,
    "patient_id"       VARCHAR(64)  NOT NULL,
    "patient_no"       VARCHAR(64),
    "page_code"        VARCHAR(64)  DEFAULT 'abx-decision',
    "source_system"    VARCHAR(64)  DEFAULT 'icu' NOT NULL,
    "infection_type"   VARCHAR(64),
    "septic_shock"     TINYINT      DEFAULT 0 NOT NULL,
    "mrsa_risk"        TINYINT      DEFAULT 0 NOT NULL,
    "mdr_risk"         TINYINT      DEFAULT 0 NOT NULL,
    "fungal_risk"      TINYINT      DEFAULT 0 NOT NULL,
    "recommended_plan" VARCHAR(500),
    "doctor_decision"  VARCHAR(500),
    "decision_status"  VARCHAR(32)  DEFAULT 'pending' NOT NULL,
    "doctor_id"        VARCHAR(64),
    "doctor_name"      VARCHAR(64),
    "create_time"      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_decision_record" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_decision_record" IS '抗感染决策记录';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."patient_id"       IS 'ICU 患者 ID';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."patient_no"       IS '住院号/就诊号';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."page_code"        IS '来源页面';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."source_system"    IS '来源系统';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."infection_type"   IS '感染类型/部位';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."septic_shock"     IS '是否脓毒性休克：0 否 1 是';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."mrsa_risk"        IS 'MRSA 高风险：0 否 1 是';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."mdr_risk"         IS 'MDR 高风险：0 否 1 是';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."fungal_risk"      IS '真菌高风险：0 否 1 是';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."recommended_plan" IS '系统推荐方案摘要';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."doctor_decision"  IS '医生最终决策';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."decision_status"  IS '状态：pending 待决策 / accepted 采纳 / declined 拒绝';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."doctor_id"        IS '决策医生 ID';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_decision_record"."doctor_name"      IS '决策医生姓名';
CREATE INDEX "zing_doctor_db_prod"."idx_zing_decision_record_patient" ON "zing_doctor_db_prod"."zing_decision_record" ("patient_id");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_decision_record_status"  ON "zing_doctor_db_prod"."zing_decision_record" ("decision_status");

-- ---------------------------------------------------------------------
-- 抗感染方案推荐日志：一次决策记录对应的系统推荐方案明细（可多条）
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_advice_log" (
    "id"                 BIGINT       IDENTITY(1,1) NOT NULL,
    "decision_record_id" BIGINT       NOT NULL,
    "drug_name"          VARCHAR(128) NOT NULL,
    "dose_plan"          VARCHAR(255),
    "route"              VARCHAR(32),
    "advice_level"       VARCHAR(16),
    "reason"             VARCHAR(500),
    "evidence"           VARCHAR(500),
    "create_time"        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_advice_log" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_advice_log" IS '抗感染方案推荐日志';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."decision_record_id" IS '关联决策记录 ID';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."drug_name"         IS '推荐药品/方案名';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."dose_plan"         IS '剂量方案';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."route"             IS '给药途径';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."advice_level"      IS '建议强度（如 强/弱，或 R1/R2/R3）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."reason"            IS '推荐理由';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_advice_log"."evidence"          IS '证据说明（指南/共识出处）';
CREATE INDEX "zing_doctor_db_prod"."idx_zing_advice_log_record" ON "zing_doctor_db_prod"."zing_advice_log" ("decision_record_id");

-- ---------------------------------------------------------------------
-- 抗菌药物 DDD 值配置表：第三维度（使用强度分析）的核心知识库
-- 初始数据按 WHO ATC/DDD 最新版本录入，支持后台页面修改
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_ddd_config" (
    "id"            BIGINT       IDENTITY(1,1) NOT NULL,
    "drug_name"     VARCHAR(128) NOT NULL,
    "atc_code"      VARCHAR(32),
    "ddd_value"     DECIMAL(10,4) NOT NULL,
    "ddd_unit"      VARCHAR(16)  DEFAULT 'g' NOT NULL,
    "route"         VARCHAR(16)  DEFAULT '注射' NOT NULL,
    "manage_level"  VARCHAR(16)  DEFAULT '非限制' NOT NULL,
    "drug_class"    VARCHAR(64),
    "keywords"      VARCHAR(500),
    "remark"        VARCHAR(500),
    "status"        TINYINT      DEFAULT 1 NOT NULL,
    "create_time"   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_ddd_config" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_ddd_config" IS '抗菌药物DDD值配置表';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."drug_name"    IS '药品通用名';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."atc_code"     IS 'WHO ATC编码';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."ddd_value"    IS 'DDD值（限定日剂量）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."ddd_unit"     IS 'DDD单位（g/mg/MU等）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."route"        IS '给药途径：注射/口服';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."manage_level" IS '管理级别：非限制/限制/特殊';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."drug_class"   IS '药物分类（青霉素类/头孢类/碳青霉烯类等）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."keywords"     IS '匹配关键词（逗号分隔，用于医嘱名称模糊匹配）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."remark"       IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_ddd_config"."status"       IS '状态：1 启用 0 停用';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_zing_ddd_config_drug_route"
    ON "zing_doctor_db_prod"."zing_ddd_config" ("drug_name", "route");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_ddd_config_class" ON "zing_doctor_db_prod"."zing_ddd_config" ("drug_class");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_ddd_config_level" ON "zing_doctor_db_prod"."zing_ddd_config" ("manage_level");

-- ---------------------------------------------------------------------
-- 第四维度：细菌培养检出监测配置表
-- 用于配置细菌分类（革兰阳性/阴性/真菌）、高风险细菌列表、标本类型等
-- 由于 ICU 库细菌名称中无耐药关键词，MDRO 精确判定需药敏结果支持，
-- 本表用于细菌分类统计和高风险细菌标记。
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_mdro_config" (
    "id"             BIGINT       IDENTITY(1,1) NOT NULL,
    "config_type"    VARCHAR(20)  NOT NULL,
    "bacteria_name"  VARCHAR(128),
    "bacteria_class" VARCHAR(20),
    "is_high_risk"   TINYINT      DEFAULT 0 NOT NULL,
    "keywords"       VARCHAR(500),
    "remark"         VARCHAR(500),
    "status"         TINYINT      DEFAULT 1 NOT NULL,
    "create_time"    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_mdro_config" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_mdro_config" IS '细菌培养监测配置表（第四维度）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."config_type"    IS '配置类型：bacteria_class细菌分类, high_risk高风险细菌, specimen标本类型';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."bacteria_name"  IS '细菌名称';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."bacteria_class" IS '细菌分类：gram_positive革兰阳性, gram_negative革兰阴性, fungi真菌, other其他';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."is_high_risk"   IS '是否高风险细菌：1是0否（ICU常见MDRO风险菌）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."keywords"       IS '匹配关键词（逗号分隔，用于细菌名称模糊匹配分类）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."remark"         IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_mdro_config"."status"         IS '状态：1 启用 0 停用';
CREATE INDEX "zing_doctor_db_prod"."idx_zing_mdro_config_type"  ON "zing_doctor_db_prod"."zing_mdro_config" ("config_type");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_mdro_config_class" ON "zing_doctor_db_prod"."zing_mdro_config" ("bacteria_class");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_mdro_config_risk"  ON "zing_doctor_db_prod"."zing_mdro_config" ("is_high_risk");

-- ---------------------------------------------------------------------
-- 脓毒症休克集束化治疗记录表
-- 用于记录脓毒症/感染性休克患者1H/3H/6H集束化治疗完成情况
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."sepsis_bundle_record" (
    "id"                  BIGINT       IDENTITY(1,1) NOT NULL,
    "patient_id"          VARCHAR(64),
    "in_hospital_no"      VARCHAR(64)  NOT NULL,
    "patient_name"        VARCHAR(64),
    "depart_code"         VARCHAR(64),
    "diagnosis_time"      TIMESTAMP,
    "in_depart_time"      TIMESTAMP,
    "bundle_1h_completed" TINYINT      DEFAULT 0,
    "bundle_3h_completed" TINYINT      DEFAULT 0,
    "bundle_6h_completed" TINYINT      DEFAULT 0,
    "bundle_1h_data"      TEXT,
    "bundle_3h_data"      TEXT,
    "bundle_6h_data"      TEXT,
    "infection_site"      VARCHAR(255),
    "pathogen"            VARCHAR(255),
    "antibiotic"          VARCHAR(255),
    "fluid_reason"        TEXT,
    "status"              TINYINT      DEFAULT 1,
    "create_by"           VARCHAR(64),
    "create_time"         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"           VARCHAR(64),
    "update_time"         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_sepsis_bundle_record" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."sepsis_bundle_record" IS '脓毒症休克集束化治疗记录表';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."patient_id"          IS '患者ID';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."in_hospital_no"      IS '住院号';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."patient_name"        IS '患者姓名';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."depart_code"         IS '科室编码';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."diagnosis_time"      IS '脓毒症确诊时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."in_depart_time"      IS '入科时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_1h_completed" IS '1H集束化是否完成：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_3h_completed" IS '3H集束化是否完成：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_6h_completed" IS '6H集束化是否完成：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_1h_data"      IS '1H项目详细数据（JSON）';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_3h_data"      IS '3H项目详细数据（JSON）';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."bundle_6h_data"      IS '6H项目详细数据（JSON）';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."infection_site"      IS '感染部位';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."pathogen"            IS '致病菌';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."antibiotic"          IS '抗生素';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."fluid_reason"        IS '液体复苏未达30ml/kg原因（JSON）';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."status"              IS '状态：1 正常 0 已删除';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."create_by"           IS '创建人';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."create_time"         IS '创建时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."update_by"           IS '更新人';
COMMENT ON COLUMN "zing_doctor_db_prod"."sepsis_bundle_record"."update_time"         IS '更新时间';
CREATE INDEX "zing_doctor_db_prod"."idx_sepsis_bundle_patient" ON "zing_doctor_db_prod"."sepsis_bundle_record" ("in_hospital_no");
CREATE INDEX "zing_doctor_db_prod"."idx_sepsis_bundle_depart"  ON "zing_doctor_db_prod"."sepsis_bundle_record" ("depart_code");
CREATE INDEX "zing_doctor_db_prod"."idx_sepsis_bundle_time"    ON "zing_doctor_db_prod"."sepsis_bundle_record" ("diagnosis_time");

-- ---------------------------------------------------------------------
-- 抗菌药物识别词库配置表（脓毒症集束化：广谱抗菌药白名单 + 非抗菌药黑名单）
-- 后台页面 abx-word-config 可增删改，启动/评估时加载，表空时回退内置默认
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_abx_word_config" (
    "id"          BIGINT       IDENTITY(1,1) NOT NULL,
    "word_type"   VARCHAR(32)  NOT NULL,
    "keyword"     VARCHAR(128) NOT NULL,
    "category"    VARCHAR(64),
    "remark"      VARCHAR(255),
    "status"      TINYINT      DEFAULT 1,
    "create_time" TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_abx_word_config" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_abx_word_config" IS '抗菌药物识别词库配置';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_word_config"."word_type"   IS '词类型：broad_spectrum 广谱抗菌药白名单 / non_antibiotic 非抗菌药黑名单';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_word_config"."keyword"     IS '匹配关键词';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_word_config"."category"    IS '分组（如：电解质/抗组胺/广谱抗菌药）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_word_config"."remark"      IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_word_config"."status"      IS '状态：1启用 0停用';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_zing_abx_word_type_kw" ON "zing_doctor_db_prod"."zing_abx_word_config" ("word_type", "keyword");
CREATE INDEX "zing_doctor_db_prod"."idx_zing_abx_word_type_status" ON "zing_doctor_db_prod"."zing_abx_word_config" ("word_type", "status");

-- ---------------------------------------------------------------------
-- 第五维度：医生交班览表 - 医生手工交班记录
-- 病情变化（condition_change）为 P0 手工录入字段；下一班计划/待办字段 P1 预留
-- 一个患者一个"已封板全天班次"一条，按 (in_hospital_no, shift_begin_time) 唯一
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."zing_doctor_handover" (
    "id"               BIGINT       IDENTITY(1,1) NOT NULL,
    "patient_id"       VARCHAR(64),
    "in_hospital_no"   VARCHAR(64)  NOT NULL,
    "patient_name"     VARCHAR(64),
    "depart_code"      VARCHAR(64),
    "shift_begin_time" TIMESTAMP,
    "shift_end_time"   TIMESTAMP,
    "condition_change" TEXT,
    "plan_next"        TEXT,
    "todo_note"        TEXT,
    "status"           TINYINT      DEFAULT 1,
    "create_by"        VARCHAR(64),
    "create_time"      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"        VARCHAR(64),
    "update_time"      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_doctor_handover" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."zing_doctor_handover" IS '医生交班览表-手工交班记录';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."patient_id"       IS '患者ID（patient_info.id）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."in_hospital_no"   IS '住院号';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."patient_name"     IS '患者姓名';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."depart_code"      IS '科室编码（sys_depart.org_code）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."shift_begin_time" IS '绑定班次开始时间（上一完整全天班起点）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."shift_end_time"   IS '绑定班次结束时间（上一完整全天班终点）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."condition_change" IS '本班病情变化（医生手工录入，P0）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."plan_next"        IS '下一班诊疗计划（P1预留）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."todo_note"        IS '待办事项（P1预留）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."status"           IS '状态：1 正常 0 已删除';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."create_by"        IS '创建人（交班医生）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."create_time"      IS '创建时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."update_by"        IS '更新人';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_doctor_handover"."update_time"      IS '更新时间';
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_zdh_patient_shift" ON "zing_doctor_db_prod"."zing_doctor_handover" ("in_hospital_no", "shift_begin_time");
CREATE INDEX "zing_doctor_db_prod"."idx_zdh_depart" ON "zing_doctor_db_prod"."zing_doctor_handover" ("depart_code");
CREATE INDEX "zing_doctor_db_prod"."idx_zdh_shift"  ON "zing_doctor_db_prod"."zing_doctor_handover" ("shift_begin_time");

-- ---------------------------------------------------------------------
-- APACHE II 评分记录表
-- 支持多次评分（入科时/24h/48h/自定义），每次评分一条记录
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."apache2_score_record" (
    "id"                BIGINT       IDENTITY(1,1) NOT NULL,
    "patient_id"        VARCHAR(64),
    "in_hospital_no"    VARCHAR(64)  NOT NULL,
    "patient_name"      VARCHAR(64),
    "depart_code"       VARCHAR(64),
    "score_time"        TIMESTAMP,
    "score_type"        VARCHAR(32),
    "age_score"         INT          DEFAULT 0,
    "chronic_score"     INT          DEFAULT 0,
    "gcs_score"         INT          DEFAULT 0,
    "physiology_score"  INT          DEFAULT 0,
    "total_score"       INT          DEFAULT 0,
    "mortality_rate"    DECIMAL(5,2),
    "aps_data"          TEXT,
    "diagnosis_type"    VARCHAR(32),
    "diagnosis_weight"  DECIMAL(8,4),
    "emergency_surgery" TINYINT      DEFAULT 0,
    "chronic_health"    VARCHAR(32),
    "gcs_detail"        VARCHAR(64),
    "data_start_time"   TIMESTAMP,
    "data_end_time"     TIMESTAMP,
    "remark"            VARCHAR(500),
    "pdf_data"          TEXT,
    "pdf_name"          VARCHAR(200),
    "status"            TINYINT      DEFAULT 1,
    "create_by"         VARCHAR(64),
    "create_time"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"         VARCHAR(64),
    "update_time"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_apache2_score_record" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."apache2_score_record" IS 'APACHE II评分记录表';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."patient_id"        IS '患者ID（patient_info.id）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."in_hospital_no"    IS '住院号';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."patient_name"      IS '患者姓名';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."depart_code"       IS '科室编码';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."score_time"        IS '评分时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."score_type"        IS '评分时机：admission入科时/24h/48h/custom自定义';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."age_score"         IS 'A年龄评分';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."chronic_score"     IS 'B慢性健康状况评分';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."gcs_score"         IS 'C GCS评分（15-GCS）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."physiology_score"  IS 'D急性生理评分（12项合计）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."total_score"       IS 'APACHE II总分（A+B+C+D）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."mortality_rate"    IS '预计院内死亡率（%）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."aps_data"          IS '12项急性生理数据JSON';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."diagnosis_type"    IS '疾病分类：nonoperative非手术/operative手术/none以上都不是';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."diagnosis_weight"  IS '诊断权重';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."emergency_surgery" IS '是否急诊手术：1是 0否';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."chronic_health"    IS '慢性健康状况：none/nonoperative/elective';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."gcs_detail"        IS 'GCS明细（E/V/M）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."data_start_time"   IS '取数开始时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."data_end_time"     IS '取数结束时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."remark"            IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."pdf_data"          IS '评分文书PDF的Base64（不含data前缀）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."pdf_name"          IS 'PDF文件名';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."status"            IS '状态：1正常 0已删除';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."create_by"         IS '创建人（评分医生）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."create_time"       IS '创建时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."update_by"         IS '更新人';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_score_record"."update_time"       IS '更新时间';
CREATE INDEX "zing_doctor_db_prod"."idx_apache2_patient" ON "zing_doctor_db_prod"."apache2_score_record" ("in_hospital_no");
CREATE INDEX "zing_doctor_db_prod"."idx_apache2_depart"  ON "zing_doctor_db_prod"."apache2_score_record" ("depart_code");
CREATE INDEX "zing_doctor_db_prod"."idx_apache2_time"    ON "zing_doctor_db_prod"."apache2_score_record" ("score_time");

-- ---------------------------------------------------------------------
-- APACHE II 配置表
-- 监护item_code配置、检验lis_item_code配置、慢性健康关键词配置
-- ---------------------------------------------------------------------
CREATE TABLE "zing_doctor_db_prod"."apache2_config" (
    "id"           BIGINT       IDENTITY(1,1) NOT NULL,
    "config_type"  VARCHAR(32)  NOT NULL,
    "config_key"   VARCHAR(128) NOT NULL,
    "config_value" VARCHAR(500),
    "item_name"    VARCHAR(128),
    "remark"       VARCHAR(255),
    "sort_no"      INT          DEFAULT 1,
    "status"       TINYINT      DEFAULT 1,
    "create_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_apache2_config" PRIMARY KEY ("id")
);
COMMENT ON TABLE  "zing_doctor_db_prod"."apache2_config" IS 'APACHE II配置表';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."config_type"  IS '配置类型：observe_item监护item_code/lis_item检验item_code/chronic_keyword慢性健康关键词';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."config_key"   IS '配置键（如：temperature/heart_rate/sodium/potassium/creatinine/hct/wbc）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."config_value" IS '配置值（item_code或lis_item_code，多个用逗号分隔）';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."item_name"    IS '项目名称';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."remark"       IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."sort_no"      IS '排序号';
COMMENT ON COLUMN "zing_doctor_db_prod"."apache2_config"."status"       IS '状态：1启用 0停用';
CREATE INDEX "zing_doctor_db_prod"."idx_apache2_config_type" ON "zing_doctor_db_prod"."apache2_config" ("config_type", "status");
