-- =====================================================================
-- 患者工作台页面注册（幂等）
--
-- 供 ICU 外链访问：/entry/patient-workbench
-- 仅注册页面，不新增业务表；可重复执行。
-- =====================================================================

DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" = 'patient-workbench';

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('patient-workbench', '患者工作台', '/page/patient-workbench',
     'ICU 在科患者基础信息工作台：患者检索、床位概览与快捷进入临床决策页面', 1);
