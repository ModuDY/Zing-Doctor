-- =====================================================================
-- 51_quality_rule_local_gap.sql（MySQL 镜像）
-- 对应达梦版：sql/51_quality_rule_local_gap.sql
-- 补 CAUTI 发病率 LOCAL 规则（2015版口径：例数 ÷ 导尿管留置总天数 × 1000‰）。
-- 其余比率规则 ICU 侧已有（VAP/CRBSI/拔管率/再插管率/床位使用率/医护床位比/DVT预防率），
-- 原子项落地后自动出数，不重复自建。
-- 幂等：只插 rule_id 不存在的行。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `quality_count_rule`
  (`rule_id`, `count_name`, `is_visible`, `is_show_page`, `sort_no`, `remark`,
   `numerator_code`, `denominator_code`, `percent_unit`, `percent_rate`, `percent_precision`,
   `target_direction`, `origin`, `local_override`, `source_status`, `status`)
SELECT 'LOCAL_CAUTI_RATE', 'ICU导尿管相关泌尿系感染（CAUTI）发生率', 1, 1, 130,
  '本院自建：2015版重症质控指标十五 = CAUTI发生例数 ÷ 导尿管留置总天数 × 1000（例/千导尿管日）；原子项 quality_425/426 由 fact_device 计算',
  'quality_425', 'quality_426', '‰', 1000, 2,
  'DOWN', 'LOCAL', 0, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `quality_count_rule` WHERE `rule_id` = 'LOCAL_CAUTI_RATE');
