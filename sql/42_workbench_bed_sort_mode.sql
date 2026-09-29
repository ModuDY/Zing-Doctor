-- =====================================================================
-- 41) 患者工作台：床位排序口径参数（达梦 DM8）
--
-- 背景：
--   工作台默认按床位排序（查房沿床位走，医生开口问的也是「几床怎么样了」）。
--   但各院床号形态不一，排序口径也就没有唯一正确答案：
--     - 「12」「9」纯数字：按字符串排会排成 10 < 9，必须提取数字按数值比；
--     - 「12A」「A12」「12-1」带字母/符号：加床、分区床位的常见写法，
--       有的科室要先看完正式床位再看加床，有的恰恰相反。
--   原先写死一种口径（提取数值升序），遇到带字母床号的院区就只能改代码。
--
--   本次把口径做成参数 WORKBENCH_BED_SORT_MODE：
--     numeric     默认。提取数字按数值升序，不区分纯数字 / 含字母（与改造前一致）
--     digitFirst  纯数字床号在前，含字母的在后，两组内部各自按数值升序
--     letterFirst 含字母的在前，纯数字在后，两组内部各自按数值升序
--   无床位的（刚入科未分床、转科中）在三种口径下都排最后。
--
-- 依赖：
--   30_user_depart_scope.sql（workbench 分组）
--   14_param_framework.sql（param_type / options / default_value 等扩展列）
--
-- 幂等：WHERE NOT EXISTS 补建，可重复执行。
--
-- 不执行的后果：
--   前端 initBedSortMode() 读不到该参数时回退 numeric —— 排序照常工作，
--   但现场改不了口径（参数设置页也没有这一项）。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/41_workbench_bed_sort_mode.sql
-- =====================================================================

-- 显式取 id，不依赖 sys_param.id 上的默认序列。
-- 该序列的当前值可能落后于表中已有的 id（本库实测：MAX(id)=35 时 NEXTVAL 仍给 35，
-- 插入直接撞主键 pk_sys_param）。改用 MAX(id)+1 后任何库上都跑得通。
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'WORKBENCH_BED_SORT_MODE', '工作台床位排序', '', 'workbench', 4, 1, 'select',
       '[{"label":"默认（提取数值升序）","value":"numeric"},'
       || '{"label":"纯数字在前，含字母在后","value":"digitFirst"},'
       || '{"label":"含字母在前，纯数字在后","value":"letterFirst"}]',
       'numeric', 0, NULL,
       '患者工作台按床位排序时的口径。'
       || 'numeric：提取床号中的数字按数值升序，不区分是否含字母（9 床排在 10 床前）；'
       || 'digitFirst：纯数字床号在前、含字母的在后，两组内部各自按数值升序；'
       || 'letterFirst：含字母的在前、纯数字在后，两组内部各自按数值升序。'
       || '无床位的患者（刚入科未分床、转科中）在三种口径下都排在最后。'
       || '参数值留空时回退默认值 default_value（numeric）。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'WORKBENCH_BED_SORT_MODE');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "default_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_key" = 'WORKBENCH_BED_SORT_MODE';
--   -- 应返回 1 行
-- ---------------------------------------------------------------------
