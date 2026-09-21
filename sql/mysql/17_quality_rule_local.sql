-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 质控：指标规则支持本院自建（增量升级，幂等，可重复执行）
--
-- 背景：quality_count_rule 此前只能从 ICU 侧 zing_icu_db_prod.quality_count_rule
--       同步而来 —— 本院想新增一条「分子 ÷ 分母」的指标，必须等重症侧先建好。
--       本脚本给规则表加「来源」维度，使之可以直接在本院配置页新增规则，
--       同时保证 ICU 同步不会把本院自建、或本院改过口径的规则冲掉。
--
-- 新增两列：
--   origin          ICU   = 由 ICU 同步而来（可被源端刷新）
--                   LOCAL = 本院自建（同步完全不动它）
--   local_override  1     = ICU 来源，但本院改过口径（分子 / 分母 / 放大系数），
--                           同步时不再覆盖这几个字段（名称、排序等仍跟随源端）
--
-- 规则编号空间隔离（关键，不要改）：
--   同步判重用 src.`id` NOT IN (SELECT rule_id ...)，而 ICU 侧 id 是纯数字。
--   本院自建规则统一用 'LOCAL_' 前缀，天然不会与 ICU 的 id 相撞 ——
--   否则本地 rule_id 一旦与 ICU 未来新增的 id 相同，那条 ICU 规则会永远
--   同步不进来，而且不报任何错，只能靠人去数两边条数才发现。
--
-- 老库升级：install.sh 的增量列表已包含本文件，会自动套用；
--           手动执行时重复跑也不会报错（DbInit 会先查列是否存在）。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/17_quality_rule_local.sql
-- =====================================================================

CALL zing_add_column('quality_count_rule', 'origin', 'VARCHAR(16) DEFAULT ''ICU'' COMMENT ''来源：ICU 由重症侧同步（可被源端刷新）/ LOCAL 本院自建（同步不覆盖）''');
CALL zing_add_column('quality_count_rule', 'local_override', 'TINYINT DEFAULT 0 COMMENT ''1 = ICU 来源但本院改过口径，同步时不覆盖分子/分母/放大系数''');

-- 存量行全部来自 ICU 同步，显式回填（列默认值对已存在的行不保证生效）
UPDATE `quality_count_rule` SET `origin` = 'ICU' WHERE `origin` IS NULL;
UPDATE `quality_count_rule` SET `local_override` = 0 WHERE `local_override` IS NULL;
