-- 32_abx_page_merge.sql
-- 「疑似感染患者列表」并入「患者工作台」后的页面注册信息更新
--
-- 只改展示名与说明，frontend_path 保持 /page/abx-patient-list 不变：
-- ICU 外链 /entry/abx-patient-list 依赖这条注册查 frontend_path，
-- 改掉它外链会 302 到不存在的地址。前端路由会把该路径重定向到
-- /page/patient-workbench?view=infection，所以外链参数（extToken / expire / sign /
-- departCode）由前端 redirect 原样透传，后端这边什么都不用动。
--
-- 依赖：sys_page_config 中该 page_code 的注册（sql/02_seed.sql 全量初始化写入）。
-- 顺序：必须排在 27_restore_config_snapshot.sql 之后 —— 那份快照会整表覆盖
-- sys_page_config，排在它前面会被覆盖回旧名称。
-- 幂等：UPDATE 可重复执行；未注册（全新库尚未跑 02）时影响 0 行，不报错。
-- =====================================================================

UPDATE "zing_doctor_db_prod"."sys_page_config"
   SET "page_name" = '感染风险患者（患者工作台视图）',
       "remark" = '已并入患者工作台：独立页面不再渲染。本注册保留用于兼容 ICU 外链 /entry/abx-patient-list 与旧链接，前端会重定向到 /page/patient-workbench?view=infection'
 WHERE "page_code" = 'abx-patient-list';
