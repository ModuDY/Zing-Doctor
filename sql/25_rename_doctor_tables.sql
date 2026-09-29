-- =====================================================================
-- 24_rename_doctor_tables.sql
-- 医生库表名对齐 ICU 命名规范（第一批：系统 / 配置 / 词典 / 事实层，共 21 张）
--
-- 背景：
--   ICU 源库 810 张表遵循统一的构词法：
--       <主体>_<业务>_<明细>
--   主体 ∈ {patient(442) / config(225) / icu(43) / sys(38) / control(27) / quality(14)}，
--   全小写下划线、无产品前缀、无缩写，明细用 item / record / log / config / execute。
--   例：patient_info_lis_item、patient_advice_execute、config_staff_ca_info。
--
--   医生库此前三套写法混用：无前缀（ards_prone_* / sofa_*）、产品前缀（zing_*）、
--   缩写前缀（qc_fact_*）。跨库联查时同一条 SQL 里出现两种命名习惯，
--   读代码要靠猜；rename 一次到位比继续两套并存便宜。
--
-- 本批（21 张 = 静态 13 张 + 运行时事实表 8 张）：
--   系统类 → sys_*        zing_sys_user / zing_sys_param / zing_param_group /
--                         zing_page_config / zing_archive_log / zing_external_access_log
--   配置类 → config_*     zing_abx_drug_dict / zing_abx_word_config /
--                         zing_ddd_config / zing_mdro_config
--   患者业务 → patient_doc_*（对齐 ICU 里 patient_doc_* 102 张的「医生侧」位置）
--                         zing_decision_record / zing_advice_log / zing_doctor_handover
--   事实层 → quality_fact_*（去掉 qc_ 缩写；由 ALTER TABLE 沿用，不用重算）
--
-- 为什么分批（25 / 26）：
--   26 号改的是临床业务表（俯卧位、ApacheII、SOFA、脓毒症），这几张表 ICU 侧存在同名
--   业务的源表（patient_prone_position_record / patient_apache_record / patient_sofa_record /
--   patient_sepsis_record 等），改名要与「自建还是读源表」的口径一起评审，故单独一批发出。
--   两批互不依赖，可分别上线，也可一次跑完。
--
-- 数据安全性（已核验）：
--   · ALTER TABLE ... RENAME 只改数据字典里的表名，数据、索引、主键、授权全部跟随，
--     不像「建新表 + INSERT SELECT」那样有丢数据风险，也不需要额外的磁盘空间；
--   · 医生库当前 0 视图 / 0 触发器 / 0 同义词、34 条约束全是主键（无外键），
--     不存在 rename 后被依赖对象失效的问题；
--   · rename 不影响任何列值（页面注册表里的 frontend_path、指标定义里的 fact_name 都不动）。
--
-- 幂等（可重复执行，install.sh 每次安装/升级都会跑）：
--   仅当「旧表存在 且 新表不存在」时才 rename。全新库由 01_schema.sql 直接按新名建表，
--   本脚本走到这里会因旧表不存在而全部跳过 —— 这也是它必须同时登记在 FULL_SQL 的原因。
--
-- 回滚（把下面这段反过来执行即可，方向与上面一一对应）：
--   ALTER TABLE "zing_doctor_db_prod"."sys_user"                  RENAME TO "zing_sys_user";
--   ALTER TABLE "zing_doctor_db_prod"."sys_param"                 RENAME TO "zing_sys_param";
--   ALTER TABLE "zing_doctor_db_prod"."sys_param_group"           RENAME TO "zing_param_group";
--   ALTER TABLE "zing_doctor_db_prod"."sys_page_config"           RENAME TO "zing_page_config";
--   ALTER TABLE "zing_doctor_db_prod"."sys_archive_log"           RENAME TO "zing_archive_log";
--   ALTER TABLE "zing_doctor_db_prod"."sys_access_log"            RENAME TO "zing_external_access_log";
--   ALTER TABLE "zing_doctor_db_prod"."config_abx_drug_dict"      RENAME TO "zing_abx_drug_dict";
--   ALTER TABLE "zing_doctor_db_prod"."config_abx_word"           RENAME TO "zing_abx_word_config";
--   ALTER TABLE "zing_doctor_db_prod"."config_ddd"                RENAME TO "zing_ddd_config";
--   ALTER TABLE "zing_doctor_db_prod"."config_mdro"               RENAME TO "zing_mdro_config";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_decision_record" RENAME TO "zing_decision_record";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_advice_log"      RENAME TO "zing_advice_log";
--   ALTER TABLE "zing_doctor_db_prod"."patient_doc_handover_record" RENAME TO "zing_doctor_handover";
--   ⚠️ 回滚前须把代码版本一并退回（代码里的 @TableName 已是新名），否则页面报「无效的表或视图名」。
--   ⚠️ 运行时事实表（quality_fact_*）是算出来的缓存，回滚时直接 DROP 即可，下次批算会按旧前缀重建。
--
-- 执行后自检（14 张改名后应各返回一行，行数与迁移前一致）：
--   SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(OWNER)='ZING_DOCTOR_DB_PROD'
--     AND TABLE_NAME IN ('sys_user','sys_param','sys_param_group','sys_page_config',
--       'sys_archive_log','sys_access_log','config_abx_drug_dict','config_abx_word',
--       'config_ddd','config_mdro','patient_doc_decision_record','patient_doc_advice_log',
--       'patient_doc_handover_record');   -- → 13
--   SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(OWNER)='ZING_DOCTOR_DB_PROD'
--     AND UPPER(TABLE_NAME) LIKE 'ZING^_%' ESCAPE '^';            -- → 0（残留即为漏改）
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/24_rename_doctor_tables.sql
-- =====================================================================

