-- =====================================================================
-- 患者诊疗摘要页面注册（MySQL / MariaDB）
-- 对应达梦版：sql/41_patient_summary_page.sql
-- =====================================================================

DELETE FROM `sys_page_config`
 WHERE `page_code` = 'patient-summary';

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('patient-summary', '患者诊疗摘要', '/page/patient-summary',
     '单患者诊疗摘要：基本信息、生命支持、评分、感染指标、当前抗菌药、待办与快捷操作', 1);

