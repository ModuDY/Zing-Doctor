-- =====================================================================
-- 抗菌药物字典（HIS 药品字典同步表）
--
-- 背景：
--   抗菌药识别原本依赖 zing_abx_word_config 的「关键词白名单 + 黑名单」，
--   但 HIS 药品字典（ICU 库 zing_icu_db_prod.config_drug，字段 is_antibiotics）
--   才是"哪种药是抗菌药"的权威来源。本表用于承接 HIS 的抗菌药清单，
--   由 AbxDrugSyncTask 夜间定时同步（也可页面手动触发），
--   供 AbxDrugRecognizer 做「精确名/通用名/基名」匹配，显著提升识别率。
--
-- 数据流：
--   zing_icu_db_prod.config_drug (is_antibiotics='1')
--        │  夜间定时 / 手动触发（只读数据源，不写 ICU）
--        ▼
--   zing_doctor_db_prod.zing_abx_drug_dict（本表）
--        │  内存快照（5 分钟 TTL）
--        ▼
--   AbxDrugRecognizer#isAntibiotic / isBroadSpectrum / isNonAntibiotic
--        → 脓毒症集束化、医生交班览表、当前抗菌药、MDRO/DDD/经验性决策
--
-- 幂等：重复执行本脚本前请先 DROP TABLE（或改为 CREATE TABLE IF NOT EXISTS）。
-- 执行方式（disql，SYSDBA 执行）：
--   SQL> start /opt/zing-doctor/sql/06_abx_drug_dict.sql
-- =====================================================================

CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_abx_drug_dict" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."zing_abx_drug_dict" (
    "id"                BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_zing_abx_drug_dict".NEXTVAL NOT NULL,
    "drug_code"         VARCHAR(64)  NOT NULL,
    "drug_name"         VARCHAR(255),
    "drug_short_name"   VARCHAR(255),
    "drug_normal_name"  VARCHAR(255),
    "drug_pinyin"       VARCHAR(255),
    "spec"              VARCHAR(255),
    "dose"              VARCHAR(64),
    "unit_code"         VARCHAR(32),
    "drug_factory_name" VARCHAR(255),
    "is_antibiotics"    VARCHAR(8),
    "antibiotics_color" VARCHAR(32),
    "sync_time"         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "status"            TINYINT      DEFAULT 1 NOT NULL,
    "del_flag"          TINYINT      DEFAULT 0 NOT NULL,
    "create_time"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_abx_drug_dict" PRIMARY KEY ("id")
);

COMMENT ON TABLE  "zing_doctor_db_prod"."zing_abx_drug_dict" IS '抗菌药物字典（HIS config_drug 中 is_antibiotics=1 的同步副本）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_code"         IS 'HIS 药品编码（唯一键，用于增量比对）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_name"         IS '药品名称（含商品名，如 盐酸克林霉素胶囊(特丽仙)）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_short_name"   IS '药品简称';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_normal_name"  IS '通用名（无商品名）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_pinyin"       IS '拼音码';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."spec"              IS '规格';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."dose"              IS '单次剂量';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."unit_code"         IS '剂量单位';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."drug_factory_name" IS '生产厂家';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."is_antibiotics"    IS 'HIS 抗菌药标记（同步源固定为 1）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."antibiotics_color" IS 'HIS 抗菌药标识色（前端着色的数据来源）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."sync_time"         IS '最近一次同步写入时间（内容无变化时不刷新）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."status"            IS '状态：1 在库（HIS 仍标记为抗菌药）0 已失效（HIS 已取消抗菌药标记）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."del_flag"          IS '删除标记：0 正常';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."create_time"       IS '创建时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_abx_drug_dict"."update_time"       IS '更新时间';

-- 唯一索引：药品编码，夜间同步按它做增量比对（存在则更新，不存在则新增）
CREATE UNIQUE INDEX "zing_doctor_db_prod"."uk_zing_abx_drug_dict_code"
    ON "zing_doctor_db_prod"."zing_abx_drug_dict" ("drug_code");

-- 识别时按状态过滤，建立普通索引
CREATE INDEX "zing_doctor_db_prod"."idx_zing_abx_drug_dict_status"
    ON "zing_doctor_db_prod"."zing_abx_drug_dict" ("status");
