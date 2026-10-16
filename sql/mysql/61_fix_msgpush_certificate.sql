-- =====================================================================
-- 61_fix_msgpush_certificate.sql（MySQL 镜像）
-- 达梦版见 sql/61_fix_msgpush_certificate.sql（背景与原因写在那份里）
--
-- 现场实测网关返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}：
-- 58/59 把 certificate（平台分配的授权码）当成了 hipToken 一类的令牌，种成了 base64 样例值。
-- 本脚本清掉仍是样例值的 certificate、改参数名与说明、加 32 位十六进制 regex；幂等可重复执行。
-- =====================================================================

UPDATE `sys_param`
   SET `param_name` = '授权码 certificate',
       `remark` = '平台按厂家分配的授权码：32 位 UUID、不含横杠（如 0f1e2d3c4b5a69788796a5b4c3d2e1f0，向医院集成平台索取本院的值）。填错时网关直接返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}。注意它与 hipToken 不是一回事 —— hipToken 是 msgBody 里的令牌，两者不能填同一串。',
       `regex` = '^[0-9a-fA-F]{32}$'
 WHERE `param_key` = 'MSGPUSH_CERTIFICATE'
   AND (`param_value` IS NULL OR TRIM(`param_value`) = ''
        OR `param_value` IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                             '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY='));

UPDATE `sys_param`
   SET `param_value` = ''
 WHERE `param_key` = 'MSGPUSH_CERTIFICATE'
   AND `param_value` IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                         '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=');
