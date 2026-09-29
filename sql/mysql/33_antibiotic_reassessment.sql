-- 33_antibiotic_reassessment.sql
-- 抗感染：48～72 小时复评任务与复评留痕（MySQL / MariaDB）
-- 对应达梦版：sql/33_antibiotic_reassessment.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `patient_doc_abx_reassessment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `decision_record_id` BIGINT NOT NULL COMMENT '关联抗感染决策记录 ID',
  `patient_id` VARCHAR(64) NOT NULL COMMENT 'ICU 患者 ID',
  `patient_no` VARCHAR(64) COMMENT '住院号/就诊号',
  `in_hospital_no` VARCHAR(64) COMMENT 'ICU 原始住院号',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `review_due_time` TIMESTAMP NOT NULL COMMENT '计划复评时间，默认决策后48小时',
  `review_time` TIMESTAMP NULL COMMENT '实际复评时间',
  `review_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/SKIPPED/VOID',
  `culture_summary` VARCHAR(1000) COMMENT '培养/药敏复核摘要',
  `clinical_response` VARCHAR(1000) COMMENT '临床疗效评价',
  `pct_trend` VARCHAR(500) COMMENT 'PCT趋势摘要',
  `decision_action` VARCHAR(32) COMMENT 'CONTINUE/DE_ESCALATE/ESCALATE/SWITCH/STOP/OTHER',
  `doctor_decision` VARCHAR(1000) COMMENT '医生复评结论',
  `doctor_id` VARCHAR(64),
  `doctor_name` VARCHAR(64),
  `remark` VARCHAR(1000),
  `void_flag` TINYINT NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_abx_reassessment_patient` (`patient_id`, `review_status`),
  KEY `idx_abx_reassessment_due` (`review_status`, `review_due_time`),
  KEY `idx_abx_reassessment_record` (`decision_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抗感染48-72小时复评任务与留痕';