DECLARE
    v_old INT;
    v_new INT;
BEGIN
    -- ---------- 1) 静态表：13 张，按 <新名 ↔ 旧名> 显式登记 ----------
    FOR r IN (
        SELECT 'zing_sys_user'             AS OLD_NAME, 'sys_user'                     AS NEW_NAME FROM DUAL UNION ALL
        SELECT 'zing_sys_param'                        , 'sys_param'                              FROM DUAL UNION ALL
        SELECT 'zing_param_group'                      , 'sys_param_group'                        FROM DUAL UNION ALL
        SELECT 'zing_page_config'                      , 'sys_page_config'                        FROM DUAL UNION ALL
        SELECT 'zing_archive_log'                      , 'sys_archive_log'                        FROM DUAL UNION ALL
        SELECT 'zing_external_access_log'              , 'sys_access_log'                         FROM DUAL UNION ALL
        SELECT 'zing_abx_drug_dict'                    , 'config_abx_drug_dict'                   FROM DUAL UNION ALL
        SELECT 'zing_abx_word_config'                  , 'config_abx_word'                        FROM DUAL UNION ALL
        SELECT 'zing_ddd_config'                       , 'config_ddd'                             FROM DUAL UNION ALL
        SELECT 'zing_mdro_config'                      , 'config_mdro'                            FROM DUAL UNION ALL
        SELECT 'zing_decision_record'                  , 'patient_doc_decision_record'            FROM DUAL UNION ALL
        SELECT 'zing_advice_log'                       , 'patient_doc_advice_log'                 FROM DUAL UNION ALL
        SELECT 'zing_doctor_handover'                  , 'patient_doc_handover_record'            FROM DUAL
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

    -- ---------- 2) 运行时事实表：qc_fact_* → quality_fact_* ----------
    -- 表名带批次后缀（…_20260701_dac46），不同环境后缀不同，故按前缀通配处理，
    -- 而不是写死 8 个名字；改名后现有缓存表继续可用，不必重跑一轮质控批算。
    FOR f IN (
        SELECT TABLE_NAME AS TN FROM ALL_TABLES
         WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND TABLE_NAME LIKE 'qc\_fact%' ESCAPE '\'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."' || f.TN
                          || '" RENAME TO "' || REPLACE(f.TN, 'qc_fact_', 'quality_fact_') || '"';
    END LOOP;
END;
/

COMMIT;
