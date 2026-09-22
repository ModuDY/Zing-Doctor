-- =====================================================================
-- 25_rename_clinical_tables.sql
-- 医生库表名对齐 ICU 命名规范（第二批：临床业务表，共 11 张）
--
-- 背景：
--   第二批动的是临床业务表，需要在「这些业务由医生系统自建表」这个口径下执行 ——
--   ICU 源库里本来就有同类载体且已有数据：
--       patient_prone_position / patient_prone_position_record   （俯卧位，2 / 4 行）
--       patient_apache_record / patient_apache_record_item       （ApacheII，374 / 5810 行）
--       patient_sofa_record / patient_sofa_record_item           （SOFA，65 / 667 行）
--       patient_sepsis_record / patient_sepsis_doubt_record 等   （脓毒症，32 / 6 行）
--   若后续改口径为「读源表」，这批表就变成不同的东西了，所以单独成脚本、单独评审。
--
-- 本批（11 张）：
--   俯卧位  ards_prone_record     → patient_doc_prone_record
--           ards_prone_timepoint  → patient_doc_prone_timepoint
--           ards_prone_cell       → patient_doc_prone_cell
--           ards_prone_cell_log   → patient_doc_prone_cell_log
--   配置类  ards_prone_config     → config_prone_item
--           ards_prone_tp_tpl     → config_prone_timepoint_tpl
--           apache2_config        → config_apache2
--           sofa_config           → config_sofa
--   评分    apache2_score_record  → patient_doc_apache2_score_record
--           sofa_score_record     → patient_doc_sofa_score_record
--   脓毒症  sepsis_bundle_record  → patient_doc_sepsis_bundle_record
--
-- ⚠️ 与 ICU 同名源的区分（重要，写 SQL 时不要省 schema 前缀）：
--   两批改成 patient_doc_* 后，跨库联查里会同时出现
--       zing_icu_db_prod.patient_apache_record        （源系统：护 ICU 自己录的）
--       zing_doctor_db_prod.patient_doc_apache2_score_record（本系统：医生侧评分）
--   跨 schema 不报错，但**名字相近极易看错**。约定：凡是跨库 SQL，
--   表名一律写全限定名（"库"."表"），不允许裸表名；审查时看到裸表名直接打回。
--
-- 数据安全性 / 幂等 / 执行方式：
--   与 24 号完全一致（ALTER TABLE RENAME 保留数据与索引；仅当旧表存在 且 新表不存在时执行；
--   医生库无视图 / 无触发器 / 无外键）。两批互不依赖，可分别上线也可一次跑完。
--
-- 回滚（反向执行即可）：
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_prone_record"           RENAME TO "ards_prone_record";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_prone_timepoint"        RENAME TO "ards_prone_timepoint";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_prone_cell"             RENAME TO "ards_prone_cell";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_prone_cell_log"         RENAME TO "ards_prone_cell_log";
--   ALTER TABLE "zing_doctor_db_prod"."config_prone_item"                  RENAME TO "ards_prone_config";
--   ALTER TABLE "zing_doctor_db_prod"."config_prone_timepoint_tpl"         RENAME TO "ards_prone_tp_tpl";
--   ALTER TABLE "zing_doctor_db_prod"."config_apache2"                     RENAME TO "apache2_config";
--   ALTER TABLE "zing_doctor_db_prod"."config_sofa"                        RENAME TO "sofa_config";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_apache2_score_record"   RENAME TO "apache2_score_record";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_sofa_score_record"      RENAME TO "sofa_score_record";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_sepsis_bundle_record"   RENAME TO "sepsis_bundle_record";
--   ⚠️ 回滚要把代码版本一并退回：代码里的 @TableName 已是新名，
--      只回滚库会让页面大面积报「无效的表或视图名」。
--
-- 执行后自检：
--   SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(OWNER)='ZING_DOCTOR_DB_PROD'
--     AND TABLE_NAME IN ('patient_doc_prone_record','patient_doc_prone_timepoint',
--       'patient_doc_prone_cell','patient_doc_prone_cell_log','config_prone_item',
--       'config_prone_timepoint_tpl','config_apache2','config_sofa',
--       'patient_doc_apache2_score_record','patient_doc_sofa_score_record',
--       'patient_doc_sepsis_bundle_record');                     -- → 11
--   SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(OWNER)='ZING_DOCTOR_DB_PROD'
--     AND (TABLE_NAME LIKE 'ards\_prone%' ESCAPE '\' OR UPPER(TABLE_NAME) LIKE 'APACHE2^_%' ESCAPE '^'
--          OR UPPER(TABLE_NAME) LIKE 'SOFA^_%' ESCAPE '^');      -- → 0（残留即为漏改）
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/25_rename_clinical_tables.sql
-- =====================================================================

DECLARE
    v_old INT;
    v_new INT;
BEGIN
    FOR r IN (
        SELECT 'ards_prone_record'    AS OLD_NAME, 'patient_doc_prone_record'          AS NEW_NAME FROM DUAL UNION ALL
        SELECT 'ards_prone_timepoint'             , 'patient_doc_prone_timepoint'                  FROM DUAL UNION ALL
        SELECT 'ards_prone_cell'                  , 'patient_doc_prone_cell'                       FROM DUAL UNION ALL
        SELECT 'ards_prone_cell_log'              , 'patient_doc_prone_cell_log'                   FROM DUAL UNION ALL
        SELECT 'ards_prone_config'                , 'config_prone_item'                            FROM DUAL UNION ALL
        SELECT 'ards_prone_tp_tpl'                , 'config_prone_timepoint_tpl'                   FROM DUAL UNION ALL
        SELECT 'apache2_config'                   , 'config_apache2'                               FROM DUAL UNION ALL
        SELECT 'sofa_config'                      , 'config_sofa'                                  FROM DUAL UNION ALL
        SELECT 'apache2_score_record'             , 'patient_doc_apache2_score_record'             FROM DUAL UNION ALL
        SELECT 'sofa_score_record'                , 'patient_doc_sofa_score_record'                FROM DUAL UNION ALL
        SELECT 'sepsis_bundle_record'             , 'patient_doc_sepsis_bundle_record'             FROM DUAL
    ) LOOP
        SELECT COUNT(*) INTO v_old FROM ALL_TABLES
         WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND TABLE_NAME = r.OLD_NAME;
        SELECT COUNT(*) INTO v_new FROM ALL_TABLES
         WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND TABLE_NAME = r.NEW_NAME;

        IF v_old > 0 AND v_new = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."' || r.OLD_NAME
                              || '" RENAME TO "' || r.NEW_NAME || '"';
        END IF;
    END LOOP;
END;
/

COMMIT;
