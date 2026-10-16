-- =====================================================================
-- 59_fix_msgpush_template.sql
-- 修正消息推送的报文模板（达梦 DM8）
-- MySQL 镜像见 sql/mysql/59_fix_msgpush_template.sql
--
-- 为什么需要这个修正：
--   接口文档里 msgHeader 与 msgBody 各自是**一份独立的 XML 文档**（各带自己的
--   XML 声明与 <root>），它们是 WebService 方法 sendMessagesToCenterPlatform 的
--   两个入参，需要包在 SOAP Envelope 里传递：
--       <soapenv:Body><sendMessagesToCenterPlatform>
--           <msgHeader><![CDATA[<?xml ...?><root>...</root>]]></msgHeader>
--           <msgBody><![CDATA[<?xml ...?><root>...</root>]]></msgBody>
--       </sendMessagesToCenterPlatform></soapenv:Body>
--   58 号最初把三段拼进同一个 <root>，现场实测网关返回
--   「error:缺少msgHeader参数!」—— 网关找的是 SOAP 参数节点，不是 <root> 的子节点。
--
-- 本脚本把模板改成文档要求的形态，并对历史状态都幂等：
--   1. MSGPUSH_HEADER_XML 若被改成过 msgHeader 片段式 → 恢复为独立 XML 文档
--   2. MSGPUSH_BODY_XML   若被改成过 msgBody   片段式 → 恢复为独立 XML 文档
--   3. MSGPUSH_ENVELOPE   只要不含 soapenv（即还是拼接式 root）→ 换成 SOAP 信封
--   4. MSGPUSH_SOAP_ACTION 为空 → 补上方法名（SOAP 1.1 规范要求值带双引号）
-- 已是正确形态的（如全新安装执行过新版 58）一律不改动。
--
-- 幂等：可重复执行。
-- =====================================================================

-- 1) msgHeader 恢复为独立 XML 文档（条件：当前值是 msgHeader 片段式）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="utf-8"?>' || CHR(10)
               || '<root>' || CHR(10)
               || '<serverName>sendMessagesToCenterPlatform</serverName>' || CHR(10)
               || '<format>xml</format>' || CHR(10)
               || '<callOperator>#callOperator#</callOperator>' || CHR(10)
               || '<certificate>#certificate#</certificate>' || CHR(10)
               || '<applyDistrictCode>#districtCode#</applyDistrictCode>' || CHR(10)
               || '<execDistrictCode>#districtCode#</execDistrictCode>' || CHR(10)
               || '</root>',
       "remark" = 'msgHeader 是独立 XML 文档（含自己的声明），由 SOAP 信封以 CDATA 嵌入。占位符：#callOperator# #certificate# #districtCode#。'
 WHERE "param_key" = 'MSGPUSH_HEADER_XML'
   AND "param_value" LIKE '%<msgHeader>%';

-- 2) msgBody 恢复为独立 XML 文档（条件：当前值是 msgBody 片段式）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="UTF-8"?>' || CHR(10)
               || '<root>' || CHR(10)
               || '<hipMessageNo>#hipMessageNo#</hipMessageNo>' || CHR(10)
               || '<hipAction>#hipAction#</hipAction>' || CHR(10)
               || '<hipToken>#hipToken#</hipToken>' || CHR(10)
               || '<mobile>#mobile#</mobile>' || CHR(10)
               || '<startTime>#startTime#</startTime>' || CHR(10)
               || '<content>#content#</content>' || CHR(10)
               || '</root>',
       "remark" = 'msgBody 是独立 XML 文档（含自己的声明），由 SOAP 信封以 CDATA 嵌入。占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
 WHERE "param_key" = 'MSGPUSH_BODY_XML'
   AND "param_value" LIKE '%<msgBody>%';

-- 3) 信封换成标准 SOAP 1.1 wrapped（条件：不含 soapenv，即还是拼接式 root）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="utf-8"?>' || CHR(10)
               || '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">' || CHR(10)
               || '<soapenv:Body>' || CHR(10)
               || '<sendMessagesToCenterPlatform>' || CHR(10)
               || '<msgHeader><![CDATA[#msgHeader#]]></msgHeader>' || CHR(10)
               || '<msgBody><![CDATA[#msgBody#]]></msgBody>' || CHR(10)
               || '</sendMessagesToCenterPlatform>' || CHR(10)
               || '</soapenv:Body>' || CHR(10)
               || '</soapenv:Envelope>',
       "remark" = 'SOAP 信封：把上面两段用 CDATA 作为 msgHeader / msgBody 两个入参传给方法 sendMessagesToCenterPlatform。现场 WSDL 若与此不同，只改本项即可。'
 WHERE "param_key" = 'MSGPUSH_ENVELOPE'
   AND "param_value" NOT LIKE '%soapenv%';

-- 4) SOAPAction 补方法名（SOAP 1.1 规范要求值带双引号）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = '"sendMessagesToCenterPlatform"',
       "remark" = 'SOAP 1.1 规范要求该值带双引号，故默认值含引号。留空则不发送此请求头；若现场网关不接受引号，去掉即可。'
 WHERE "param_key" = 'MSGPUSH_SOAP_ACTION'
   AND ("param_value" IS NULL OR TRIM("param_value") = '');

-- 5) 返回成功标识的说明：网关实测返回 JSON 而非文档所写的 XML，
--    且业务失败同样返回 HTTP 200 —— 留空按 2xx 判定会误报成功。
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "remark" = '网关实测返回 JSON（如 {"code":"50002","message":"参数校验不通过"}），并非文档所写的 XML。留空按 HTTP 2xx 判定，但业务失败同样返回 200 —— 请在拿到成功返回后，把成功标志（如 "code":"0"）填到这里，判定才准确。'
 WHERE "param_key" = 'MSGPUSH_SUCCESS_KEY'
   AND ("remark" IS NULL OR "remark" LIKE '%XML%');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检：应看到 soapenv:Envelope，且两个片段各自以 XML 声明开头、以 root 为根
--   SELECT "param_key", SUBSTR("param_value", 1, 60) FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_group" = 'msgpush'
--      AND "param_key" IN ('MSGPUSH_HEADER_XML','MSGPUSH_BODY_XML','MSGPUSH_ENVELOPE','MSGPUSH_SOAP_ACTION');
-- ---------------------------------------------------------------------