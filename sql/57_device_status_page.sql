-- =====================================================================
-- 57_device_status_page.sql
-- 页面注册：设备状态（只读，重症系统 config_device + 在科患者设备编码绑定 + 采集连接状态）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/57_device_status_page.sql
--
-- 页面：/page/device-status（科室级，无患者依赖，挂「质控/管理」菜单组）
-- 后端：GET /api/department-cockpit/device-status（口径与驾驶舱「设备接入态势」同源）
-- 幂等：按 page_code 判重，可重复执行。
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'device-status')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('device-status', '设备状态', '/page/device-status',
       '只读设备清单：编码/类型/科室/绑定患者/采集连接状态/最近更新；口径与驾驶舱设备接入态势同源（config_device）', 1);
  END LOOP;
END;
/
