-- =====================================================================
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

UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = 'DVT预防',
       `source_table`       = 'patient_info',
       `alias`              = 'pi',
       `patient_key`        = NULL,
       `in_hospital_no_key` = NULL,
       `patient_name_key`   = NULL,
       `depart_key`         = NULL,
       `select_cols`        = '["pi.id AS patient_id", "pi.in_hospital_no AS in_hospital_no", "pi.name AS patient_name", "pi.gender AS gender", "pi.bed_code AS bed_code", "pi.age AS age", "pi.depart_code AS depart_code", "pi.ward_name AS ward_name", "pi.out_vest_type AS out_vest_type", "pi.charge_doctor_name AS charge_doctor_name", "pi.in_depart_time AS in_depart_time", "pi.out_depart_time AS out_depart_time", "pi.out_hospital_time AS out_hospital_time", "CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"]',
       `derive_cols`        = '["CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%深静脉血栓%'' OR d.diag_name LIKE ''%下肢静脉血栓%'' OR d.diag_name LIKE ''%肺栓塞%'')) THEN 1 ELSE 0 END AS dvt_diag", "CASE WHEN pi.out_vest_type = 3 AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%深静脉血栓%'' OR d.diag_name LIKE ''%下肢静脉血栓%'' OR d.diag_name LIKE ''%肺栓塞%'')) THEN 1 ELSE 0 END AS dvt_death", "CASE WHEN pi.out_vest_type = 3 AND pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS died_in_period", "CASE WHEN pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS out_in_period", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_drug\" e JOIN \"zing_icu_db_prod\".\"patient_advice_execute\" x ON x.id = e.execute_id WHERE e.patient_id = pi.id AND e.del_flag = 0 AND x.del_flag = 0 AND x.start_time < :periodEnd AND (x.end_time IS NULL OR x.end_time >= :periodStart) AND (e.drug_name LIKE ''%肝素%'' OR e.drug_name LIKE ''%利伐沙班%'' OR e.drug_name LIKE ''%达比加群酯%'' OR e.drug_name LIKE ''%阿哌沙班%'' OR e.drug_name LIKE ''%磺达肝癸%'')) THEN 1 ELSE 0 END AS dvt_drug_status", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%气压治疗%'' OR o.advice_name LIKE ''%肢体加压%'' OR o.advice_name LIKE ''%间歇充气%'' OR o.advice_name LIKE ''%加压泵%'' OR o.advice_name LIKE ''%空气波%'' OR o.advice_name LIKE ''%梯度压力%'' OR o.advice_name LIKE ''%抗血栓%'' OR o.advice_name LIKE ''%弹力袜%'')) THEN 1 ELSE 0 END AS dvt_mech_status", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%下腔静脉滤器%'' OR o.advice_name LIKE ''%滤器置入%'')) THEN 1 ELSE 0 END AS dvt_filter_status", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_drug\" e JOIN \"zing_icu_db_prod\".\"patient_advice_execute\" x ON x.id = e.execute_id WHERE e.patient_id = pi.id AND e.del_flag = 0 AND x.del_flag = 0 AND x.start_time < :periodEnd AND (x.end_time IS NULL OR x.end_time >= :periodStart) AND (e.drug_name LIKE ''%肝素%'' OR e.drug_name LIKE ''%利伐沙班%'' OR e.drug_name LIKE ''%达比加群酯%'' OR e.drug_name LIKE ''%阿哌沙班%'' OR e.drug_name LIKE ''%磺达肝癸%'')) OR EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%气压治疗%'' OR o.advice_name LIKE ''%肢体加压%'' OR o.advice_name LIKE ''%间歇充气%'' OR o.advice_name LIKE ''%加压泵%'' OR o.advice_name LIKE ''%空气波%'' OR o.advice_name LIKE ''%梯度压力%'' OR o.advice_name LIKE ''%抗血栓%'' OR o.advice_name LIKE ''%弹力袜%'')) OR EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%下腔静脉滤器%'' OR o.advice_name LIKE ''%滤器置入%'')) THEN 1 ELSE 0 END AS dvt_prevent_status", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_drug\" e JOIN \"zing_icu_db_prod\".\"patient_advice_execute\" x ON x.id = e.execute_id WHERE e.patient_id = pi.id AND e.del_flag = 0 AND x.del_flag = 0 AND x.start_time < :periodEnd AND (x.end_time IS NULL OR x.end_time >= :periodStart) AND (e.drug_name LIKE ''%肝素%'' OR e.drug_name LIKE ''%利伐沙班%'' OR e.drug_name LIKE ''%达比加群酯%'' OR e.drug_name LIKE ''%阿哌沙班%'' OR e.drug_name LIKE ''%磺达肝癸%'')) AND NOT EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%气压治疗%'' OR o.advice_name LIKE ''%肢体加压%'' OR o.advice_name LIKE ''%间歇充气%'' OR o.advice_name LIKE ''%加压泵%'' OR o.advice_name LIKE ''%空气波%'' OR o.advice_name LIKE ''%梯度压力%'' OR o.advice_name LIKE ''%抗血栓%'' OR o.advice_name LIKE ''%弹力袜%'' OR o.advice_name LIKE ''%下腔静脉滤器%'' OR o.advice_name LIKE ''%滤器置入%'')) THEN 1 ELSE 0 END AS dvt_only_drug", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%气压治疗%'' OR o.advice_name LIKE ''%肢体加压%'' OR o.advice_name LIKE ''%间歇充气%'' OR o.advice_name LIKE ''%加压泵%'' OR o.advice_name LIKE ''%空气波%'' OR o.advice_name LIKE ''%梯度压力%'' OR o.advice_name LIKE ''%抗血栓%'' OR o.advice_name LIKE ''%弹力袜%'')) AND NOT EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_drug\" e JOIN \"zing_icu_db_prod\".\"patient_advice_execute\" x ON x.id = e.execute_id WHERE e.patient_id = pi.id AND e.del_flag = 0 AND x.del_flag = 0 AND x.start_time < :periodEnd AND (x.end_time IS NULL OR x.end_time >= :periodStart) AND (e.drug_name LIKE ''%肝素%'' OR e.drug_name LIKE ''%利伐沙班%'' OR e.drug_name LIKE ''%达比加群酯%'' OR e.drug_name LIKE ''%阿哌沙班%'' OR e.drug_name LIKE ''%磺达肝癸%'')) AND NOT EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o2 WHERE o2.in_hospital_serial_no = pi.in_hospital_serial_no AND (o2.advice_name LIKE ''%下腔静脉滤器%'' OR o2.advice_name LIKE ''%滤器置入%'')) THEN 1 ELSE 0 END AS dvt_only_mech", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o WHERE o.in_hospital_serial_no = pi.in_hospital_serial_no AND o.start_time < :periodEnd AND (o.end_time IS NULL OR o.end_time >= :periodStart) AND (o.advice_name LIKE ''%下腔静脉滤器%'' OR o.advice_name LIKE ''%滤器置入%'')) AND NOT EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_drug\" e JOIN \"zing_icu_db_prod\".\"patient_advice_execute\" x ON x.id = e.execute_id WHERE e.patient_id = pi.id AND e.del_flag = 0 AND x.del_flag = 0 AND x.start_time < :periodEnd AND (x.end_time IS NULL OR x.end_time >= :periodStart) AND (e.drug_name LIKE ''%肝素%'' OR e.drug_name LIKE ''%利伐沙班%'' OR e.drug_name LIKE ''%达比加群酯%'' OR e.drug_name LIKE ''%阿哌沙班%'' OR e.drug_name LIKE ''%磺达肝癸%'')) AND NOT EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_original\" o2 WHERE o2.in_hospital_serial_no = pi.in_hospital_serial_no AND (o2.advice_name LIKE ''%气压治疗%'' OR o2.advice_name LIKE ''%肢体加压%'' OR o2.advice_name LIKE ''%间歇充气%'' OR o2.advice_name LIKE ''%加压泵%'' OR o2.advice_name LIKE ''%空气波%'' OR o2.advice_name LIKE ''%梯度压力%'' OR o2.advice_name LIKE ''%抗血栓%'' OR o2.advice_name LIKE ''%弹力袜%'')) THEN 1 ELSE 0 END AS dvt_only_filter"]',
       `where_conds`        = '["pi.del_flag = 0", "pi.in_depart_time IS NOT NULL", "pi.in_depart_time < :periodEnd", "(pi.out_depart_time IS NULL OR pi.out_depart_time >= :periodStart)"]',
       `group_cols`         = '[]',
       `note`               = 'DVT 诊断/死亡 + 药物/机械/滤器预防（医嘱侧，2024版ICU-07口径）',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_dvt';
UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = '导管_管路_院感',
       `source_table`       = 'patient_tube',
       `alias`              = 'pt',
       `patient_key`        = 'patient_id',
       `in_hospital_no_key` = 'in_hospital_no',
       `patient_name_key`   = 'patient_name',
       `depart_key`         = 'depart_code',
       `select_cols`        = '["pt.id AS device_id", "pt.patient_id AS patient_id", "pt.tube_code AS tube_code", "pt.tube_name AS tube_name", "pt.tube_set_start_time AS insert_time", "pt.tube_set_end_time AS remove_time", "pt.take_source AS take_source", "pt.is_not_plan_extubation AS is_not_plan_extubation", "pt.is_reintubation24 AS is_reintubation24", "(SELECT pi0.depart_code FROM \"zing_icu_db_prod\".\"patient_info\" pi0 WHERE pi0.id = pt.patient_id) AS depart_code", "(SELECT pi0.name FROM \"zing_icu_db_prod\".\"patient_info\" pi0 WHERE pi0.id = pt.patient_id) AS patient_name", "(SELECT pi0.in_hospital_no FROM \"zing_icu_db_prod\".\"patient_info\" pi0 WHERE pi0.id = pt.patient_id) AS in_hospital_no"]',
       `derive_cols`        = '["CASE WHEN pt.tube_code LIKE ''%CVC%'' OR pt.tube_code LIKE ''%中心静脉%'' OR pt.tube_name LIKE ''%中心静脉%'' OR pt.tube_name LIKE ''%PICC%'' THEN ''CVC'' WHEN pt.tube_code LIKE ''%气管插管%'' OR pt.tube_code LIKE ''%气管切开%'' OR pt.tube_name LIKE ''%气管%'' THEN ''气道导管'' WHEN pt.tube_code LIKE ''%导尿%'' OR pt.tube_name LIKE ''%导尿%'' THEN ''导尿管'' ELSE ''其他'' END AS device_kind", "CASE WHEN pt.tube_set_end_time IS NOT NULL AND pt.tube_set_end_time >= :periodStart AND pt.tube_set_end_time < :periodEnd THEN 1 ELSE 0 END AS removed_in_period", "CASE WHEN pt.tube_set_end_time IS NOT NULL AND pt.tube_set_end_time >= :periodStart AND pt.tube_set_end_time < :periodEnd AND pt.is_not_plan_extubation = 1 THEN 1 ELSE 0 END AS unplanned_removal", "CASE WHEN pt.tube_set_end_time IS NOT NULL AND pt.tube_set_end_time >= :periodStart AND pt.tube_set_end_time < :periodEnd AND pt.is_not_plan_extubation = 0 AND pt.is_reintubation24 = 1 THEN 1 ELSE 0 END AS reintubation_48h", "DATEDIFF(DAY, CASE WHEN pt.tube_set_start_time < :periodStart THEN :periodStart ELSE pt.tube_set_start_time END, CASE WHEN pt.tube_set_end_time IS NOT NULL AND pt.tube_set_end_time < :periodEnd THEN pt.tube_set_end_time WHEN pt.tube_take_away_time IS NOT NULL AND pt.tube_take_away_time < :periodEnd THEN pt.tube_take_away_time ELSE :periodEnd END) AS device_days", "CASE WHEN (pt.tube_code LIKE ''%CVC%'' OR pt.tube_code LIKE ''%中心静脉%'' OR pt.tube_name LIKE ''%中心静脉%'' OR pt.tube_name LIKE ''%PICC%'') AND EXISTS (SELECT 1 FROM \"zing_doctor_db_prod\".\"patient_doc_device_infection\" r WHERE r.patient_id = pt.patient_id AND r.status = 1 AND r.infection_type = ''CRBSI'' AND r.confirm_time >= pt.tube_set_start_time AND r.confirm_time <= :periodEnd AND (pt.tube_set_end_time IS NULL OR DATEDIFF(HOUR, pt.tube_set_end_time, r.confirm_time) BETWEEN 0 AND 48)) THEN 1 WHEN (pt.tube_code LIKE ''%气管插管%'' OR pt.tube_code LIKE ''%气管切开%'' OR pt.tube_name LIKE ''%气管%'') AND EXISTS (SELECT 1 FROM \"zing_doctor_db_prod\".\"patient_doc_device_infection\" r WHERE r.patient_id = pt.patient_id AND r.status = 1 AND r.infection_type = ''VAP'' AND r.confirm_time >= pt.tube_set_start_time AND r.confirm_time <= :periodEnd AND (pt.tube_set_end_time IS NULL OR DATEDIFF(HOUR, pt.tube_set_end_time, r.confirm_time) BETWEEN 0 AND 48)) THEN 1 WHEN (pt.tube_code LIKE ''%导尿%'' OR pt.tube_name LIKE ''%导尿%'') AND EXISTS (SELECT 1 FROM \"zing_doctor_db_prod\".\"patient_doc_device_infection\" r WHERE r.patient_id = pt.patient_id AND r.status = 1 AND r.infection_type = ''CAUTI'' AND r.confirm_time >= pt.tube_set_start_time AND r.confirm_time <= :periodEnd AND (pt.tube_set_end_time IS NULL OR DATEDIFF(HOUR, pt.tube_set_end_time, r.confirm_time) BETWEEN 0 AND 48)) THEN 1 ELSE 0 END AS device_infection"]',
       `where_conds`        = '["pt.del_flag = 0", "pt.tube_set_start_time < :periodEnd", "(pt.tube_set_end_time IS NULL OR pt.tube_set_end_time >= :periodStart)"]',
       `group_cols`         = '[]',
       `note`               = '导管事件（置入/拔除/非计划拔管/48h再插管/留置天数）；感染以医生确诊录入为准',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_device';
UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = '床位_资源',
       `source_table`       = 'config_bed',
       `alias`              = 'b',
       `patient_key`        = 'bed_id',
       `in_hospital_no_key` = '',
       `patient_name_key`   = '',
       `depart_key`         = 'depart_code',
       `select_cols`        = '["b.id AS bed_id", "b.bed_code AS bed_code", "b.bed_name AS bed_name", "b.depart_code AS depart_code", "b.begin_use_time AS begin_use_time", "b.stop_use_time AS stop_use_time"]',
       `derive_cols`        = '["CASE WHEN b.begin_use_time IS NULL OR b.begin_use_time > :periodEnd THEN :periodEnd ELSE b.begin_use_time END AS open_to", "DATEDIFF(DAY, CASE WHEN b.begin_use_time < :periodStart THEN :periodStart ELSE b.begin_use_time END, CASE WHEN (b.stop_use_time IS NULL OR b.stop_use_time > :periodEnd) THEN :periodEnd ELSE b.stop_use_time END) AS bed_days", "CASE WHEN (b.begin_use_time IS NULL OR b.begin_use_time < :periodEnd) AND (b.stop_use_time IS NULL OR b.stop_use_time >= :periodStart) THEN 1 ELSE 0 END AS open_in_period"]',
       `where_conds`        = '["b.del_flag = 0"]',
       `group_cols`         = '[]',
       `note`               = '床位开放记录（config_bed）；支撑床位使用率/医师床位比/护士床位比分母',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_bed';
UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = 'ARDS专项',
       `source_table`       = 'patient_info',
       `alias`              = 'pi',
       `patient_key`        = NULL,
       `in_hospital_no_key` = NULL,
       `patient_name_key`   = NULL,
       `depart_key`         = NULL,
       `select_cols`        = '["pi.id AS patient_id", "pi.in_hospital_no AS in_hospital_no", "pi.name AS patient_name", "pi.gender AS gender", "pi.depart_code AS depart_code", "pi.in_depart_time AS in_depart_time", "pi.out_depart_time AS out_depart_time", "pi.bed_code AS bed_code", "CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"]',
       `derive_cols`        = '["CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%ARDS%'' OR d.diag_name LIKE ''%急性呼吸窘迫%'')) THEN 1 ELSE 0 END AS ards", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%ARDS%'' OR d.diag_name LIKE ''%急性呼吸窘迫%'') AND d.diag_name LIKE ''%轻度%'') THEN 1 ELSE 0 END AS ards_mild", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%ARDS%'' OR d.diag_name LIKE ''%急性呼吸窘迫%'') AND d.diag_name LIKE ''%中度%'') THEN 1 ELSE 0 END AS ards_moderate", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%ARDS%'' OR d.diag_name LIKE ''%急性呼吸窘迫%'') AND d.diag_name LIKE ''%重度%'') THEN 1 ELSE 0 END AS ards_severe", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND (pa.name LIKE ''%气管插管%'' OR pa.name LIKE ''%机械通气%'' OR pa.name LIKE ''%呼吸机%'')) THEN 1 ELSE 0 END AS intubated", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND (pa.name LIKE ''%无创%'' OR pa.name LIKE ''%BiPAP%'' OR pa.name LIKE ''%CPAP%'' OR pa.name LIKE ''%无创呼吸机%'')) THEN 1 ELSE 0 END AS niv", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND (pa.name LIKE ''%高流量%'' OR pa.name LIKE ''%HFNC%'' OR pa.name LIKE ''%经鼻高流量%'')) THEN 1 ELSE 0 END AS hfnc", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND pa.name LIKE ''%俯卧位%'') OR EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_observe_module_item\" i WHERE i.patient_id = pi.id AND i.del_flag = 0 AND i.item_name LIKE ''%俯卧位%'') THEN 1 ELSE 0 END AS prone", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND pa.type_code = ''drug'' AND (pa.name LIKE ''%顺阿曲库铵%'' OR pa.name LIKE ''%阿曲库铵%'' OR pa.name LIKE ''%维库溴铵%'' OR pa.name LIKE ''%罗库溴铵%'' OR pa.name LIKE ''%哌库溴铵%'')) THEN 1 ELSE 0 END AS muscle_relaxant", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice\" pa WHERE pa.in_hospital_no = pi.in_hospital_no AND pa.del_flag = 0 AND pa.status = ''1'' AND (pa.name LIKE ''%ECMO%'' OR pa.name LIKE ''%体外膜肺%'')) THEN 1 ELSE 0 END AS ecmo", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND d.diag_name LIKE ''%细菌性肺炎%'') THEN 1 ELSE 0 END AS cause_bacterial_pneumonia", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%病毒性肺炎%'' OR d.diag_name LIKE ''%病毒肺炎%'')) THEN 1 ELSE 0 END AS cause_viral_pneumonia", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%真菌性肺炎%'' OR d.diag_name LIKE ''%肺真菌%'')) THEN 1 ELSE 0 END AS cause_fungal_pneumonia", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND d.diag_name LIKE ''%吸入性肺炎%'') THEN 1 ELSE 0 END AS cause_aspiration", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%肺挫伤%'' OR d.diag_name LIKE ''%肺挫裂伤%'')) THEN 1 ELSE 0 END AS cause_contusion", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%脓毒症%'' OR d.diag_name LIKE ''%感染性休克%'') AND d.diag_name NOT LIKE ''%肺炎%'') THEN 1 ELSE 0 END AS cause_extrapulmonary_infection", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%多发伤%'' OR d.diag_name LIKE ''%创伤性休克%'')) THEN 1 ELSE 0 END AS cause_extrapulmonary_trauma", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%重症胰腺炎%'' OR d.diag_name LIKE ''%急性胰腺炎%'')) THEN 1 ELSE 0 END AS cause_pancreatitis", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%大量输血%'' OR d.diag_name LIKE ''%输血相关%'')) THEN 1 ELSE 0 END AS cause_massive_transfusion", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%体外循环%'' OR d.diag_name LIKE ''%心脏术后%'')) THEN 1 ELSE 0 END AS cause_cardiopulmonary_bypass", "CASE WHEN pi.gender = ''男'' OR pi.gender = ''1'' OR pi.gender = ''M'' THEN 1 ELSE 0 END AS sex_male", "CASE WHEN REGEXP_LIKE(TRIM(pi.age), ''^[0-9]{1,3}$'') THEN CAST(TRIM(pi.age) AS INT) END AS age_num", "(SELECT SUM(DATEDIFF(DAY, CASE WHEN pt.tube_set_start_time < :periodStart THEN :periodStart ELSE pt.tube_set_start_time END, CASE WHEN pt.tube_set_end_time IS NOT NULL AND pt.tube_set_end_time < :periodEnd THEN pt.tube_set_end_time WHEN pt.tube_take_away_time IS NOT NULL AND pt.tube_take_away_time < :periodEnd THEN pt.tube_take_away_time ELSE :periodEnd END)) FROM \"zing_icu_db_prod\".\"patient_tube\" pt WHERE pt.patient_id = pi.id AND pt.del_flag = 0 AND pt.tube_set_start_time < :periodEnd AND (pt.tube_set_end_time IS NULL OR pt.tube_set_end_time >= :periodStart) AND (pt.tube_code LIKE ''%气管插管%'' OR pt.tube_code LIKE ''%气管切开%'' OR pt.tube_name LIKE ''%气管%'')) AS mv_days", "(SELECT SUM(DATEDIFF(MINUTE, pr.start_time, pr.end_time)) FROM \"zing_doctor_db_prod\".\"patient_doc_prone_record\" pr WHERE pr.patient_id = pi.id AND pr.status = 1 AND pr.record_status = ''submitted'' AND pr.start_time < :periodEnd AND (pr.end_time IS NULL OR pr.end_time >= :periodStart)) AS prone_min", "CASE WHEN pi.out_vest_type = 3 AND pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS died_in_period"]',
       `where_conds`        = '["pi.del_flag = 0", "pi.in_depart_time IS NOT NULL", "pi.in_depart_time < :periodEnd", "(pi.out_depart_time IS NULL OR pi.out_depart_time >= :periodStart)"]',
       `group_cols`         = '[]',
       `note`               = 'ARDS 人群/分度/病因/呼吸支持方式；含机械通气天数与俯卧位时长（分钟）',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_ards';
UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = '评分_资源',
       `source_table`       = 'config_staff',
       `alias`              = 's',
       `patient_key`        = 'user_id',
       `in_hospital_no_key` = 'work_no',
       `patient_name_key`   = 'realname',
       `depart_key`         = 'depart_code',
       `select_cols`        = '["s.id AS user_id", "s.realname AS realname", "s.work_no AS work_no", "s.depart_code AS depart_code", "s.depart_name AS depart_name", "s.post_name AS post_name", "s.office_date AS office_date", "s.leave_date AS leave_date"]',
       `derive_cols`        = '["CASE WHEN s.post_name LIKE ''%医师%'' THEN 1 ELSE 0 END AS is_doctor", "CASE WHEN s.post_name LIKE ''%护士%'' OR s.post_name LIKE ''%护师%'' THEN 1 ELSE 0 END AS is_nurse"]',
       `where_conds`        = '["s.del_flag = 0", "s.leave_date IS NULL", "(s.depart_name LIKE ''%ICU%'' OR (s.depart_name LIKE ''%重症%'' AND s.depart_name NOT LIKE ''%呼吸与危重症%''))"]',
       `group_cols`         = '[]',
       `note`               = 'ICU 医护人员花名册（config_staff）；排班表未启用，以档案科室近似全职口径',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_staff';
