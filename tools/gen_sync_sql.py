# -*- coding: utf-8 -*-
"""从 quality YAML 生成 DB 真源同步脚本（50_quality_gap_impl.sql 及 mysql 镜像）。
YAML 是代码侧真源、DB 是运行期真源；本生成器保证两边逐字段一致，避免手抄漂移。"""
import yaml, glob, json, io, sys

def load(pattern):
    out = []
    for f in sorted(glob.glob(pattern, recursive=True)):
        for doc in yaml.safe_load_all(open(f, encoding='utf-8')):
            if not doc:
                continue
            for d in (doc if isinstance(doc, list) else [doc]):
                out.append(d)
    return out

facts = {d['fact']: d for d in load('src/main/resources/quality/facts/*.yaml')}
metrics = {d['code']: d for d in load('src/main/resources/quality/metrics/*.yaml')}

CHANGED_FACTS = ['fact_dvt', 'fact_device', 'fact_bed', 'fact_ards', 'fact_staff']
# 只同步「口径/实现真正变了」的指标：下面的 UPDATE 是整字段覆盖，会把库里的
# dims / patient_fields 等列一并写成 YAML 值。因此仅改过 remark 的指标
# （quality_20 / quality_410 / quality_421）刻意不入清单 —— 它们不参与计算，
# 文案以 YAML 为准即可，不值得为此冒覆盖库内既有列的风险。
CHANGED_METRICS = [
    # DVT 预防（2024版 ICU-07）
    'quality_14', 'quality_404', 'quality_405', 'quality_406', 'quality_407',
    'quality_408', 'quality_409', 'quality_411', 'quality_412', 'quality_413',
    # 导管/管路/院感（ICU-12/13/16/17 + 2015 CAUTI）
    'quality_17', 'quality_18', 'quality_19', 'quality_24', 'quality_25',
    'quality_26', 'quality_27', 'quality_425', 'quality_426',
    # 床位资源（ICU-01/02/03）+ 医护人员（连库激活 config_staff）
    'quality_301', 'quality_302', 'quality_303', 'quality_304',
    # ARDS 支持时长与转归
    'quality_477', 'quality_486', 'quality_487', 'quality_488', 'quality_489', 'quality_490',
]

def jarr(v):
    return json.dumps(v if v is not None else [], ensure_ascii=False)

def sq(s):
    """SQL 单引号字面量（None -> NULL）"""
    if s is None:
        return 'NULL'
    return "'" + str(s).replace("'", "''") + "'"

def pf_json(d):
    pfs = d.get('patientFields') or []
    if not pfs:
        return 'NULL'
    return sq(json.dumps(pfs, ensure_ascii=False))

