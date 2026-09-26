-- 33_antibiotic_reassessment.sql
-- 抗感染：48～72 小时复评任务与复评留痕（达梦 DM8）
--
-- 复评是抗感染决策后的独立临床任务，不改写历史决策记录。
-- 任务状态：PENDING 待复评 / COMPLETED 已完成 / SKIPPED 已跳过 / VOID 已作废。
-- 第一版定位为临床决策支持与复评留痕，不直接执行医嘱。
-- =====================================================================

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND TABLE_NAME = 'patient_doc_abx_reassessment';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."patient_doc_abx_reassessment" (
            "id"                   BIGINT NOT NULL,
            "decision_record_id"   BIGINT NOT NULL,
            "patient_id"           VARCHAR(64) NOT NULL,
            "patient_no"           VARCHAR(64),
            "in_hospital_no"       VARCHAR(64),
            "depart_code"          VARCHAR(64),
            "review_due_time"      TIMESTAMP NOT NULL,
            "review_time"          TIMESTAMP,
            "review_status"        VARCHAR(16) DEFAULT ''PENDING'' NOT NULL,
            "culture_summary"      VARCHAR(1000),
            "clinical_response"    VARCHAR(1000),
            "pct_trend"            VARCHAR(500),
            "decision_action"      VARCHAR(32),
            "doctor_decision"      VARCHAR(1000),
            "doctor_id"            VARCHAR(64),
            "doctor_name"          VARCHAR(64),
            "remark"               VARCHAR(1000),
            "void_flag"            TINYINT DEFAULT 0 NOT NULL,
            "create_time"          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_time"          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_patient_doc_abx_reassessment" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_SEQUENCES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND SEQUENCE_NAME = 'SEQ_patient_doc_abx_reassessment';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_patient_doc_abx_reassessment" START WITH 1 INCREMENT BY 1';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND INDEX_NAME = 'idx_abx_reassessment_patient';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_abx_reassessment_patient" ON "zing_doctor_db_prod"."patient_doc_abx_reassessment" ("patient_id", "review_status")';
    END IF;

    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND INDEX_NAME = 'idx_abx_reassessment_due';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_abx_reassessment_due" ON "zing_doctor_db_prod"."patient_doc_abx_reassessment" ("review_status", "review_due_time")';
    END IF;

    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND INDEX_NAME = 'idx_abx_reassessment_record';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_abx_reassessment_record" ON "zing_doctor_db_prod"."patient_doc_abx_reassessment" ("decision_record_id")';
    END IF;
END;
/

COMMENT ON TABLE "zing_doctor_db_prod"."patient_doc_abx_reassessment" IS '抗感染48-72小时复评任务与留痕';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."review_status" IS 'PENDING待复评/COMPLETED已完成/SKIPPED已跳过/VOID已作废';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."decision_action" IS 'CONTINUE继续/DE_ESCALATE降阶/ESCALATE升阶/SWITCH换药/STOP停药/OTHER其他';
COMMIT;
