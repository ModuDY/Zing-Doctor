-- =====================================================================
-- 59_fix_msgpush_template.sql
-- 按平台提供的他厂家真实成功报文，升级消息推送的报文模板与配套参数（达梦 DM8）
-- MySQL 镜像见 sql/mysql/59_fix_msgpush_template.sql
--
-- 为什么要升级：现场按 58 号初版模板实测，网关返回
--   「error:缺少msgHeader参数!」→ 改成 SOAP 1.1 wrapped 后变为
--   「{"code":"50002","message":"参数校验不通过"}」，说明结构对了但字段不全。
--   平台给出的真实报文与初版假设有 6 处实质差异：
--     1. 是 SOAP **1.2**（2003/05 命名空间），不是 schemas.xmlsoap.org；
--     2. 要额外声明命名空间 xmlns:zys="http://www.zysoft.com.cn/"；
--     3. 操作元素是 <zys:CallInterface>，**不是** serverName 同名元素；
--     4. 入参是带命名空间的 <zys:msgHeader> / <zys:msgBody>；
--     5. msgHeader 实为 **21 个字段**（stepNo / visitorIp / msgUuid / convertId /
--        senderCode / receivedCode / targetType / convertType 等），初版只有 7 个；
--     6. msgBody 还有 <patientName>；
--     并且 SOAP 1.2 不用 SOAPAction 请求头，Content-Type 必须是 application/soap+xml。
--
-- 幂等：每条都带「只在还是旧形态时才改」的条件，新装环境（执行过新版 58）全部跳过。
-- =====================================================================

-- 1) msgHeader 补全为 21 个字段（条件：不含 msgUuid 即为旧模板）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="UTF-8"?><root>' || CHR(10)
               || '<stepNo>#stepNo#</stepNo>' || CHR(10)
               || '<visitorIp>#visitorIp#</visitorIp>' || CHR(10)
               || '<receiverServer>#receiverServer#</receiverServer>' || CHR(10)
               || '<msgUuid>#msgUuid#</msgUuid>' || CHR(10)
               || '<receivedOrgCode>#receivedOrgCode#</receivedOrgCode>' || CHR(10)
               || '<format>#format#</format>' || CHR(10)
               || '<certificate>#certificate#</certificate>' || CHR(10)
               || '<serverName>#serverName#</serverName>' || CHR(10)
               || '<convertId>#convertId#</convertId>' || CHR(10)
               || '<convertType>#convertType#</convertType>' || CHR(10)
               || '<targetType>#targetType#</targetType>' || CHR(10)
               || '<senderCode>#senderCode#</senderCode>' || CHR(10)
               || '<receiverUrl>#receiverUrl#</receiverUrl>' || CHR(10)
               || '<senderOrgCode>#senderOrgCode#</senderOrgCode>' || CHR(10)
               || '<applyDistrictCode>#districtCode#</applyDistrictCode>' || CHR(10)
               || '<reqUuid>#reqUuid#</reqUuid>' || CHR(10)
               || '<msgUuidOrion>#msgUuidOrion#</msgUuidOrion>' || CHR(10)
               || '<execDistrictCode>#districtCode#</execDistrictCode>' || CHR(10)
               || '<callOperator>#callOperator#</callOperator>' || CHR(10)
               || '<serviceId>#serviceId#</serviceId>' || CHR(10)
               || '<receivedCode>#receivedCode#</receivedCode>' || CHR(10)
               || '</root>',
       "remark" = '独立 XML 文档，由信封以 CDATA 嵌入。系统自动生成 #stepNo# #msgUuid# #convertId#'
               || ' #reqUuid# #msgUuidOrion# #visitorIp# #serverName#；其余走参数。'
 WHERE "param_key" = 'MSGPUSH_HEADER_XML'
   AND "param_value" NOT LIKE '%msgUuid%';

-- 2) msgBody 补 patientName（条件：不含它即为旧模板）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="UTF-8"?><root>' || CHR(10)
               || '<content>#content#</content>' || CHR(10)
               || '<hipAction>#hipAction#</hipAction>' || CHR(10)
               || '<hipMessageNo>#hipMessageNo#</hipMessageNo>' || CHR(10)
               || '<hipToken>#hipToken#</hipToken>' || CHR(10)
               || '<mobile>#mobile#</mobile>' || CHR(10)
               || '<patientName>#patientName#</patientName>' || CHR(10)
               || '<startTime>#startTime#</startTime>' || CHR(10)
               || '</root>'
 WHERE "param_key" = 'MSGPUSH_BODY_XML'
   AND "param_value" NOT LIKE '%patientName%';

-- 3) 信封换成 SOAP 1.2 + zys + CallInterface（条件：不含 CallInterface 即为旧信封）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" =
               '<?xml version="1.0" encoding="UTF-8"?>' || CHR(10)
               || '<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:zys="http://www.zysoft.com.cn/">' || CHR(10)
               || '  <soap:Header/>' || CHR(10)
               || '  <soap:Body>' || CHR(10)
               || '    <zys:CallInterface>' || CHR(10)
               || '      <zys:msgHeader><![CDATA[#msgHeader#]]></zys:msgHeader>' || CHR(10)
               || '      <zys:msgBody><![CDATA[#msgBody#]]></zys:msgBody>' || CHR(10)
               || '    </zys:CallInterface>' || CHR(10)
               || '  </soap:Body>' || CHR(10)
               || '</soap:Envelope>',
       "remark" = '照平台真实报文对齐：SOAP 1.2 + zys 命名空间 + CallInterface + CDATA 承载两份独立 XML。'
               || '现场 WSDL 若不同，只改本项即可。'
 WHERE "param_key" = 'MSGPUSH_ENVELOPE'
   AND "param_value" NOT LIKE '%CallInterface%';

