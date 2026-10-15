-- =====================================================================
-- 56_manual_pages.sql
-- 页面注册：血流动力学与心功能（含 ADL）/ 床旁超声教学
-- （达梦 DM8）MySQL 镜像见 sql/mysql/56_manual_pages.sql
--
-- 页面：/page/hemodynamics（单患者，挂「临床分析」菜单组）
--       /page/echo-teaching（科室教学静态页，无患者依赖）
-- 后端：module/hemodynamics（/api/hemodynamics/*）、module/adl（/api/adl/*）
-- 表：  patient_doc_hemodynamics / patient_doc_adl（见 55_hemodynamics_adl.sql）
-- 幂等：按 page_code 判重，可重复执行。
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'hemodynamics')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('hemodynamics', '血流动力学与心功能', '/page/hemodynamics',
       '手工录入 CO/CVP/MAP/EF，SVR=(MAP-CVP)/CO×80 自动计算 + 趋势；含 ADL（Barthel 指数）十项录入与依赖分级', 1);
  END LOOP;
END;
/

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'echo-teaching')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('echo-teaching', '床旁超声教学', '/page/echo-teaching',
       'POCUS 八部位教学：肺部/眼球/下腔静脉/腹部/膈肌/肾脏/心脏/血管 操作要点与判读入门（静态内容）', 1);
  END LOOP;
END;
/
