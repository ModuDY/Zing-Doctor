-- =====================================================================
-- 54_infection_ards_pages.sql
-- 页面注册：院感分析（体温+感染指标） / ARDS 全院筛查
-- （达梦 DM8）MySQL 镜像见 sql/mysql/54_infection_ards_pages.sql
--
-- 页面：/page/infection-watch（单患者，挂「临床分析」菜单组）
--       /page/ards-screen（全院清单，挂「质控/管理」菜单组）
-- 后端：/api/vitals + /api/lab-trend?type=infection（新增面板）；
--       /api/ards/hospital-screen（全院筛查， departCode 按账号权限收敛）
-- 幂等：按 page_code 判重，可重复执行。
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'infection-watch')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('infection-watch', '院感分析', '/page/infection-watch',
       '单患者院感分析：体温 + WBC/PCT/CRP/IL-6 感染指标联合趋势（检验按项目名匹配，排除体液标本白细胞）', 1);
  END LOOP;
END;
/

BEGIN
  FOR r IN (
    SELECT 1 AS need FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_page_config"
                        WHERE "page_code" = 'ards-screen')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."sys_page_config"
      ("page_code", "page_name", "frontend_path", "remark", "status")
    VALUES
      ('ards-screen', 'ARDS 全院筛查', '/page/ards-screen',
       '全院 ARDS 诊断筛查清单（在院或已出科，诊断含 ARDS/急性呼吸窘迫综合征）；分级与通气达标分析在 ARDS 监测页', 1);
  END LOOP;
END;
/
