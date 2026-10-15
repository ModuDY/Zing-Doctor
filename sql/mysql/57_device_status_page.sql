-- =====================================================================
-- 57_device_status_page.sql（MySQL 镜像）
-- 对应达梦版：sql/57_device_status_page.sql
-- 幂等：按 page_code 判重。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'device-status', '设备状态', '/page/device-status',
  '只读设备清单：编码/类型/科室/绑定患者/采集连接状态/最近更新；口径与驾驶舱设备接入态势同源（config_device）', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'device-status');
