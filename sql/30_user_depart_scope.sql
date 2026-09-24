-- =====================================================================
-- 30) 患者工作台：科室边界与管理员名单
--
-- 背景：
--   患者工作台要「按在科患者组织待办」，读的数据跨重症系统多个科室。
--   直登账号能看哪些科室，此前在系统里<b>没有任何依据</b> —— 本库没有角色表
--   （zing_doctor_db_prod 下不存在 sys_role / sys_user_role，42 张表全是业务表），
--   所以「这条数据该不该让这个人看到」长期处于不受控状态。
--
--   本次把科室边界定下来：
--     1. 名单内的管理员账号：豁免科室限制，全院可见、可切任意科室；
--     2. 其余账号：按重症侧 sys_user_depart 的授权科室收敛；
--     3. 重症侧查不到该账号的：一律视为「无科室」，<b>不退回全院</b> ——
--        宁可让他看到空白并报上来，也不能让一个未识别的账号看全科数据；
--     4. 授权了多个科室的：让用户进工作台时自己选，不替他猜。
--
-- 依赖：
--   必须在 14_param_framework.sql 之后执行 —— 用到它扩展的 param_type /
--   default_value 等列，以及 sys_param_group 表。
--
-- 幂等说明：
--   分组与参数都用 INSERT ... SELECT ... WHERE NOT EXISTS 补建，可重复执行。
--
-- 不执行的后果：
--   功能不挂 —— Java 侧读不到该参数时会回退 application.yml 的默认值
--   （zing.workbench.super-users = admin,zing）。但现场若需要增删管理员，
--   就只能在页面上做不了，得改配置重启。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/30_user_depart_scope.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 分组：患者工作台（供 WORKBENCH_* 参数挂载）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param_group"
    ("id", "group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param_group"),
       'workbench', '患者工作台', 50, 'User',
       '患者工作台：科室可见范围、待办规则等相关参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param_group" WHERE "group_code" = 'workbench');

-- ---------------------------------------------------------------------
-- 参数：工作台管理员名单（逗号分隔的本系统用户名）
--
-- 值语义：
--   名单内的账号在患者工作台上豁免科室限制，可看并可切换全部科室。
--   名单外的账号按其重症侧账号授权（sys_user_depart）收敛；
--   重症侧查不到该账号的一律按「无科室」处理，不退回全院。
--
-- 为什么是「用户名名单」而不是角色表：
--   本库没有 sys_role / sys_user_role，短期也不打算为此引入一整套权限体系。
--   名单放在这里，页面可改、有据可查，比写死在 Java 常量里好；
--   将来若真上了角色体系，把这里换成按角色判定即可，接口不用改。
--
-- 注意：
--   名单按<b>本系统 username</b> 比对，而科室Authorization按同一个 username
--   去重症侧反查，两边必须能对应上 —— 这也是为什么外链自动注册的账号
--   用「工号 → 医生姓名」推导而不能用手机号。
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT 'WORKBENCH_SUPER_USERS', '工作台管理员名单', 'admin,zing', 'workbench', 1, 1, 'text',
       NULL, 'admin,zing', 0, NULL,
       '逗号分隔的本系统用户名。名单内账号在患者工作台上豁免科室限制、全院可见；名单外账号按其重症侧账号授权科室收敛，重症侧查不到该账号则视为无科室。多个科室时进页面让用户自选。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'WORKBENCH_SUPER_USERS');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "group_code", "group_name" FROM "zing_doctor_db_prod"."sys_param_group"
--    WHERE "group_code" = 'workbench';
--   SELECT "param_key", "param_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'WORKBENCH_SUPER_USERS';
--   -- 两者都应各返回 1 行
-- ---------------------------------------------------------------------
