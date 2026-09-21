-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 医生决策系统 - 质控指标「率类引用」增量（达梦 DM8）
--
-- 背景：两条病死率指标的分子与分母不在同一张事实层上，单条 DSL SQL 表达不了：
--         ICU 实际病死率   = ICU死亡患者数 ÷ 同期患者总数（转出+出院）
--         ICU患者预计病死率 = ICU收治患者预计病死率之和 ÷ 同期患者总数（转出+出院）
--       分子来自评分/患者流转层，分母来自患者流转层，一条 fact 串不起两层聚合。
--       因此指标定义新增 numerator_metric / denominator_metric 两列，声明
--       「率 = 指标A ÷ 指标B」，由引擎在被引用指标算完之后按科室取行相除 ——
--       口径只在被引用指标里定义一处，避免复制条件后两条指标长期互相矛盾。
--
-- 本脚本做三件事：
--   A) quality_metric_def 增加两个引用列
--   B) quality_302 / quality_303 改为人工填报（MANUAL）
--   C) quality_30 / quality_31 改为引用型自动计算
--
-- 幂等：ALTER TABLE ADD COLUMN 由 DbInit 做列存在检查，可重复执行；
--       UPDATE 反复执行结果一致。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/16_quality_fatality_ref.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) 引用列
--    非空即表示本指标的分子/分母取自另一条指标的结果，不再编译本指标的 SQL。
-- ---------------------------------------------------------------------
CALL zing_add_column('quality_metric_def', 'numerator_metric', 'VARCHAR(32) COMMENT ''分子引用的指标编码（率类跨事实层用），如 quality_16；非空则不编译本指标 SQL''');
CALL zing_add_column('quality_metric_def', 'denominator_metric', 'VARCHAR(32) COMMENT ''分母引用的指标编码，如 quality_403''');
-- ---------------------------------------------------------------------
-- B) ICU 医师/护士总数改为人工填报
--    人员数量变动不频繁，由科室按月在配置页录入，不再等 config_staff 花名册接入。
--    原自动口径（post_name LIKE %医师% / %护士%）留在 remark 里，接入后随时可切回。
-- ---------------------------------------------------------------------
UPDATE `quality_metric_def`
   SET `impl_status`  = 'MANUAL',
       `fact_name`    = 'fact_staff',
       `remark`       = '人工填报：人员数量不常变，由科室按月在配置页录入。原计划接 config_staff 花名册（post_name LIKE %医师% / %护士%），未接入期间不给出假 0',
       `expr_version` = `expr_version` + 1,
       `update_time`  = CURRENT_TIMESTAMP
 WHERE `index_code` IN ('quality_302', 'quality_303');

-- ---------------------------------------------------------------------
-- C) 两条病死率改为「引用型」自动计算
--    分母同为 quality_403（同期患者总数（转出+出院））。
--
--    【放大系数是这一步最容易错的地方】
--      quality_30：分子是「死亡人数」→ 人数比人数，必须 ×100 才是百分数；
--      quality_31：分子是「百分数之和」→ 本身已是百分数口径，系数只能是 1，
--                  填 100 会让结果放大一百倍，且看板上的数字看起来「像那么回事」。
-- ---------------------------------------------------------------------
UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'RATE',
       `scale`                  = 100,
       `numerator_metric`       = 'quality_16',
       `denominator_metric`     = 'quality_403',
       `dims`                   = '[`depart_code`]',
       `expr_where`             = NULL,
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `remark`                 = '自动计算：ICU死亡患者数 ÷ 同期患者总数（转出+出院），即 quality_16 ÷ quality_403。分子分母都是既有指标，口径不在本行重复定义；改分子口径只需改 quality_16',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_30';

UPDATE `quality_metric_def`
   SET `impl_status`            = 'IMPL',
       `value_type`             = 'RATE',
       `scale`                  = 1,
       `numerator_metric`       = 'quality_15',
       `denominator_metric`     = 'quality_403',
       `dims`                   = '[`depart_code`]',
       `expr_where`             = NULL,
       `expr_numerator`         = NULL,
       `expr_denominator_where` = NULL,
       `remark`                 = '自动计算：ICU收治患者预计病死率之和 ÷ 同期患者总数（转出+出院），即 quality_15 ÷ quality_403。放大系数必须是 1 —— 分子本身已是百分数之和，再乘 100 会放大一百倍',
       `expr_version`           = `expr_version` + 1,
       `update_time`            = CURRENT_TIMESTAMP
 WHERE `index_code` = 'quality_31';

-- ---------------------------------------------------------------------
-- D) 执行结果核对（手动执行，不在自动升级通道里跑）
--    scale：quality_30 = 100，quality_31 = 1；两条的 denominator_metric 都应是 quality_403。
--    install.sh 的通道会把这个文件整体灌进 disql，查询语句在其中没有意义，
--    因此这里只留注释：需要核对时手动复制下面这段执行。
--
-- SELECT `index_code`, `impl_status`, `value_type`, `scale`,
--        `numerator_metric`, `denominator_metric`, `dims`
--   FROM `quality_metric_def`
--  WHERE `index_code` IN ('quality_30', 'quality_31', 'quality_302', 'quality_303')
--  ORDER BY `index_code`;
-- ---------------------------------------------------------------------
