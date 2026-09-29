-- =====================================================================
-- 35) 患者工作台：默认视图模式参数（达梦 DM8）
--
-- 背景：
--   工作台支持「列表视图 / 床头卡视图」两种呈现，顶栏可随时切换。但切换只落在
--   sessionStorage 里，换浏览器、换终端或换个人就回到默认 —— 科室若普遍用床头卡
--   （床旁查房、大屏观看），每个人每次进来都要手动切一次。
--
--   本次把「默认视图」做成参数 WORKBENCH_VIEW_MODE（table / cards），
--   现场在参数设置页改一次，全科生效。
--
-- 依赖：
--   30_user_depart_scope.sql（workbench 分组）
--   14_param_framework.sql（param_type / options / default_value 等扩展列）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 不执行的后果：
--   前端 initViewMode() 读不到该参数时回退默认 table —— 功能正常，但床头卡只能
--   靠每个人手动切，现场改不了默认值。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/35_workbench_view_mode.sql
-- =====================================================================

-- 显式取 id，不依赖 sys_param.id 上的默认序列。
-- 该序列的当前值可能落后于表中已有的 id —— 本库实测：表里 MAX(id)=35 时
-- SEQ_sys_param.NEXTVAL 仍然给出 35，插入直接撞主键 pk_sys_param。
-- （表里 id 是 1–10、32–35 这样带空洞的，并非全部由序列生成，序列自然对不上。）
-- 改用 MAX(id)+1 后，无论序列处在什么状态都能插入，脚本在任何库上都跑得通。
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'WORKBENCH_VIEW_MODE', '工作台默认视图', '', 'workbench', 2, 1, 'select',
       '[{"label":"列表视图","value":"table"},'
       || '{"label":"床头卡视图","value":"cards"}]',
       'table', 0, NULL,
       '患者工作台打开时的默认视图。'
       || 'table：列表视图，信息密度高，适合逐个核对与搜索；'
       || 'cards：床头卡视图，一张卡片一个患者，适合床旁查房与大屏观看。'
       || '用户在页面上的手动切换只对本次会话生效（存 sessionStorage），不改变这里的全院默认值。'
       || '参数值留空时回退默认值 default_value。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'WORKBENCH_VIEW_MODE');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'WORKBENCH_VIEW_MODE';
--   -- 应返回 1 行
-- ---------------------------------------------------------------------
