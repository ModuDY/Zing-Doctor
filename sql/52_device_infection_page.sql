-- =====================================================================
-- 52_device_infection_page.sql
-- 新页面注册：导管相关感染确诊（VAP / CRBSI / CAUTI 医生确诊录入）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/52_device_infection_page.sql
--
-- 页面：/page/device-infection（患者级，挂「临床分析」菜单组）
-- 后端：module/deviceinfection（/api/device-infection/*）
-- 表：  patient_doc_device_infection（见 50_quality_gap_impl.sql）
-- 幂等：按 page_code 判重，可重复执行。
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'device-infection')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('device-infection', '导管相关感染确诊', '/page/device-infection',
       'VAP/CRBSI/CAUTI 三型感染共用录入表单；确诊记录驱动 quality_24/26/425 与规则层三条千导管日发病率（2024版ICU-16/17、2015版十五口径）', 1);
  END LOOP;
END;
/
