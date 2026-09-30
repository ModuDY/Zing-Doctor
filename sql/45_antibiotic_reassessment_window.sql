-- 35_antibiotic_reassessment_window.sql
-- 达梦 DM8：将“创建后48小时即逾期”升级为 48～72 小时复评窗口。
-- 已有记录缺少首次实际给药时间，明确按决策/任务创建时间回填，不冒充真实给药时间。

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'PATIENT_DOC_ABX_REASSESSMENT'
       AND UPPER(COLUMN_NAME) = 'TREATMENT_START_TIME';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."patient_doc_abx_reassessment" ADD "treatment_start_time" TIMESTAMP';
    END IF;

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'PATIENT_DOC_ABX_REASSESSMENT'
       AND UPPER(COLUMN_NAME) = 'REVIEW_OPEN_TIME';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."patient_doc_abx_reassessment" ADD "review_open_time" TIMESTAMP';
    END IF;

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'PATIENT_DOC_ABX_REASSESSMENT'
       AND UPPER(COLUMN_NAME) = 'TIME_SOURCE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."patient_doc_abx_reassessment" ADD "time_source" VARCHAR(32)';
    END IF;
END;
/

UPDATE "zing_doctor_db_prod"."patient_doc_abx_reassessment"
   SET "treatment_start_time" = COALESCE("treatment_start_time", "create_time"),
       "review_open_time" = COALESCE("review_open_time", DATEADD(HOUR, 48, "create_time")),
       "review_due_time" = CASE WHEN "review_status" = 'PENDING' THEN DATEADD(HOUR, 72, "create_time")
                                ELSE "review_due_time" END,
       "time_source" = COALESCE("time_source", 'DECISION_ACCEPTED')
 WHERE "treatment_start_time" IS NULL
    OR "review_open_time" IS NULL
    OR "time_source" IS NULL;

COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."treatment_start_time" IS '复评计时起点；优先首次实际给药时间';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."review_open_time" IS '建议复评窗口开放时间（计时起点后48小时）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."review_due_time" IS '建议复评窗口截止时间（计时起点后72小时）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_abx_reassessment"."time_source" IS 'FIRST_ADMINISTRATION首次实际给药/DECISION_ACCEPTED决策采纳时间';

-- 一次决策只能有一条复评任务；若历史上已有重复数据，此处会明确失败，需先清理重复记录。
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(INDEX_NAME) = 'UX_ABX_REASSESSMENT_DECISION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "zing_doctor_db_prod"."ux_abx_reassessment_decision" ON "zing_doctor_db_prod"."patient_doc_abx_reassessment" ("decision_record_id")';
    END IF;
END;
/
COMMIT;
