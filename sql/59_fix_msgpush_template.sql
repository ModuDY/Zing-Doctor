-- =====================================================================
-- 59_fix_msgpush_template.sql
-- 修正 58 号脚本写入的报文模板默认值（达梦 DM8）
-- MySQL 镜像见 sql/mysql/59_fix_msgpush_template.sql
--
-- 为什么需要这个修正：
--   58 号给 MSGPUSH_HEADER_XML / MSGPUSH_BODY_XML 的默认值里带了
--   `<?xml version="1.0" ...?>` 声明和一层 <root>，但这两个参数是**片段**，
--   会被原样嵌进信封模板。于是渲染出来一份报文里有三份 XML 声明：
--       <?xml ...?> <root> <?xml ...?> <root>..</root> <?xml ...?> <root>..</root> </root>
--   一个 XML 文档只能有一个声明且必须在最开头 —— 这份报文网关必然解析失败。
--
--   改为：片段各自用 <msgHeader> / <msgBody> 作为根元素（不再带声明），
--   XML 声明只保留在信封模板 MSGPUSH_ENVELOPE 里。
--
-- 幂等且不覆盖现场改动：
--   仅当参数值仍是 58 号写入的旧默认（含 `<?xml version=`）时才更新；
--   现场若已按 WSDL 改过模板，本脚本不会动它。
--
-- 幂等：可重复执行。
-- =====================================================================

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<msgHeader>' || CHR(10)
               || '<serverName>sendMessagesToCenterPlatform</serverName>' || CHR(10)
               || '<format>xml</format>' || CHR(10)
               || '<callOperator>#callOperator#</callOperator>' || CHR(10)
               || '<certificate>#certificate#</certificate>' || CHR(10)
               || '<applyDistrictCode>#districtCode#</applyDistrictCode>' || CHR(10)
               || '<execDistrictCode>#districtCode#</execDistrictCode>' || CHR(10)
               || '</msgHeader>',
       "remark" = 'msgHeader 片段（勿写 XML 声明，声明只在信封里）。占位符：#callOperator# #certificate# #districtCode#。按现场 WSDL 调整。'
 WHERE "param_key" = 'MSGPUSH_HEADER_XML'
   AND "param_value" LIKE '<?xml version=%';

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<msgBody>' || CHR(10)
               || '<hipMessageNo>#hipMessageNo#</hipMessageNo>' || CHR(10)
               || '<hipAction>#hipAction#</hipAction>' || CHR(10)
               || '<hipToken>#hipToken#</hipToken>' || CHR(10)
               || '<mobile>#mobile#</mobile>' || CHR(10)
               || '<startTime>#startTime#</startTime>' || CHR(10)
               || '<content>#content#</content>' || CHR(10)
               || '</msgBody>',
       "remark" = 'msgBody 片段（勿写 XML 声明）。占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
 WHERE "param_key" = 'MSGPUSH_BODY_XML'
   AND "param_value" LIKE '<?xml version=%';

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）：HEADER/BODY 应为「片段无声明」，ENVELOPE 为「含声明」
--   SELECT "param_key", CASE WHEN "param_value" LIKE '<?xml version=%'
--                            THEN '含声明' ELSE '片段无声明' END
--     FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_group" = 'msgpush'
--      AND "param_key" IN ('MSGPUSH_HEADER_XML','MSGPUSH_BODY_XML','MSGPUSH_ENVELOPE');
-- ---------------------------------------------------------------------