# ---------------- DM 脚本 ----------------
dm = []
dm.append("""-- =====================================================================
-- 50_quality_gap_impl.sql
-- 质控中台缺口落地：导管事件源 / DVT 预防 / 床位资源 / ARDS 支持时长（达梦 DM8）
--
-- 背景：验收对照表 75 项未完全落地功能点中，以下 28 条指标此前为
--       PENDING_SOURCE / PLACEHOLDER，本次全部落地（IMPL）：
--         · 插管三指标 quality_17/18/19（2024版 ICU-12/13，源 patient_tube）
--         · 导管感染 VAP/CRBSI/CAUTI：quality_24/26/425（源：医生确诊录入表，
--           本脚本新建 patient_doc_device_infection）+ 留置/通气天数
--           quality_25/27/426
--         · DVT 预防 10 项（2024版 ICU-07，源：医嘱侧 patient_advice_execute_drug /
--           patient_advice_original，关键词按官方口径）
--         · 床位 quality_301/304（2024版 ICU-01/02/03 分母，源 config_bed）
--         · ARDS quality_477/486/487-490（支持时长 / 转归）
--       事实层 4 条同步改造：fact_device 改接 patient_tube 并关联确诊表；
--       fact_dvt 补预防列；fact_bed 激活并对齐实库字段；fact_ards 补
--       mv_days / prone_min 两列。
--
-- 本脚本由 tools/gen_sync_sql.py 从 classpath YAML 生成，
-- 与 src/main/resources/quality/** 中 CHANGED_FACTS / CHANGED_METRICS 列出的条目逐字段一致；
-- YAML 改动后需重跑生成器（python tools/gen_sync_sql.py）再执行本脚本。
-- 未纳入清单的条目不会被本脚本覆盖，即使 YAML 侧有改动 —— 整字段 SET 会给库里
-- 既有列写入 YAML 值，对「只改了 remark」的条目（quality_20 / quality_410 /
-- quality_421）风险大于收益，故刻意保持不同步，其口径备注以 YAML 为准。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/50_quality_gap_impl.sql
-- 执行后：POST /api/quality/recalc 重算（或等 QualityDailyTask 每日批算）。
-- 幂等：UPDATE 反复执行结果一致；建表带存在性检查。
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) 导管相关感染确诊录入表（VAP / CRBSI / CAUTI 医生确诊）
--    三型感染共用一套录入表单（按 infection_type 区分），
--    确诊记录同时驱动 quality_24/26/425 与导管统计页的感染标注。
--    口径：2024版 VAP=使用有创呼吸机期间或停用48h内新发肺部感染；
--          CRBSI=留置血管导管期间或拔出48h内原发性血流感染；
--          CAUTI=2015版，插管或拔管48h内新发尿路感染。
-- ---------------------------------------------------------------------
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND TABLE_NAME = 'patient_doc_device_infection';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."patient_doc_device_infection" (
            "id"                 BIGINT NOT NULL,
            "patient_id"         VARCHAR(64) NOT NULL,
            "in_hospital_no"     VARCHAR(64),
            "patient_name"       VARCHAR(64),
            "depart_code"        VARCHAR(64),
            "infection_type"     VARCHAR(16) NOT NULL,
            "confirm_time"       TIMESTAMP NOT NULL,
            "diagnosis_basis"    VARCHAR(32),
            "clinical_evidence"  VARCHAR(2000),
            "pathogen"           VARCHAR(1000),
            "related_tube_code"  VARCHAR(64),
            "related_tube_name"  VARCHAR(128),
            "doctor_name"        VARCHAR(64),
            "doctor_work_no"     VARCHAR(64),
            "remark"             VARCHAR(1000),
            "status"             TINYINT DEFAULT 1 NOT NULL,
            "create_by"          VARCHAR(64),
            "create_time"        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_by"          VARCHAR(64),
            "update_time"        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_patient_doc_device_infection" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND INDEX_NAME = 'idx_pdi_patient_type';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_pdi_patient_type" ON "zing_doctor_db_prod"."patient_doc_device_infection" ("patient_id", "infection_type")';
    END IF;
END;
/

COMMENT ON TABLE "zing_doctor_db_prod"."patient_doc_device_infection" IS '导管相关感染确诊录入（VAP/CRBSI/CAUTI，医生确诊；驱动质控导管域指标）';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_device_infection"."infection_type" IS '感染类型：VAP 呼吸机相关肺炎 / CRBSI 血管内导管相关血流感染 / CAUTI 导尿管相关泌尿系感染';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_device_infection"."confirm_time" IS '确诊时间；质控口径窗口=置入~拔除+48h';
COMMENT ON COLUMN "zing_doctor_db_prod"."patient_doc_device_infection"."diagnosis_basis" IS '诊断依据：临床诊断 / 病原学确诊（医院感染诊断标准）';

-- ---------------------------------------------------------------------
-- B) 事实层定义更新（与 classpath YAML 一致）
-- ---------------------------------------------------------------------""")

for fn in CHANGED_FACTS:
    d = facts[fn]
    dm.append(f"""UPDATE "zing_doctor_db_prod"."quality_fact_def"
   SET "status"             = {sq(d.get('status'))},
       "domain_code"        = {sq(d.get('domain'))},
       "source_table"       = {sq(d.get('source'))},
       "alias"              = {sq(d.get('alias'))},
       "patient_key"        = {sq(d.get('patientKey'))},
       "in_hospital_no_key" = {sq(d.get('inHospitalNoKey'))},
       "patient_name_key"   = {sq(d.get('patientNameKey'))},
       "depart_key"         = {sq(d.get('departKey'))},
       "select_cols"        = {sq(jarr(d.get('select')))},
       "derive_cols"        = {sq(jarr(d.get('derive')))},
       "where_conds"        = {sq(jarr(d.get('where')))},
       "group_cols"         = {sq(jarr(d.get('group')))},
       "note"               = {sq(d.get('note'))},
       "update_time"        = CURRENT_TIMESTAMP
 WHERE "fact_name" = {sq(fn)};""")

dm.append("""
-- ---------------------------------------------------------------------
-- C) 指标定义更新（impl_status/口径三要素，与 classpath YAML 一致）
--    expr_version +1：口径变化可回溯历史值。
-- ---------------------------------------------------------------------""")

for code in CHANGED_METRICS:
    d = metrics[code]
    scale = d.get('scale')
    scale_sql = str(scale) if scale is not None else 'NULL'
    dims_sql = sq(jarr(d.get('dims') or []))
    dm.append(f"""UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = {sq(d.get('implStatus', 'IMPL'))},
       "value_type"             = {sq(d.get('valueType', 'COUNT'))},
       "agg"                    = {sq(d.get('agg', 'PT_COUNT'))},
       "scale"                  = {scale_sql},
       "expr_where"             = {sq(d.get('where'))},
       "expr_numerator"         = {sq(d.get('numerator'))},
       "expr_denominator_where" = {sq(d.get('denominatorWhere'))},
       "dims"                   = {dims_sql},
       "patient_fields"         = {pf_json(d)},
       "remark"                 = {sq(d.get('remark'))},
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = {sq(code)};""")

dm.append("""
-- ---------------------------------------------------------------------
-- D) 核对（手动执行）
--   SELECT "fact_name", "status", "source_table" FROM "zing_doctor_db_prod"."quality_fact_def" ORDER BY "fact_name";
--   SELECT "index_code", "impl_status", "agg", "expr_where" FROM "zing_doctor_db_prod"."quality_metric_def"
--    WHERE "index_code" IN ('quality_14','quality_17','quality_24','quality_301','quality_426','quality_486');
--   预期：全部 IMPL；fact_device/fact_dvt/fact_bed/fact_ards = ACTIVE。
--   重算：POST /api/quality/recalc（async=true 返回 runId，轮询 /api/quality/run）。
-- ---------------------------------------------------------------------""")

