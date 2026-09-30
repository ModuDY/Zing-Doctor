-- 44_patient_round_record.sql
-- 医生查房记录表（达梦 DM8）
--
-- 查房记录是患者日级的诊疗计划记录，与班次级的交班表分开：
--   交班表 patient_doc_handover_record：班次级，面向交接
--   查房表 patient_doc_round_record：患者日级，面向当日诊疗计划
-- 不能复用同一张表，否则交班内容被修改后查房历史也会变，无法追溯。
--
-- 同一患者同一天只保留一份，保存时自动覆盖更新（Service 层控制）。
-- =====================================================================

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND TABLE_NAME = 'patient_doc_round_record';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."patient_doc_round_record" (
            "id"                   BIGINT NOT NULL,
            "patient_id"           VARCHAR(64) NOT NULL,
            "in_hospital_no"       VARCHAR(64),
            "patient_name"         VARCHAR(64),
            "depart_code"          VARCHAR(64),
            "round_date"           DATE NOT NULL,
            "main_problem"         VARCHAR(1000),
            "infection_judgment"   VARCHAR(1000),
            "respiratory_plan"     VARCHAR(1000),
            "circulatory_plan"     VARCHAR(1000),
            "renal_sedation_plan"  VARCHAR(1000),
            "abx_plan"             VARCHAR(1000),
            "recheck_items"        VARCHAR(1000),
            "treatment_goal"       VARCHAR(1000),
            "tomorrow_focus"       VARCHAR(1000),
            "status"               TINYINT DEFAULT 1 NOT NULL,
            "create_by"            VARCHAR(64),
            "create_time"          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_by"            VARCHAR(64),
            "update_time"          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_patient_doc_round_record" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_SEQUENCES
     WHERE UPPER(SEQUENCE_OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(SEQUENCE_NAME)  = 'SEQ_PATIENT_DOC_ROUND_RECORD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_patient_doc_round_record" START WITH 1 INCREMENT BY 1';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND INDEX_NAME = 'idx_round_patient_date';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "zing_doctor_db_prod"."idx_round_patient_date" ON "zing_doctor_db_prod"."patient_doc_round_record" ("patient_id", "round_date")';
    END IF;
END;
/

COMMENT ON TABLE "zing_doctor_db_prod"."patient_doc_round_record" IS '医生查房记录（患者日级诊疗计划）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."round_date" IS '查房日期（同一患者同一天只保留一份）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."main_problem" IS '今日主要问题';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."infection_judgment" IS '感染判断';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."respiratory_plan" IS '呼吸支持计划';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."circulatory_plan" IS '循环支持计划';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."renal_sedation_plan" IS '镇静镇痛/肾脏支持计划';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."abx_plan" IS '抗菌药调整计划';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."recheck_items" IS '今日复查项目';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."treatment_goal" IS '治疗目标';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_round_record"."tomorrow_focus" IS '明日重点';
COMMIT;
