-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----
CALL zing_add_column('quality_count_rule', 'target_direction', 'VARCHAR(16) DEFAULT ''UP'' COMMENT ''达标方向：UP=越高越好 / DOWN=越低越好；决定「达标」是 ≥ 还是 ≤ 目标值''');

-- =====================================================================
-- 19 指标达标方向
--
-- 背景：
--   quality_count_rule 已有 target_value / warning_value（本院自管），
--   但缺「方向」这一列，于是配了阈值也判不出达标与否：
--   同一个数值在「ICU镇痛评估率 ≥ 90%」和「VAP发病率 ≤ 5‰」上结论正好相反，
--   而阈值本身看不出属于哪一类。若靠「目标值与预警值谁大谁小」去猜方向，
--   遇到只配一个阈值的指标就无从判断；猜错的后果是把不达标标成达标，
--   比不判定更危险（会让质控科漏掉该整改的指标）。
--
-- 本列取值：
--   UP   越高越好 —— 依从率、完成率、送检率等（默认）
--   DOWN 越低越好 —— 病死率、感染率、并发症率等
--
-- 幂等：先查数据字典再决定是否 ADD COLUMN，重复执行不报错、不重复加列。
--   原因同 18：本脚本挂在 install.sh 的 INCREMENTAL_SQL 里，增量通道对失败只 warn
--   后继续，裸 ALTER TABLE 会让「列已存在」的噪声盖住真正的失败；漏执行的库
--   也无法用同一份脚本补救（缺列时「质控指标」看板直接加载失败：
--   无效的列名[target_direction]）。
-- =====================================================================

-- 存量行补默认值（默认 UP 覆盖多数指标；越低越好的少数指标由质控科在页面上改一次）
UPDATE `quality_count_rule`
SET `target_direction` = 'UP'
WHERE `target_direction` IS NULL;