UPDATE `quality_fact_def`
   SET `status`             = 'ACTIVE',
       `domain_code`        = '患者流转',
       `source_table`       = 'patient_info',
       `alias`              = 'pi',
       `patient_key`        = NULL,
       `in_hospital_no_key` = NULL,
       `patient_name_key`   = NULL,
       `depart_key`         = 'depart_code',
       `select_cols`        = '["pi.id AS patient_id", "pi.in_hospital_no AS in_hospital_no", "pi.name AS patient_name", "pi.gender AS gender", "pi.age AS age", "pi.depart_code AS depart_code", "pi.ward_name AS ward_name", "pi.out_vest_type AS out_vest_type", "pi.charge_doctor_name AS charge_doctor_name", "pi.in_depart_time AS in_depart_time", "pi.out_depart_time AS out_depart_time", "pi.out_hospital_time AS out_hospital_time", "pi.is_plan AS is_plan", "pi.bed_code AS bed_code", "CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"]',
       `derive_cols`        = '["CASE WHEN pi.in_depart_time >= :periodStart THEN 1 ELSE 0 END AS admitted_in_period", "CASE WHEN pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS out_in_period", "CASE WHEN pi.out_vest_type = 3 THEN 1 ELSE 0 END AS died", "CASE WHEN pi.out_vest_type = 3 AND pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS died_in_period", "CASE WHEN (pi.out_vest_type IS NULL OR pi.out_vest_type <> 3) AND pi.out_depart_time >= :periodStart AND pi.out_depart_time < :periodEnd THEN 1 ELSE 0 END AS survived_out_in_period", "DATEDIFF(DAY, CASE WHEN pi.in_depart_time < :periodStart THEN :periodStart ELSE pi.in_depart_time END, CASE WHEN (pi.out_depart_time IS NULL OR pi.out_depart_time > :periodEnd) THEN :periodEnd ELSE pi.out_depart_time END) AS stay_days", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND (d.diag_name LIKE ''%术后%'' OR d.diag_name LIKE ''%手术后%'')) THEN 1 ELSE 0 END AS post_op", "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info\" p2 WHERE p2.in_hospital_no = pi.in_hospital_no AND p2.id <> pi.id AND p2.del_flag = 0 AND p2.out_depart_time IS NOT NULL AND p2.out_depart_time < pi.in_depart_time AND DATEDIFF(HOUR, p2.out_depart_time, pi.in_depart_time) <= 48) THEN 1 ELSE 0 END AS readmit_48h"]',
       `where_conds`        = '["pi.del_flag = 0", "pi.in_depart_time IS NOT NULL", "pi.in_depart_time < :periodEnd", "(pi.out_depart_time IS NULL OR pi.out_depart_time >= :periodStart)"]',
       `group_cols`         = '[]',
       `note`               = 'ICU 患者主表；床日按「本周期 ∩ 在院区间」取交集，跨月自动切分',
       `update_time`        = CURRENT_TIMESTAMP
 WHERE `fact_name` = 'fact_patient_stay';

