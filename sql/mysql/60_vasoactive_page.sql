-- =====================================================================
-- 60_vasoactive_page.sql（MySQL 镜像）
-- 对应达梦版：sql/60_vasoactive_page.sql
-- 幂等：按 page_code 判重。
-- 编号变更：原为 58 号，与 58_msg_push.sql 撞号，已改为 60 号并补登记到全部清单。
-- =====================================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

INSERT INTO `sys_page_config`
  (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
SELECT 'vasoactive-analysis', '血管活性药分析', '/page/vasoactive-analysis',
  '只读：执行链路时间轴（含调速步骤）+ 开立在用 + MAP/乳酸双轴趋势；与 SOFA 循环项同源', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_page_config` WHERE `page_code` = 'vasoactive-analysis');
