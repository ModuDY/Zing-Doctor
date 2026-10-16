-- 59_fix_msgpush_template.sql（MySQL 镜像）
-- 修正消息推送的报文模板：msgHeader / msgBody 为独立 XML 文档，外层用 SOAP 信封
-- 对应达梦版：sql/59_fix_msgpush_template.sql（真源，改动请先改那边）
--
-- 背景：接口文档里 msgHeader 与 msgBody 各自是一份独立的 XML 文档，是 WebService
--   方法 sendMessagesToCenterPlatform 的两个入参，需要包在 SOAP Envelope 里传递。
--   58 号最初把三段拼进同一个 <root>，现场实测网关返回「error:缺少msgHeader参数!」
--   —— 网关找的是 SOAP 参数节点，不是 <root> 的子节点。
--
-- 幂等：已是正确形态的（如全新安装执行过新版 58）一律不改动，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

-- 1) msgHeader 恢复为独立 XML 文档（条件：当前值是 msgHeader 片段式）
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="utf-8"?>', '\n',
              '<root>', '\n',
              '<serverName>sendMessagesToCenterPlatform</serverName>', '\n',
              '<format>xml</format>', '\n',
              '<callOperator>#callOperator#</callOperator>', '\n',
              '<certificate>#certificate#</certificate>', '\n',
              '<applyDistrictCode>#districtCode#</applyDistrictCode>', '\n',
              '<execDistrictCode>#districtCode#</execDistrictCode>', '\n',
              '</root>'),
       `remark` = 'msgHeader 是独立 XML 文档（含自己的声明），由 SOAP 信封以 CDATA 嵌入。占位符：#callOperator# #certificate# #districtCode#。'
 WHERE `param_key` = 'MSGPUSH_HEADER_XML'
   AND `param_value` LIKE '%<msgHeader>%';

-- 2) msgBody 恢复为独立 XML 文档（条件：当前值是 msgBody 片段式）
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="UTF-8"?>', '\n',
              '<root>', '\n',
              '<hipMessageNo>#hipMessageNo#</hipMessageNo>', '\n',
              '<hipAction>#hipAction#</hipAction>', '\n',
              '<hipToken>#hipToken#</hipToken>', '\n',
              '<mobile>#mobile#</mobile>', '\n',
              '<startTime>#startTime#</startTime>', '\n',
              '<content>#content#</content>', '\n',
              '</root>'),
       `remark` = 'msgBody 是独立 XML 文档（含自己的声明），由 SOAP 信封以 CDATA 嵌入。占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
 WHERE `param_key` = 'MSGPUSH_BODY_XML'
   AND `param_value` LIKE '%<msgBody>%';

-- 3) 信封换成标准 SOAP 1.1 wrapped（条件：不含 soapenv）
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="utf-8"?>', '\n',
              '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">', '\n',
              '<soapenv:Body>', '\n',
              '<sendMessagesToCenterPlatform>', '\n',
              '<msgHeader><![CDATA[#msgHeader#]]></msgHeader>', '\n',
              '<msgBody><![CDATA[#msgBody#]]></msgBody>', '\n',
              '</sendMessagesToCenterPlatform>', '\n',
              '</soapenv:Body>', '\n',
              '</soapenv:Envelope>'),
       `remark` = 'SOAP 信封：把上面两段用 CDATA 作为 msgHeader / msgBody 两个入参传给方法 sendMessagesToCenterPlatform。现场 WSDL 若与此不同，只改本项即可。'
 WHERE `param_key` = 'MSGPUSH_ENVELOPE'
   AND `param_value` NOT LIKE '%soapenv%';

-- 4) SOAPAction 补方法名（SOAP 1.1 规范要求值带双引号）
UPDATE `sys_param`
   SET `param_value` = '"sendMessagesToCenterPlatform"',
       `remark` = 'SOAP 1.1 规范要求该值带双引号，故默认值含引号。留空则不发送此请求头；若现场网关不接受引号，去掉即可。'
 WHERE `param_key` = 'MSGPUSH_SOAP_ACTION'
   AND (`param_value` IS NULL OR TRIM(`param_value`) = '');

-- 5) 返回成功标识的说明：网关实测返回 JSON 而非文档所写的 XML，
--    且业务失败同样返回 HTTP 200 —— 留空按 2xx 判定会误报成功。
UPDATE `sys_param`
   SET `remark` = '网关实测返回 JSON（如 {"code":"50002","message":"参数校验不通过"}），并非文档所写的 XML。留空按 HTTP 2xx 判定，但业务失败同样返回 200 —— 请在拿到成功返回后，把成功标志（如 "code":"0"）填到这里，判定才准确。'
 WHERE `param_key` = 'MSGPUSH_SUCCESS_KEY'
   AND (`remark` IS NULL OR `remark` LIKE '%XML%');

COMMIT;

-- 自检：应看到 soapenv:Envelope，两个片段各自以 XML 声明开头
--   SELECT `param_key`, LEFT(`param_value`, 60) FROM `sys_param`
--    WHERE `param_group` = 'msgpush'
--      AND `param_key` IN ('MSGPUSH_HEADER_XML','MSGPUSH_BODY_XML','MSGPUSH_ENVELOPE','MSGPUSH_SOAP_ACTION');