-- 4) Content-Type 必须是 application/soap+xml（SOAP 1.2）
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = 'application/soap+xml; charset=UTF-8',
       "default_value" = 'application/soap+xml; charset=UTF-8',
       "remark" = '真实报文是 SOAP 1.2，必须用 application/soap+xml（用 text/xml 会被网关拒）。'
 WHERE "param_key" = 'MSGPUSH_CONTENT_TYPE'
   AND "param_value" NOT LIKE '%application/soap+xml%';

-- 5) SOAPAction 清空：SOAP 1.2 不用该请求头
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = '',
       "default_value" = '',
       "remark" = 'SOAP 1.2 已不用该请求头，留空即不发送。若现场网关实际是 SOAP 1.1 才需填（值要带双引号）。'
 WHERE "param_key" = 'MSGPUSH_SOAP_ACTION'
   AND "param_value" IS NOT NULL
   AND TRIM("param_value") <> '';

-- 6) hipToken 换成真实报文里的值；callOperator 换成 PEIS
--    （条件：仍是文档初版样例值才改，现场已填过真值的不动）
--    ⚠️ 只动 hipToken：certificate 是平台分配的「授权码」（32 位 UUID，见 61 号脚本），
--       当初把它一起改成 hipToken 同值是错的 —— 网关会直接回
--       「无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)」。
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=',
       "remark" = '平台按厂家分发。默认值取自平台提供的真实报文，若非本院可用凭证请替换。'
 WHERE "param_key" = 'MSGPUSH_HIP_TOKEN'
   AND "param_value" LIKE 'uHto9nM6CWyLHAno%';

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = 'PEIS',
       "remark" = '平台按厂家分配。示例（体检）为 PEIS，本系统须向医院索取对应值。'
 WHERE "param_key" = 'MSGPUSH_CALL_OPERATOR'
   AND "param_value" = 'DHZZ';

-- 7) 返回成功标识的说明：网关返回 JSON 且业务失败也是 200
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "remark" = '网关返回 JSON（如 {"code":"50002","message":"参数校验不通过"}）而非 XML，'
               || '且业务失败也返回 HTTP 200。留空按 2xx 判定会误报成功 —— '
               || '拿到成功返回后把成功标志（如 "code":"0"）填这里。'
 WHERE "param_key" = 'MSGPUSH_SUCCESS_KEY'
   AND ("remark" IS NULL OR "remark" NOT LIKE '%JSON%');

-- ---------------------------------------------------------------------
-- 8) 新增 11 个参数：平台报文里按厂家分配的字段
--    （全新安装由 58 号写入；这里补「已执行过 58 号初版」的环境）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_SENDER_CODE', '发送方代码 senderCode', '', 'msgpush', 20, 1, 'text',
       NULL, '', 0, NULL,
       '平台按厂家分配。真实示例（学生体检）为 PE，本系统须向医院索取，留空会被判校验不通过。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_SENDER_CODE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RECEIVED_CODE', '接收方代码 receivedCode', 'MP', 'msgpush', 21, 1, 'text',
       NULL, 'MP', 0, NULL, '真实示例为 MP（消息平台）。若医院另有约定按现场改。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RECEIVED_CODE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_TARGET_TYPE', '目标类型 targetType', '1', 'msgpush', 22, 1, 'text',
       NULL, '1', 0, NULL, '真实示例为 1。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_TARGET_TYPE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONVERT_TYPE', '转换类型 convertType', '0', 'msgpush', 23, 1, 'text',
       NULL, '0', 0, NULL, '真实示例为 0。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONVERT_TYPE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_FORMAT', '数据格式 format', 'HL7V3', 'msgpush', 24, 1, 'text',
       NULL, 'HL7V3', 0, NULL, '真实示例为 HL7V3（不是 xml），填错会被判校验不通过。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_FORMAT');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_SERVICE_ID', '服务标识 serviceId', 'sendMessagesToCenterPlatform', 'msgpush', 25, 1, 'text',
       NULL, 'sendMessagesToCenterPlatform', 0, NULL, '真实示例与 serverName 同值。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_SERVICE_ID');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_VISITOR_IP', '调用方 IP visitorIp', '', 'msgpush', 26, 1, 'text',
       NULL, '', 0, NULL,
       '平台按此记录来访来源。留空则由系统自动取本机 IP，一般无需填。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_VISITOR_IP');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RECEIVER_SERVER', '接收服务 receiverServer', 'null', 'msgpush', 27, 1, 'text',
       NULL, 'null', 0, NULL, '真实示例为字符串 null（不是空节点），照抄即可。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RECEIVER_SERVER');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RECEIVER_URL', '接收地址 receiverUrl', 'null', 'msgpush', 28, 1, 'text',
       NULL, 'null', 0, NULL, '真实示例为字符串 null（不是空节点），照抄即可。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RECEIVER_URL');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_SENDER_ORG_CODE', '发送方机构码 senderOrgCode', '', 'msgpush', 29, 1, 'text',
       NULL, '', 0, NULL, '真实示例为空，可留空。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_SENDER_ORG_CODE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RECEIVED_ORG_CODE', '接收方机构码 receivedOrgCode', '', 'msgpush', 30, 1, 'text',
       NULL, '', 0, NULL, '真实示例为空，可留空。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RECEIVED_ORG_CODE');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检：msgpush 分组应有 30 行
--   SELECT COUNT(*) FROM "zing_doctor_db_prod"."sys_param" WHERE "param_group" = 'msgpush';
-- ---------------------------------------------------------------------