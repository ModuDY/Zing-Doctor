-- 44_patient_round_record.sql
-- 医生查房记录表（MySQL / MariaDB）
-- 对应达梦版：sql/44_patient_round_record.sql
--
-- 查房记录是患者日级的诊疗计划记录，与班次级的交班表分开。
-- 同一患者同一天只保留一份，保存时自动覆盖更新（Service 层控制）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `patient_doc_round_record` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `patient_id` VARCHAR(64) NOT NULL COMMENT 'ICU 患者 ID',
  `in_hospital_no` VARCHAR(64) COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `round_date` DATE NOT NULL COMMENT '查房日期（同一患者同一天只保留一份）',
  `main_problem` VARCHAR(1000) COMMENT '今日主要问题',
  `infection_judgment` VARCHAR(1000) COMMENT '感染判断',
  `respiratory_plan` VARCHAR(1000) COMMENT '呼吸支持计划',
  `circulatory_plan` VARCHAR(1000) COMMENT '循环支持计划',
  `renal_sedation_plan` VARCHAR(1000) COMMENT '镇静镇痛/肾脏支持计划',
  `abx_plan` VARCHAR(1000) COMMENT '抗菌药调整计划',
  `recheck_items` VARCHAR(1000) COMMENT '今日复查项目',
  `treatment_goal` VARCHAR(1000) COMMENT '治疗目标',
  `tomorrow_focus` VARCHAR(1000) COMMENT '明日重点',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0已删除',
  `create_by` VARCHAR(64) COMMENT '创建人',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(64) COMMENT '更新人',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_round_patient_date` (`patient_id`, `round_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生查房记录（患者日级诊疗计划）';
