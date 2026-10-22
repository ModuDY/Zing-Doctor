-- =====================================================================
-- 64_fix_param_mojibake.sql（MySQL / MariaDB 专用，达梦侧不适用）
--
-- 解决现象：参数设置页的分组名、参数名、说明整片乱码，形如
--   「æ, £ê€...ã+å½œè€œåº」
--
-- 覆盖范围（2026-10 对现场库全表 456 个文本列扫描得出，共 10 列）：
--   sys_param_group.group_name / remark
--   sys_param.param_name / remark / options
--   config_nutrition_formula.formula_name / match_keywords / spec_note / source_note
--   quality_calc_trace.sql_text
--   新增了中文种子的脚本，若同样缺 SET NAMES，部署后也要并进本文件。
--
-- 成因：install-mariadb-debian.sh 是「一个 SQL 文件一条连接」逐个导入，
--   而 29~49 等约三十个种子脚本开头没有 SET NAMES utf8mb4。mysql 客户端的
--   默认连接字符集跟随 locale，在 LANG=C（sudo / 未配置 locale 的服务器）下
--   就是 latin1 —— SQL 文件里的 UTF-8 中文被当成 latin1 逐字节解释，再转成
--   utf8mb4 存进列里，一个汉字变成 3 个「Ã¦」这样的怪字符，属于双重编码。
--   这是导入期的问题，不是页面渲染或 JDBC 的问题：应用写入通道是 UTF-8，
--   页面里新填的中文不会乱。
--
-- ⚠️ 两个必须知道的坑（第一版脚本就栽在这里）：
--   1) MariaDB 对「字符不在目标字符集里」的 CONVERT(... USING latin1) 是**硬报错**
--      （ERROR 1977 Cannot convert 'utf8mb4' character 0xE69687 to 'latin1'），
--      不像 MySQL 那样替换成 '?'。同一张表里既有正常中文（09 月导入）又有乱码
--      （10 月导入），只要更新语句碰到一行正常中文，整条就中断。
--      所以必须先用纯字节条件把乱码行挑出来，绝不碰正常行。
--   2) MySQL/MariaDB 的 latin1 实际是 **cp1252**，不是 ISO-8859-1：原始字节
--      0x80~0x9F 会被解释成 €‚…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ 这批 U+2000~U+2122
--      的字符，UTF-8 占 3 字节（E2 80 xx）。所以判据不能只看 1~2 字节字符：
--      现场 antibiotic 分组（抗生素）的乱码整串里连一个 C28x 都没有。
--
-- 判据（三条，前两条只在纯字节层面求值，先于 CONVERT 生效）：
--   ① HEX(col) REGEXP 'C2[89AB]|E2[89AB]'
--      乱码里每个汉字的第 2、3 字节落在 0x80~0xBF：
--        · 0xA0~0xBF → U+00A0~U+00BF → UTF-8 C2A0~C2BF（C2[89AB]）
--        · 0x80~0x9F → cp1252 特殊字符 → UTF-8 E280~E2BF（E2[89AB]）
--      正常中文（E4~E9 开头）、ASCII、法语（C3A9）都不含这两段，天然排除。
--   ② HEX(col) NOT REGEXP 'E[4-9][89AB]'
--      正常汉字是 E4~E9 开头、第二字节 80~BF 的三字节串。命中说明该行
--      至少还有一个没乱的汉字（混合行），跳过 —— 宁漏不误伤。
--   ③ CONVERT 后不含 '?' 且字符数变少
--      乱码一个汉字是 3 个字符，还原后是 1 个，必然变短；正常行到不了这里。
--
-- 修复原理：筛选出的行用 latin1 取回原始字节，再以 utf8mb4 解码还原。
-- 幂等：修过的行不再满足判据，可重复执行。
-- 已验证：临时表内「正常中文 / café / 纯 ASCII / 现场两行乱码」四类对照，
--   正常行零改动，乱码行正确还原，重复执行 0 行更新。
-- 已知漏项：乱码原文里本来含半角 '?' 的行、以及与正常中文混排的行会被跳过，
--   核对时若发现，按本文件同样的表达式手工处理。
-- =====================================================================

SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---------------------------------------------------------------------
-- 1) 参数分组：分组名、说明
-- ---------------------------------------------------------------------
UPDATE `sys_param_group`
   SET `group_name` = CONVERT(BINARY(CONVERT(`group_name` USING latin1)) USING utf8mb4)
 WHERE `group_name` IS NOT NULL
   AND `group_name` <> ''
   AND HEX(`group_name`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`group_name`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`group_name` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`group_name` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`group_name`);

