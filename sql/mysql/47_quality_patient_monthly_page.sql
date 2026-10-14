-- =====================================================================
-- 患者月度质控指标页注册（MySQL / MariaDB）
-- 对应达梦版：sql/47_quality_patient_monthly_page.sql
-- =====================================================================

DELETE FROM `sys_page_config`
 WHERE `page_code` = 'quality-patient-monthly';

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('quality-patient-monthly', '患者月度质控指标', '/page/quality-patient-monthly',
     '单患者月度质控档案：患者切换（含已出科）、月份切换、命中指标与纳入情况、导出 Excel 与打印', 1);
