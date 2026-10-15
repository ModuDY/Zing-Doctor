-- =====================================================================
-- 52_device_infection_page.sql（MySQL 镜像）
-- 对应达梦版：sql/52_device_infection_page.sql
-- 幂等：按 page_code 判重。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'device-infection', '导管相关感染确诊', '/page/device-infection',
  'VAP/CRBSI/CAUTI 三型感染共用录入表单；确诊记录驱动 quality_24/26/425 与规则层三条千导管日发病率（2024版ICU-16/17、2015版十五口径）', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'device-infection');
