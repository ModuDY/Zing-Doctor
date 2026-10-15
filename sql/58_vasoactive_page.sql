-- =====================================================================
-- 58_vasoactive_page.sql
-- 页面注册：血管活性药分析（休克患者滴定对照：执行时间轴 / 开立在用 / MAP 与乳酸趋势）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/58_vasoactive_page.sql
--
-- 页面：/page/vasoactive-analysis（患者级，从患者工作台进入，带 patientId）
-- 后端：GET /api/vasoactive/patients/{patientId}/analysis?hours=
--       取数与 SOFA 循环项同源（patient_advice_execute + _drug + _step / patient_advice /
--       监护观察项 oi_pjy·oi_ycpjy / LIS 乳酸），不另起口径。
-- 幂等：按 page_code 判重，可重复执行。
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'vasoactive-analysis')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('vasoactive-analysis', '血管活性药分析', '/page/vasoactive-analysis',
       '只读：执行链路时间轴（含调速步骤）+ 开立在用 + MAP/乳酸双轴趋势；与 SOFA 循环项同源', 1);
  END LOOP;
END;
/
