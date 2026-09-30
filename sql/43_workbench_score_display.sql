-- =====================================================================
-- 43) 患者工作台：评分显示类型参数（达梦 DM8）
--
-- 背景：
--   工作台列表与床头卡的「评分」一格原先固定显示 SOFA。但有的科室日常用 APACHE II
--   （尤其是收治范围偏综合、习惯用 APACHE II 做死亡风险沟通的 ICU），看 SOFA 反而不直观。
--   前端已按参数 WORKBENCH_SCORE_DISPLAY 决定显示哪一个，但这个参数此前没有建库脚本 ——
--   参数设置页看不到它、也改不了，只能改代码。
--
--   取值：
--     SOFA     默认。显示 SOFA 总分（≥10 标红，Sepsis-3 器官功能障碍阈值）
--     APACHE2  显示 APACHE II 总分（≥25 标红，高危常用分界）
--
-- 依赖：
--   30_user_depart_scope.sql（workbench 分组）
--   14_param_framework.sql（param_type / options / default_value 等扩展列）
--   42_workbench_bed_sort_mode.sql（workbench 分组排序号已用到 4，本参数取 5）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 不执行的后果：
--   前端 initScoreDisplay() 读不到该参数时回退 SOFA —— 工作台照常工作，
--   但现场切不到 APACHE II，参数设置页也没有这一项。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/43_workbench_score_display.sql
-- =====================================================================

-- 显式取 id，不依赖 sys_param.id 上的默认序列。
-- 该序列的当前值可能落后于表中已有的 id（本库实测：MAX(id)=35 时 NEXTVAL 仍给 35，
-- 插入直接撞主键 pk_sys_param）。改用 MAX(id)+1 后任何库上都跑得通。
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'WORKBENCH_SCORE_DISPLAY', '工作台评分显示', '', 'workbench', 5, 1, 'select',
       '[{"label":"SOFA（默认）","value":"SOFA"},'
       || '{"label":"APACHE II","value":"APACHE2"}]',
       'SOFA', 0, NULL,
       '患者工作台列表与床头卡「评分」一格显示哪个评分。'
       || 'SOFA：显示 SOFA 总分，≥10 标红（Sepsis-3 器官功能障碍阈值）；'
       || 'APACHE2：显示 APACHE II 总分，≥25 标红。'
       || '参数值留空时回退默认值 default_value（SOFA）。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'WORKBENCH_SCORE_DISPLAY');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'WORKBENCH_SCORE_DISPLAY';
--   -- 应返回 1 行
-- ---------------------------------------------------------------------
