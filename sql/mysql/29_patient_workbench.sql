-- =====================================================================
-- 29_patient_workbench.sql
-- 患者工作台页面注册（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/29_patient_workbench.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符，逻辑完全一致。
--
-- 供 ICU 外链访问：/entry/patient-workbench
-- 仅注册页面，不新增业务表；可重复执行。
--
-- 幂等写法与达梦一致：先按 page_code 删除再插入
-- （MySQL 虽有 ON DUPLICATE KEY，但达梦不支持，两边保持同一套写法）。
-- =====================================================================

DELETE FROM `sys_page_config`
 WHERE `page_code` = 'patient-workbench';

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('patient-workbench', '患者工作台', '/page/patient-workbench',
     'ICU 在科患者基础信息工作台：患者检索、床位概览与快捷进入临床决策页面', 1);