# ---------------- MySQL 镜像 ----------------
my = []
my.append("""-- =====================================================================
-- 50_quality_gap_impl.sql（MySQL 镜像）
-- 对应达梦版：sql/50_quality_gap_impl.sql
-- 由 tools/gen_sync_sql.py 一并生成（同步范围见达梦版头部说明）；
-- 属人工维护脚本，已登记进 tools/dm_to_mysql.py 的 MANUAL，勿用转换器机械覆盖。
-- 导管相关感染确诊表 + 质控事实层/指标层缺口落地同步。
-- 执行：mysql -uroot -p zing_doctor_db_prod < 本文件
-- 幂等：CREATE TABLE IF NOT EXISTS；UPDATE 反复执行结果一致。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

CREATE TABLE IF NOT EXISTS `patient_doc_device_infection` (
  `id` BIGINT NOT NULL COMMENT '主键（雪花算法）',
  `patient_id` VARCHAR(64) NOT NULL COMMENT 'ICU 患者 ID',
  `in_hospital_no` VARCHAR(64) COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `infection_type` VARCHAR(16) NOT NULL COMMENT '感染类型：VAP/CRBSI/CAUTI',
  `confirm_time` TIMESTAMP NOT NULL COMMENT '确诊时间（窗口=置入~拔除+48h）',
  `diagnosis_basis` VARCHAR(32) COMMENT '诊断依据：临床诊断/病原学确诊',
  `clinical_evidence` VARCHAR(2000) COMMENT '临床依据（症状/影像/检验）',
  `pathogen` VARCHAR(1000) COMMENT '病原学（培养/涂片结果）',
  `related_tube_code` VARCHAR(64) COMMENT '关联导管编码',
  `related_tube_name` VARCHAR(128) COMMENT '关联导管名称',
  `doctor_name` VARCHAR(64) COMMENT '确诊医师',
  `doctor_work_no` VARCHAR(64) COMMENT '确诊医师工号',
  `remark` VARCHAR(1000) COMMENT '备注',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0已删除',
  `create_by` VARCHAR(64) COMMENT '创建人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) COMMENT '更新人',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_pdi_patient_type` (`patient_id`, `infection_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导管相关感染确诊录入（VAP/CRBSI/CAUTI）';

-- ---- 事实层定义更新 ----
""")

for fn in CHANGED_FACTS:
    d = facts[fn]
    my.append(f"""UPDATE `quality_fact_def`
   SET `status`             = {sq(d.get('status'))},
       `domain_code`        = {sq(d.get('domain'))},
       `source_table`       = {sq(d.get('source'))},
       `alias`              = {sq(d.get('alias'))},
       `patient_key`        = {sq(d.get('patientKey'))},
       `in_hospital_no_key` = {sq(d.get('inHospitalNoKey'))},
       `patient_name_key`   = {sq(d.get('patientNameKey'))},
       `depart_key`         = {sq(d.get('departKey'))},
       `select_cols`        = {sq(jarr(d.get('select')))},
       `derive_cols`        = {sq(jarr(d.get('derive')))},
       `where_conds`        = {sq(jarr(d.get('where')))},
       `group_cols`         = {sq(jarr(d.get('group')))},
       `note`               = {sq(d.get('note'))},
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = {sq(fn)};""")

my.append("""
-- ---- 指标定义更新 ----
""")

for code in CHANGED_METRICS:
    d = metrics[code]
    scale = d.get('scale')
    scale_sql = str(scale) if scale is not None else 'NULL'
    dims_sql = sq(jarr(d.get('dims') or []))
    my.append(f"""UPDATE `quality_metric_def`
   SET `impl_status`            = {sq(d.get('implStatus', 'IMPL'))},
       `value_type`             = {sq(d.get('valueType', 'COUNT'))},
       `agg`                    = {sq(d.get('agg', 'PT_COUNT'))},
       `scale`                  = {scale_sql},
       `expr_where`             = {sq(d.get('where'))},
       `expr_numerator`         = {sq(d.get('numerator'))},
       `expr_denominator_where` = {sq(d.get('denominatorWhere'))},
       `dims`                   = {dims_sql},
       `patient_fields`         = {pf_json(d)},
       `remark`                 = {sq(d.get('remark'))},
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = {sq(code)};""")

io.open('sql/50_quality_gap_impl.sql', 'w', encoding='utf-8').write('\n'.join(dm) + '\n')
io.open('sql/mysql/50_quality_gap_impl.sql', 'w', encoding='utf-8').write('\n'.join(my) + '\n')
print('written: sql/50_quality_gap_impl.sql (%d facts, %d metrics)' % (len(CHANGED_FACTS), len(CHANGED_METRICS)))
print('written: sql/mysql/50_quality_gap_impl.sql')
