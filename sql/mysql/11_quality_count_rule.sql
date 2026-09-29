-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 医生决策系统 - 质控「指标规则层」增量脚本（达梦 DM8）
--
-- 定位：补上此前遗漏的一层 —— 真正的业务指标。
--
--   此前三层： 数据源 sources.yaml → 事实层 fact → 指标 quality_xxx
--   我们把 quality_xxx 叫成了「指标」，但它其实只是**原子项**（一个数人头/数天数的量），
--   自身成不了率。真正的指标在 ICU 侧是 zing_icu_db_prod.quality_count_rule：
--
--       指标值 = 原子项(numerator_code) ÷ 原子项(denominator_code) × percent_rate
--
--   例：ICU镇痛评估率 = quality_306（做了镇痛评估的人数）÷ quality_403（同期患者总数）× 100
--       VAP 发病率    = quality_24 ÷ quality_25 × 1000   （例/千呼吸机日）
--
--   复用是常态而非特例：quality_449 被 17 条指标当分母、quality_403 被 13 条当分母。
--   没有这一层，「同期患者总数」这段口径要在 13 条指标里各算一遍 —— 改一次口径要动 13 处。
--
-- 建表位置：医生自有库 zing_doctor_db_prod（可写）。
--   源数据在 ICU 侧 zing_icu_db_prod，我们只读；两边同实例（application.yml 中 host/port 相同），
--   因此**一条跨 schema 的 INSERT … SELECT 即可同步**，不需要任何跨库中间件。
--
-- 为什么不同步到 ICU 而是拉过来：本表额外增加了 target_value / warning_value 等本院自管字段，
--   ICU 侧那四列目前全是 NULL，但质控的「达标与否」判定必须有地方存。
--   同步后本院可自行配置，不再受 ICU 表结构与权限约束。
--
-- 幂等：DbInit 会对 CREATE TABLE / CREATE INDEX 做存在性检查；同步语句带 NOT IN 幂等，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/11_quality_count_rule.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `quality_count_rule` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
-- ICU 侧 quality_count_rule.id。用 rule_id 而非用源 id 当主键：,
-- 本院将来可能自建规则，不能依赖外部主键空间。,
  `rule_id` VARCHAR(64)    NOT NULL COMMENT 'ICU 侧规则 id，本表唯一约束，用于幂等同步',
  `count_name` VARCHAR(200),
  `quality_type_code` VARCHAR(8),
  `is_visible` INT            DEFAULT 1,
-- is_show_page=0 的规则不进看板（如「ICU实际病死率」「感染性休克1h集束化治疗完成率」）,
  `is_show_page` INT            DEFAULT 1,
  `sort_no` INT            DEFAULT 0,
  `remark` VARCHAR(1000),
  `numerator_code` VARCHAR(64) COMMENT '分子原子项 code，指向 quality_metric_def.index_code',
  `denominator_code` VARCHAR(64) COMMENT '分母原子项 code，指向 quality_metric_def.index_code',
-- percent_unit 取自 ICU 侧，但**不可直接当单位用**：,
-- percent_rate=1000 的两条（VAP / CRBSI）该字段仍写 '%'，实际是「例/千日」。,
  `percent_unit` VARCHAR(16),
-- 放大系数：100 → 百分比；1000 → ‰（例/千日）；1 → 原样（不放大）,
  `percent_rate` INT            DEFAULT 100 COMMENT '放大系数 100/1000/1；值 = 分子 ÷ 分母 × 本值',
  `percent_precision` INT            DEFAULT 2,
  `depart_code` VARCHAR(64),
-- 本院自管（ICU 侧原表有这四列但当前全 NULL，落到本院后可自行维护）,
  `target_value` DECIMAL(20,6) COMMENT '本院自管目标值（ICU 侧未配）；达标线，留空则不判达标',
  `warning_value` DECIMAL(20,6) COMMENT '本院自管预警值（ICU 侧未配）；跌出此线为预警，留空则只判达标与否',