-- ---- 指标定义更新 ----

UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'post_op = 1 AND is_plan = 0 AND admitted_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2026-10-15落地：源字段 patient_info.is_plan=0（非计划转入，业务方确认口径）+诊断文本术后；2024版ICU-14的「非早期预警/麻醉诱导前无计划」细分需麻醉记录，当前按源系统计划标记计',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_20';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_prevent_status = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '任一预防措施（药物/机械/滤器）即计入；2024版ICU-07口径，源为医嘱侧',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_14';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_only_drug = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '仅药物预防（未做机械预防、未置滤器）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_404';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_drug_status = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '含合并其他预防措施者',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_405';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_only_mech = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '仅机械预防（肢体加压泵/梯度压力弹力袜等）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_406';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_mech_status = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '含合并其他预防措施者',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_407';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_only_filter = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '仅滤器（未做药物/机械预防）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_408';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_filter_status = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '含合并其他预防措施者',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_409';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_prevent_status = 1 AND dvt_diag = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '预防 + 诊断（诊断文本含深静脉血栓/下肢静脉血栓/肺栓塞）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_411';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_prevent_status = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '预防 + 本周期内死亡',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_412';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'dvt_prevent_status = 1 AND dvt_diag = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '预防 + 诊断 + 本周期内死亡',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_413';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'unplanned_removal = 1 AND device_kind = ''气道导管''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-12分子：patient_tube.is_not_plan_extubation=1；源与导管统计页同表同口径',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_17';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'removed_in_period = 1 AND device_kind = ''气道导管''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-12/13共同分母：本周期内拔管例数',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_18';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'reintubation_48h = 1 AND device_kind = ''气道导管''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-13分子：计划拔管后48h再插管（is_reintubation24 字段名为24、实际口径48h，见 TubeStatsMapper）；源字段无法区分再插管是否继发于非计划拔管，按排除非计划拔管行近似',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_19';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'device_infection = 1 AND device_kind = ''气道导管''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       `remark`                 = '2024版ICU-16分子：以医生确诊录入（patient_doc_device_infection, VAP）为准，确诊时间落在置入～拔除+48h窗口；需先在「导管相关感染确诊」页录入',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_24';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'SUM',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'device_kind = ''气道导管''',
       `expr_numerator`         = 'device_days',
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-16分母。当前以气管插管/气切留置天数代理有创通气天数（呼吸机使用记录接入后替换事实层取数列）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_25';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'device_infection = 1 AND device_kind = ''CVC''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       `remark`                 = '2024版ICU-17分子：医生确诊录入（CRBSI），窗口=置入～拔除+48h；血管导管含CVC/PICC/PORT/脐静脉导管，当前按CVC白名单覆盖',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_26';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'SUM',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'device_kind = ''CVC''',
       `expr_numerator`         = 'device_days',
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-17分母：CVC留置天数，按「本周期∩留置区间」切分，未拔截止转出带管/周期末',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_27';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'device_infection = 1 AND device_kind = ''导尿管''',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       `remark`                 = '2015版十五：CAUTI发生例数，以医生确诊录入（CAUTI）为准，窗口=置入～拔除+48h',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_425';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'SUM',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'device_kind = ''导尿管''',
       `expr_numerator`         = 'device_days',
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2015版十五分母：导尿管留置总天数（例/千导尿管日）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_426';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'bed_days > 0',
       `expr_numerator`         = 'bed_days',
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-01分母：床位开放记录按「本周期∩开放区间」切分求和；与占用床日（quality_2）同构，二者相除即床位使用率（规则层 LOCAL_BED_USAGE）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_301';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'is_doctor = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2026-10-15连库激活：config_staff 在职人员按档案科室（ICU/重症相关，排除呼吸与危重症医学科）+岗位含医师计；排班表未启用，暂无在岗班次过滤（2024版ICU-02口径）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_302';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'is_nurse = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '同 quality_302：岗位含护士/护师计（2024版ICU-03口径）',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_303';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'open_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '2024版ICU-02/03分母：本周期内处于开放状态的床位数',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_304';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'AVG',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'ards = 1 AND mv_days > 0',
       `expr_numerator`         = 'mv_days',
       `expr_denominator_where` = 'ards = 1 AND mv_days > 0',
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '以气管插管/气切留置天数代理（呼吸机使用记录接入后替换事实层取数列）；分母限定有创通气的ARDS患者',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_477';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'AVG',
       `agg`                    = 'SUM',
       `scale`                  = NULL,
       `expr_where`             = 'ards = 1 AND prone_min > 0',
       `expr_numerator`         = 'prone_min / 1440.0',
       `expr_denominator_where` = 'ards = 1 AND prone_min > 0',
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '取已提交的俯卧位通气治疗记录（patient_doc_prone_record）时长求和÷患者数；分母限定实施过俯卧位的ARDS患者',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_486';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'ards = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '按ICU转归死亡计（国家ICU-11口径）；严格院内死亡需病案首页出院结局',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_487';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'ards_mild = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '同 quality_487 口径',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_488';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'ards_moderate = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '同 quality_487 口径',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_489';
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'COUNT',
       `agg`                    = 'PT_COUNT',
       `scale`                  = NULL,
       `expr_where`             = 'ards_severe = 1 AND died_in_period = 1',
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `dims`                   = '["depart_code"]',
       `patient_fields`         = NULL,
       `remark`                 = '同 quality_487 口径',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_490';
