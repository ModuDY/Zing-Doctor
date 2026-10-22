-- =====================================================================
-- 64_fix_param_mojibake.sql（MySQL / MariaDB 专用，达梦侧不适用）
--
-- 解决现象：参数设置页的分组名、参数名、说明整片乱码，形如
--   「æ, £ê€...ã+å½œè€œåº」
--
-- 成因：install-mariadb-debian.sh 是「一个 SQL 文件一条连接」逐个导入，
--   而 29~49 等约三十个种子脚本开头没有 SET NAMES utf8mb4。mysql 客户端的
--   默认连接字符集跟随 locale，在 LANG=C（sudo / 未配置 locale 的服务器）下
--   就是 latin1 —— SQL 文件里的 UTF-8 中文被当成 latin1 逐字节解释，再转成
--   utf8mb4 存进列里，一个汉字变成 3 个「Ã¦」这样的怪字符，属于双重编码。
--   这是导入期的问题，不是页面渲染或 JDBC 的问题：应用写入通道是 UTF-8，
--   页面里新填的中文不会乱。
--
-- 修复原理：可判定的乱码行满足两点 ——
--   ① 用 latin1 取回原始字节、再用 utf8mb4 解码后，不含 '?'（说明原串每个
--      字符都落在 U+0080~U+00FF 区间，正是被 latin1 解释过的 UTF-8 字节）；
--   ② 解码后字符数变少（多个怪字符合成一个汉字）。
--   正常的 UTF-8 中文经同样的转换会得到一串 '?'（汉字不在 latin1 里），
--   判据 ① 直接排除；纯 ASCII 串字符数不变，判据 ② 排除。故不会误伤。
--
-- 幂等：修过的行不再满足判据，可重复执行。
-- 已知漏项：乱码原文里本来含半角 '?' 的行会被判据 ① 跳过（宁漏不误伤），
--   核对时若发现，按本文件同样的表达式手工修即可。
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
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`group_name` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`group_name` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`group_name`);

UPDATE `sys_param_group`
   SET `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
 WHERE `remark` IS NOT NULL
   AND `remark` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`remark`);

-- ---------------------------------------------------------------------
-- 2) 参数：名称、值、说明、选项、默认值
--    param_value 里混着令牌/地址这类纯 ASCII 值，判据② 天然跳过，不影响。
-- ---------------------------------------------------------------------
UPDATE `sys_param`
   SET `param_name` = CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4)
 WHERE `param_name` IS NOT NULL
   AND `param_name` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`param_name` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`param_name`);

UPDATE `sys_param`
   SET `param_value` = CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4)
 WHERE `param_value` IS NOT NULL
   AND `param_value` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`param_value` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`param_value`);

UPDATE `sys_param`
   SET `remark` = CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)
 WHERE `remark` IS NOT NULL
   AND `remark` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`remark` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`remark`);

UPDATE `sys_param`
   SET `options` = CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4)
 WHERE `options` IS NOT NULL
   AND `options` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`options` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`options`);

UPDATE `sys_param`
   SET `default_value` = CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4)
 WHERE `default_value` IS NOT NULL
   AND `default_value` <> ''
   AND LOCATE('?', CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4)) = 0
   AND CHAR_LENGTH(CONVERT(BINARY(CONVERT(`default_value` USING latin1)) USING utf8mb4))
       < CHAR_LENGTH(`default_value`);

-- ---------------------------------------------------------------------
-- 3) 核对：下面应全是正常中文；若仍见到「æ...」说明该行含半角 '?' 被跳过
-- ---------------------------------------------------------------------
SELECT `group_code`, `group_name` FROM `sys_param_group` ORDER BY `sort_no`;
SELECT `param_key`, `param_name` FROM `sys_param`
 WHERE `param_group` IN ('system', 'external', 'msgpush', 'workbench')
 ORDER BY `param_group`, `sort_no`;
