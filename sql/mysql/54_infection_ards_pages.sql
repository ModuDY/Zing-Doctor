-- =====================================================================
-- 54_infection_ards_pages.sql（MySQL 镜像）
-- 对应达梦版：sql/54_infection_ards_pages.sql
-- 幂等：按 page_code 判重。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'infection-watch', '院感分析', '/page/infection-watch',
  '单患者院感分析：体温 + WBC/PCT/CRP/IL-6 感染指标联合趋势（检验按项目名匹配，排除体液标本白细胞）', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'infection-watch');

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'ards-screen', 'ARDS 全院筛查', '/page/ards-screen',
  '全院 ARDS 诊断筛查清单（在院或已出科，诊断含 ARDS/急性呼吸窘迫综合征）；分级与通气达标分析在 ARDS 监测页', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'ards-screen');
