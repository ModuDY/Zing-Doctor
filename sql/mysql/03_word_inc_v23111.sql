-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- =====================================================================
-- zing-doctor v23.1.11 抗菌药识别词库增量（非抗菌药补词：芒硝/含漱/达克罗宁/微量元素等）
-- 当前环境直接执行，无需重启后端（每次打开页面自动从表刷新）
-- 幂等：已存在词条自动跳过
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/03_word_inc_v23111.sql
-- =====================================================================

INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '达克罗宁', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='达克罗宁');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '布比卡因', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='布比卡因');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '罗哌卡因', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='罗哌卡因');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '普鲁卡因', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='普鲁卡因');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '丁卡因', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='丁卡因');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '苯佐卡因', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='苯佐卡因');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '含漱', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='含漱');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '漱口', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='漱口');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '西吡氯铵', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='西吡氯铵');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '氯己定', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='氯己定');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '聚维酮碘', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='聚维酮碘');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '碘伏', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='碘伏');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '碘甘油', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='碘甘油');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '锡类散', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='锡类散');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '冰硼散', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='冰硼散');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '西瓜霜', '局麻/口腔护理/外用消毒', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='西瓜霜');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '芒硝', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='芒硝');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '冰片', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='冰片');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '金黄散', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='金黄散');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '青黛', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='青黛');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '云南白药', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='云南白药');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '伤科灵', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='伤科灵');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '正骨水', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='正骨水');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '红花油', '中药/外敷', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='红花油');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '微量元素', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='微量元素');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '多种微量元素', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='多种微量元素');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '安达美', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='安达美');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '门冬氨酸钾镁', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='门冬氨酸钾镁');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '葡萄糖酸锌', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='葡萄糖酸锌');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '硫酸锌', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='硫酸锌');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '亚硒酸钠', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='亚硒酸钠');
INSERT INTO `config_abx_word` (`word_type`,`keyword`,`category`,`remark`,`status`)
SELECT 'non_antibiotic', '含D3', '微量元素/电解质营养', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM `config_abx_word` WHERE `word_type`='non_antibiotic' AND `keyword`='含D3');
