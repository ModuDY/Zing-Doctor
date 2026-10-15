-- =====================================================================
-- 55_hemodynamics_adl.sql
-- 手工录入表：血流动力学/心功能（patient_doc_hemodynamics） + ADL Barthel（patient_doc_adl）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/55_hemodynamics_adl.sql
--
-- 功能：/page/hemodynamics（血流动力学与心功能监测 + ADL 评估）
-- 后端：module/hemodynamics（/api/hemodynamics/*）、module/adl（/api/adl/*）
-- 派生：SVR = (MAP − CVP)/CO × 80（服务端计算）；ADL 总分 = 十项求和（服务端计算）
-- 幂等：可重复执行。
-- =====================================================================

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'patient_doc_hemodynamics';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."patient_doc_hemodynamics" (
            "id"              BIGINT NOT NULL,
            "patient_id"      VARCHAR(64) NOT NULL,
            "in_hospital_no"  VARCHAR(64),
            "patient_name"    VARCHAR(64),
            "depart_code"     VARCHAR(64),
            "measure_time"    TIMESTAMP NOT NULL,
            "sbp"             NUMBER(6,1),
            "dbp"             NUMBER(6,1),
            "map_val"         NUMBER(6,1),
            "cvp"             NUMBER(6,1),
            "co"              NUMBER(6,2),
            "svr"             NUMBER(8,1),
            "ef"              NUMBER(5,1),
            "method"          VARCHAR(32),
            "remark"          VARCHAR(1000),
            "status"          TINYINT DEFAULT 1 NOT NULL,
            "create_by"       VARCHAR(64),
            "create_time"     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_by"       VARCHAR(64),
            "update_time"     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_patient_doc_hemodynamics" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'patient_doc_hemodynamics'
       AND INDEX_NAME = 'idx_phd_patient_time';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_phd_patient_time" ON "zing_doctor_db_prod"."patient_doc_hemodynamics" ("patient_id", "measure_time")';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'patient_doc_adl';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."patient_doc_adl" (
            "id"              BIGINT NOT NULL,
            "patient_id"      VARCHAR(64) NOT NULL,
            "in_hospital_no"  VARCHAR(64),
            "patient_name"    VARCHAR(64),
            "depart_code"     VARCHAR(64),
            "adl_time"        TIMESTAMP NOT NULL,
            "feeding"         TINYINT,
            "bathing"         TINYINT,
            "grooming"        TINYINT,
            "dressing"        TINYINT,
            "bowels"          TINYINT,
            "bladder"         TINYINT,
            "toilet"          TINYINT,
            "transfer"        TINYINT,
            "mobility"        TINYINT,
            "stairs"          TINYINT,
            "total_score"     TINYINT,
            "remark"          VARCHAR(1000),
            "status"          TINYINT DEFAULT 1 NOT NULL,
            "create_by"       VARCHAR(64),
            "create_time"     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_by"       VARCHAR(64),
            "update_time"     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_patient_doc_adl" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'patient_doc_adl'
       AND INDEX_NAME = 'idx_padl_patient_time';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_padl_patient_time" ON "zing_doctor_db_prod"."patient_doc_adl" ("patient_id", "adl_time")';
    END IF;
END;
/
