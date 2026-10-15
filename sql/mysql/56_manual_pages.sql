-- =====================================================================
-- 56_manual_pages.sql（MySQL 镜像）
-- 对应达梦版：sql/56_manual_pages.sql
-- 幂等：按 page_code 判重。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'hemodynamics', '血流动力学与心功能', '/page/hemodynamics',
  '手工录入 CO/CVP/MAP/EF，SVR=(MAP-CVP)/CO×80 自动计算 + 趋势；含 ADL（Barthel 指数）十项录入与依赖分级', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'hemodynamics');

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'echo-teaching', '床旁超声教学', '/page/echo-teaching',
  'POCUS 八部位教学：肺部/眼球/下腔静脉/腹部/膈肌/肾脏/心脏/血管 操作要点与判读入门（静态内容）', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'echo-teaching');
