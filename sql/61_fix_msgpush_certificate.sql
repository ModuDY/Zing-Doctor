-- =====================================================================
-- 61_fix_msgpush_certificate.sql
-- 「授权码 certificate」是平台按厂家分配的授权码，不是 hipToken
-- MySQL 镜像见 sql/mysql/61_fix_msgpush_certificate.sql
--
-- 背景（现场实测，2026-10-16）：
--   网关返回 {"code":"50002","data":"","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}
--   58/59 把 certificate 与 hipToken 当成同一类凭证，先后种成了他厂家真实报文里的两串 base64：
--     uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==   （58 初版种子值）
--     8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=                 （59 把它改成了 hipToken 的值）
--   说明书对这一项的要求是 32 位 UUID、不含横杠，两串样例值都不满足。
--
-- 本脚本三件事：
--   1) 清空仍是样例 base64 的 certificate —— 留着只会让人以为配好了；
--      只动「空值 / 样例值」的行，现场已填真值的不被覆盖。
--   2) 参数名改成「授权码 certificate」，说明写清来源与平台报错原文（同样只动空值/样例值的行）。
--   3) 清掉 regex，不做任何格式限制 —— 允许现场按平台实际给的值填，填成与 hipToken 一样的值也放行。
--      这一条对所有行生效：参数页不该替平台下结论。
--   required 保持 0。
--
-- 幂等：可重复执行。
-- =====================================================================

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_name" = '授权码 certificate',
       "remark" = '平台按厂家分配的授权码，形式以平台分配的为准（说明书示例为 32 位 UUID、不含横杠，'
               || '向医院集成平台索取本院的值）。本项不做格式校验，与 hipToken 填同值也允许。'
               || '填错时网关返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}。'
 WHERE "param_key" = 'MSGPUSH_CERTIFICATE'
   AND ("param_value" IS NULL OR TRIM("param_value") = ''
        OR "param_value" IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                             '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY='));

-- 不做格式限制：参数页与后端都不再校验 certificate 的格式（含与 hipToken 同值的情况）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "regex" = NULL
 WHERE "param_key" = 'MSGPUSH_CERTIFICATE';

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = ''
 WHERE "param_key" = 'MSGPUSH_CERTIFICATE'
   AND "param_value" IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                         '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=');