-- 达标方向：UP=越高越好（依从率 / 完成率类，多数指标）/ DOWN=越低越好（病死率 / 感染率类）。,
-- 这一列不能省：同一个数值在两类指标上结论正好相反，,
-- 而「送检率」和「感染率」的阈值本身看不出方向，判错会把不达标标成达标 —— 比不判定更危险。,
  `target_direction` VARCHAR(16)    DEFAULT 'UP' COMMENT '达标方向：UP=越高越好 / DOWN=越低越好；决定「达标」是 ≥ 还是 ≤ 目标值',
  `target_line_color` VARCHAR(32),
  `warning_line_color` VARCHAR(32),
  `source_status` INT            DEFAULT 1,
  `sync_time` TIMESTAMP,
  `status` INT            DEFAULT 1 COMMENT '1 启用 / 0 停用',
  `update_time` TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='质控指标规则（真正的业务指标 = 分子原子项 ÷ 分母原子项 × 放大系数；源同步自 ICU）';
CALL zing_add_index('quality_count_rule', 'uk_qcr_rule', 1, '`rule_id`');
CALL zing_add_index('quality_count_rule', 'idx_qcr_show', 0, '`is_show_page`, `sort_no`');

-- ---------------------------------------------------------------------
-- 从 ICU 侧同步规则 —— 默认**不随本脚本执行**，见下方说明
--
-- 为什么不自动跑：这条语句跨 schema 引用 zing_icu_db_prod。全新安装时 ICU 库可能
-- 尚未就绪（或库账号无跨 schema 读权限），一旦报错会让整条初始化链中断，
-- 安装脚本据此判定「初始化失败」—— 为一个可选的同步动作拖垮整个安装不值得。
--
-- 因此同步改为**运行期动作**：看板右上角「同步指标规则」按钮即调 POST /api/quality/rules/sync，
-- 幂等（只插 rule_id 不存在的），失败会在页面上直接给出原因，比埋在脚本里报错好排查。
--
-- DBA 若想在此处直接灌数，把下面整段（含 BEGIN/END）取消注释后执行即可。
-- 用动态 SQL + 异常吞掉是刻意的：静态 SQL 引用不存在的表会在**编译期**报错，异常捕获不住。
-- ---------------------------------------------------------------------
-- BEGIN
--   EXECUTE IMMEDIATE
--     'INSERT INTO `quality_count_rule` '
--     || '(`rule_id`,`count_name`,`quality_type_code`,`is_visible`,`is_show_page`,`sort_no`,`remark`,'
--     || '`numerator_code`,`denominator_code`,`percent_unit`,`percent_rate`,`percent_precision`,'
--     || '`depart_code`,`source_status`,`sync_time`,`status`) '
--     || 'SELECT src.`id`, src.`count_name`, src.`quality_type_code`, '
--     || 'CAST(src.`is_visible` AS INT), CAST(src.`is_show_page` AS INT), CAST(src.`sort_no` AS INT), '
--     || 'src.`remark`, src.`numerator_code`, src.`denominator_code`, src.`percent_unit`, '
--     || 'CAST(src.`percent_rate` AS INT), CAST(src.`percent_precision` AS INT), '
--     || 'src.`depart_code`, CAST(src.`status` AS INT), CURRENT_TIMESTAMP, 1 '
--     || 'FROM `quality_count_rule` src '
--     || 'WHERE src.`del_flag` = ''0'' '
--     || 'AND src.`id` NOT IN (SELECT `rule_id` FROM `quality_count_rule`)';
-- EXCEPTION
--   WHEN OTHERS THEN NULL;
-- END;
-- /
--
-- 同步时两处刻意的处理，改这段前请先读懂：
--   1. CAST 是兜住源列可能为字符型（源表 percent_rate 取值形如 '100'）。
--      若某天源端改了列类型导致 CAST 报错，去掉对应 CAST 直接取列即可。
--   2. 重复规则按 id **全列，不去重**。
--      ICU 侧「ICU医师床位比」「ICU护士床位比」各有 2 条（id 不同、分子分母相同），
--      源表去重名 60 个 / 实际规则 62 条。按业务要求全部保留，由页面如实呈现，
--      不擅自吞掉一条可能的口径变体。
--   3. **不同步** target_value 等四列：源端全 NULL，同步会把本院已配置的值覆盖掉。
