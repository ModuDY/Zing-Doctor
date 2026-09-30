-- MySQL / MariaDB：48～72 小时复评窗口升级。
-- 幂等：MariaDB 10.0.2+ 支持 ADD COLUMN / ADD UNIQUE KEY IF NOT EXISTS，
-- 重复执行不会报 Duplicate column / Duplicate key（达梦版用 DECLARE+COUNT 同样幂等）。
ALTER TABLE `patient_doc_abx_reassessment`
  ADD COLUMN IF NOT EXISTS `treatment_start_time` TIMESTAMP NULL COMMENT '复评计时起点；优先首次实际给药时间' AFTER `depart_code`,
  ADD COLUMN IF NOT EXISTS `review_open_time`    TIMESTAMP NULL COMMENT '计时起点后48小时' AFTER `treatment_start_time`,
  ADD COLUMN IF NOT EXISTS `time_source`         VARCHAR(32) NULL COMMENT 'FIRST_ADMINISTRATION/DECISION_ACCEPTED' AFTER `review_due_time`;

UPDATE `patient_doc_abx_reassessment`
SET `treatment_start_time` = COALESCE(`treatment_start_time`, `create_time`),
    `review_open_time` = COALESCE(`review_open_time`, DATE_ADD(`create_time`, INTERVAL 48 HOUR)),
    `review_due_time` = CASE WHEN `review_status` = 'PENDING'
                             THEN DATE_ADD(`create_time`, INTERVAL 72 HOUR)
                             ELSE `review_due_time` END,
    `time_source` = COALESCE(`time_source`, 'DECISION_ACCEPTED');

ALTER TABLE `patient_doc_abx_reassessment`
  ADD UNIQUE KEY IF NOT EXISTS `ux_abx_reassessment_decision` (`decision_record_id`);
