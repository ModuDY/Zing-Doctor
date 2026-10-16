-- =====================================================================
-- 60_vasoactive_page.sql
-- 页面注册：血管活性药分析（休克患者滴定对照：执行时间轴 / 开立在用 / MAP 与乳酸趋势）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/60_vasoactive_page.sql
--
-- 页面：/page/vasoactive-analysis（患者级，从患者工作台进入，带 patientId）
-- 后端：GET /api/vasoactive/patients/{patientId}/analysis?hours=
--       取数与 SOFA 循环项同源（patient_advice_execute + _drug + _step / patient_advice /
--       监护观察项 oi_pjy·oi_ycpjy / LIS 乳酸），不另起口径。
-- 幂等：按 page_code 判重，可重复执行。
--
-- 编号变更：本脚本原为 58 号，与 58_msg_push.sql 撞号（两个不同功能的脚本用同一个号，
--   清单登记时必然只能留一个，另一个永远不会被执行）。已改为 60 号并补登记到
--   install.sh 三处清单 / install-mariadb-debian.sh / build-delivery.ps1 / dm_to_mysql.py。
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
