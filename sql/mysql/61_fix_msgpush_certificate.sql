-- =====================================================================
-- 61_fix_msgpush_certificate.sql（MySQL 镜像）
-- 达梦版见 sql/61_fix_msgpush_certificate.sql（背景与原因写在那份里）
--
-- 现场实测网关返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}：
-- 58/59 把 certificate（平台分配的授权码）当成了 hipToken 一类的令牌，种成了 base64 样例值。
-- 本脚本清掉仍是样例值的 certificate、改参数名与说明、清掉 regex（不做格式限制）；幂等可重复执行。
-- =====================================================================

UPDATE `sys_param`
   SET `param_name` = '授权码 certificate',
       `remark` = '平台按厂家分配的授权码，形式以平台分配的为准（说明书示例为 32 位 UUID、不含横杠，向医院集成平台索取本院的值）。本项不做格式校验，与 hipToken 填同值也允许。填错时网关返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}。'
 WHERE `param_key` = 'MSGPUSH_CERTIFICATE'
   AND (`param_value` IS NULL OR TRIM(`param_value`) = ''
        OR `param_value` IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                             '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY='));

UPDATE `sys_param`
   SET `regex` = NULL
 WHERE `param_key` = 'MSGPUSH_CERTIFICATE';

UPDATE `sys_param`
   SET `param_value` = ''
 WHERE `param_key` = 'MSGPUSH_CERTIFICATE'
   AND `param_value` IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                         '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=');
