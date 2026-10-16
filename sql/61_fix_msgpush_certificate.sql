-- =====================================================================
-- 61_fix_msgpush_certificate.sql
-- 「平台凭证 certificate」不是令牌，是平台分配的授权码（32 位 UUID）
-- MySQL 镜像见 sql/mysql/61_fix_msgpush_certificate.sql
--
-- 背景（现场实测，2026-10-16）：
--   网关返回 {"code":"50002","data":"","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}
--   58/59 把 certificate 与 hipToken 当成同一类凭证，先后种成了他厂家真实报文里的两串 base64：
--     uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==   （58 初版种子值）
--     8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=                 （59 把它改成了 hipToken 的值）
--   两串都不可能是 32 位 UUID，所以怎么改都过不了平台这一步，与 hipToken 无关：
--   hipToken 在 msgBody（平台按厂家分发的令牌），certificate 在 msgHeader（平台分配的授权码）。
--
-- 本脚本三件事，只动「还是样例值 / 空值」的行使现场已填真值的不被覆盖：
--   1) 清空仍是样例 base64 的 certificate —— 留着只会让人以为配好了；
--   2) 参数名改成「授权码 certificate」，说明写清格式、来源与报错原文；
--   3) 加 32 位十六进制 regex，填错保存时当场被参数页拦下。
--   required 保持 0：只做演练/不校验授权码的现场不该被参数页卡住；真发时后端另有校验。
--
-- 幂等：可重复执行。
-- =====================================================================

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_name" = '授权码 certificate',
       "remark" = '平台按厂家分配的授权码：32 位 UUID、不含横杠'
               || '（如 0f1e2d3c4b5a69788796a5b4c3d2e1f0，向医院集成平台索取本院的值）。'
               || '填错时网关直接返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}。'
               || '注意它与 hipToken 不是一回事 —— hipToken 是 msgBody 里的令牌，两者不能填同一串。',
       "regex" = '^[0-9a-fA-F]{32}$'
 WHERE "param_key" = 'MSGPUSH_CERTIFICATE'
   AND ("param_value" IS NULL OR TRIM("param_value") = ''
        OR "param_value" IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                             '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY='));

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = ''
 WHERE "param_key" = 'MSGPUSH_CERTIFICATE'
   AND "param_value" IN ('uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
                         '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=');