UPDATE `sys_param_group`
   SET `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
 WHERE `remark` IS NOT NULL
   AND `remark` <> ''
   AND HEX(`remark`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`remark`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`remark`);

-- ---------------------------------------------------------------------
-- 2) 参数：名称、值、说明、选项、默认值
--    param_value 里混着令牌/地址这类纯 ASCII 值，判据①天然跳过，不影响。
-- ---------------------------------------------------------------------
UPDATE `sys_param`
   SET `param_name` = CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4)
 WHERE `param_name` IS NOT NULL
   AND `param_name` <> ''
   AND HEX(`param_name`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`param_name`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`param_name`);

UPDATE `sys_param`
   SET `param_value` = CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4)
 WHERE `param_value` IS NOT NULL
   AND `param_value` <> ''
   AND HEX(`param_value`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`param_value`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`param_value`);

UPDATE `sys_param`
   SET `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
 WHERE `remark` IS NOT NULL
   AND `remark` <> ''
   AND HEX(`remark`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`remark`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`remark`);

UPDATE `sys_param`
   SET `options` = CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4)
 WHERE `options` IS NOT NULL
   AND `options` <> ''
   AND HEX(`options`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`options`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`options`);

UPDATE `sys_param`
   SET `default_value` = CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4)
 WHERE `default_value` IS NOT NULL
   AND `default_value` <> ''
   AND HEX(`default_value`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`default_value`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`default_value`);

-- ---------------------------------------------------------------------
-- 3) 营养配方配置（46_nutrition_formula.sql 种入，同批缺 SET NAMES）
--    全库扫描实测：9 条配方 × 4 个中文列全部双重编码，页面上整片乱码。
-- ---------------------------------------------------------------------
UPDATE `config_nutrition_formula`
   SET `formula_name` = CONVERT(BINARY(CONVERT(`formula_name` USING latin1)) USING utf8mb4)
 WHERE `formula_name` IS NOT NULL
   AND `formula_name` <> ''
   AND HEX(`formula_name`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`formula_name`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`formula_name` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`formula_name` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`formula_name`);

UPDATE `config_nutrition_formula`
   SET `match_keywords` = CONVERT(BINARY(CONVERT(`match_keywords` USING latin1)) USING utf8mb4)
 WHERE `match_keywords` IS NOT NULL
   AND `match_keywords` <> ''
   AND HEX(`match_keywords`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`match_keywords`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`match_keywords` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`match_keywords` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`match_keywords`);

UPDATE `config_nutrition_formula`
   SET `spec_note` = CONVERT(BINARY(CONVERT(`spec_note` USING latin1)) USING utf8mb4)
 WHERE `spec_note` IS NOT NULL
   AND `spec_note` <> ''
   AND HEX(`spec_note`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`spec_note`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`spec_note` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`spec_note` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`spec_note`);

UPDATE `config_nutrition_formula`
   SET `source_note` = CONVERT(BINARY(CONVERT(`source_note` USING latin1)) USING utf8mb4)
 WHERE `source_note` IS NOT NULL
   AND `source_note` <> ''
   AND HEX(`source_note`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`source_note`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`source_note` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`source_note` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`source_note`);

-- ---------------------------------------------------------------------
-- 4) 质控血缘里保存的 SQL 文本（页面上「看规则」时展示）
--    注意：全库扫描会把这里判成 4 行，实际是误报 —— 扫描只看 HEX，而纯 ASCII
--    的 "N("、"L(" 这类字节组合拼起来正好出现 E28/C28 片段。判据③（转换后
--    字符数必须变少）能挡住它，所以本条 UPDATE 现场实测 0 行。
--    保留这一段只是兜底：真有内联中文的双重编码 SQL 时能自动还原。
-- ---------------------------------------------------------------------
UPDATE `quality_calc_trace`
   SET `sql_text` = CONVERT(BINARY(CONVERT(`sql_text` USING latin1)) USING utf8mb4)
 WHERE `sql_text` IS NOT NULL
   AND `sql_text` <> ''
   AND HEX(`sql_text`) REGEXP 'C2[89AB]|E2[89AB]'
   AND HEX(`sql_text`) NOT REGEXP 'E[4-9][89AB]'
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`sql_text` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`sql_text` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`sql_text`);

-- ---------------------------------------------------------------------
-- 5) 核对：下面应全是正常中文；若仍见到「æ...」见文件头的「已知漏项」
-- ---------------------------------------------------------------------
SELECT `group_code`, `group_name`, HEX(`group_name`) AS hex_name
  FROM `sys_param_group` ORDER BY `sort_no`;
SELECT `param_key`, `param_name`, HEX(`param_name`) AS hex_name
  FROM `sys_param`
 WHERE `param_group` IN ('system', 'external', 'msgpush', 'workbench')
 ORDER BY `param_group`, `sort_no`;
