-- 32_abx_page_merge.sql
-- 「疑似感染患者列表」并入「患者工作台」后的页面注册信息更新（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/32_abx_page_merge.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符。
-- =====================================================================

UPDATE `sys_page_config`
   SET `page_name` = '感染风险患者（患者工作台视图）',
       `remark` = '已并入患者工作台：独立页面不再渲染。本注册保留用于兼容 ICU 外链 /entry/abx-patient-list 与旧链接，前端会重定向到 /page/patient-workbench?view=infection'
 WHERE `page_code` = 'abx-patient-list';
