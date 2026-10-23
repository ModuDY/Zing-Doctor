-- =====================================================================
-- 65_param_value_mediumtext.sql（MySQL / MariaDB 专用）
--
-- 达梦侧同一列也是 VARCHAR(1000)（见 sql/12_archive.sql），跑达梦库的现场
-- 需由 DBA 自行放宽（参考写法，按现场 DM 版本核对）：
--   ALTER TABLE "zing_doctor_db_prod"."sys_param" MODIFY "param_value" CLOB;
-- 未做成脚本：DM 的 MODIFY 语法随版本有差异，交给现场 DBA 执行更稳妥。
--
-- 解决现象：参数设置 → 评分配置 → 文书院徽，上传图片保存后系统加载不出来
--   （列表里的缩略图、评分文书抬头都是破图/空白），换浏览器、清缓存、
--   重启服务都无效。
--
-- 原因：sys_param.param_value 建表时是 VARCHAR(1000)（见 12_archive.sql），
--   而图片类参数（DOC_HOSPITAL_LOGO 院徽）存的是 base64 dataURI，
--   一张显示尺寸只有 60px 的院徽转出来也有几十 KB：
--     · 严格模式（MySQL 5.7 / MariaDB 10.2 起默认 STRICT_TRANS_TABLES）
--       保存直接报 1406 Data too long for column 'param_value'；
--     · 非严格模式：静默截断到 1000 字符，dataURI 只存进开头一小段。
--   两种表现本质相同 —— <img src> 拿到的是半截 base64，浏览器解不出来。
--
-- 改动：param_value → MEDIUMTEXT（16MB）。
--   前端会把图片等比缩到 512px 再转 base64，通常 20~80KB；
--   留到 16MB 是给「直接粘贴大图 / 填一段外部 dataURI」留余量。
--
-- 幂等：先查 information_schema，已经是长文本就不再 ALTER，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

-- 未选择库时（例如把合成的 install-all.sql 直接喂给客户端）退回默认库名
SET @db := IFNULL(DATABASE(), 'zing_doctor_db_prod');

SET @need := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db
                 AND TABLE_NAME   = 'sys_param'
                 AND COLUMN_NAME  = 'param_value'
                 AND DATA_TYPE IN ('varchar', 'char'));

SET @ddl := IF(@need > 0,
  'ALTER TABLE `sys_param` MODIFY `param_value` MEDIUMTEXT COMMENT ''参数值（图片类参数存 base64 dataURI，故用 MEDIUMTEXT）''',
  'DO 0');

PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

COMMIT;
