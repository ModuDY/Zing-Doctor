-- 59_fix_msgpush_template.sql（MySQL / MariaDB 镜像）
-- 按平台提供的他厂家真实成功报文，升级消息推送的报文模板与配套参数
-- 对应达梦版：sql/59_fix_msgpush_template.sql（真源，改动请先改那边）
--
-- 平台真实报文与初版假设的 6 处实质差异：
--   SOAP 1.2（2003/05 命名空间）+ xmlns:zys="http://www.zysoft.com.cn/"；
--   操作元素 <zys:CallInterface>；入参 <zys:msgHeader> / <zys:msgBody>；
--   msgHeader 共 21 个字段（初版只有 7 个）；msgBody 含 <patientName>；
--   SOAP 1.2 不用 SOAPAction，Content-Type 必须 application/soap+xml。
--
-- 幂等：每条都带「只在还是旧形态时才改」的条件，新装环境全部跳过。
-- =====================================================================
SET NAMES utf8mb4;

-- 1) msgHeader 补全为 21 个字段
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="UTF-8"?><root>', '\n',
              '<stepNo>#stepNo#</stepNo>', '\n',
              '<visitorIp>#visitorIp#</visitorIp>', '\n',
              '<receiverServer>#receiverServer#</receiverServer>', '\n',
              '<msgUuid>#msgUuid#</msgUuid>', '\n',
              '<receivedOrgCode>#receivedOrgCode#</receivedOrgCode>', '\n',
              '<format>#format#</format>', '\n',
              '<certificate>#certificate#</certificate>', '\n',
              '<serverName>#serverName#</serverName>', '\n',
              '<convertId>#convertId#</convertId>', '\n',
              '<convertType>#convertType#</convertType>', '\n',
              '<targetType>#targetType#</targetType>', '\n',
              '<senderCode>#senderCode#</senderCode>', '\n',
              '<receiverUrl>#receiverUrl#</receiverUrl>', '\n',
              '<senderOrgCode>#senderOrgCode#</senderOrgCode>', '\n',
              '<applyDistrictCode>#districtCode#</applyDistrictCode>', '\n',
              '<reqUuid>#reqUuid#</reqUuid>', '\n',
              '<msgUuidOrion>#msgUuidOrion#</msgUuidOrion>', '\n',
              '<execDistrictCode>#districtCode#</execDistrictCode>', '\n',
              '<callOperator>#callOperator#</callOperator>', '\n',
              '<serviceId>#serviceId#</serviceId>', '\n',
              '<receivedCode>#receivedCode#</receivedCode>', '\n',
              '</root>'),
       `remark` = CONCAT('独立 XML 文档，由信封以 CDATA 嵌入。系统自动生成 #stepNo# #msgUuid# #convertId#',
              ' #reqUuid# #msgUuidOrion# #visitorIp# #serverName#；其余走参数。')
 WHERE `param_key` = 'MSGPUSH_HEADER_XML'
   AND `param_value` NOT LIKE '%msgUuid%';

-- 2) msgBody 补 patientName
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="UTF-8"?><root>', '\n',
              '<content>#content#</content>', '\n',
              '<hipAction>#hipAction#</hipAction>', '\n',
              '<hipMessageNo>#hipMessageNo#</hipMessageNo>', '\n',
              '<hipToken>#hipToken#</hipToken>', '\n',
              '<mobile>#mobile#</mobile>', '\n',
              '<patientName>#patientName#</patientName>', '\n',
              '<startTime>#startTime#</startTime>', '\n',
              '</root>')
 WHERE `param_key` = 'MSGPUSH_BODY_XML'
   AND `param_value` NOT LIKE '%patientName%';

-- 3) 信封换成 SOAP 1.2 + zys + CallInterface
UPDATE `sys_param`
   SET `param_value` = CONCAT('<?xml version="1.0" encoding="UTF-8"?>', '\n',
              '<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:zys="http://www.zysoft.com.cn/">', '\n',
              '  <soap:Header/>', '\n',
              '  <soap:Body>', '\n',
              '    <zys:CallInterface>', '\n',
              '      <zys:msgHeader><![CDATA[#msgHeader#]]></zys:msgHeader>', '\n',
              '      <zys:msgBody><![CDATA[#msgBody#]]></zys:msgBody>', '\n',
              '    </zys:CallInterface>', '\n',
              '  </soap:Body>', '\n',
              '</soap:Envelope>'),
       `remark` = CONCAT('照平台真实报文对齐：SOAP 1.2 + zys 命名空间 + CallInterface + CDATA 承载两份独立 XML。',
              '现场 WSDL 若不同，只改本项即可。')
 WHERE `param_key` = 'MSGPUSH_ENVELOPE'
   AND `param_value` NOT LIKE '%CallInterface%';

-- 4) Content-Type 必须是 application/soap+xml
UPDATE `sys_param`
   SET `param_value` = 'application/soap+xml; charset=UTF-8',
       `default_value` = 'application/soap+xml; charset=UTF-8',
       `remark` = '真实报文是 SOAP 1.2，必须用 application/soap+xml（用 text/xml 会被网关拒）。'
 WHERE `param_key` = 'MSGPUSH_CONTENT_TYPE'
   AND `param_value` NOT LIKE '%application/soap+xml%';

-- 5) SOAPAction 清空（SOAP 1.2 不用该请求头）
UPDATE `sys_param`
   SET `param_value` = '',
       `default_value` = '',
       `remark` = 'SOAP 1.2 已不用该请求头，留空即不发送。若现场网关实际是 SOAP 1.1 才需填（值要带双引号）。'
 WHERE `param_key` = 'MSGPUSH_SOAP_ACTION'
   AND `param_value` IS NOT NULL
   AND TRIM(`param_value`) <> '';

-- 6) 凭证与 callOperator 换成真实报文里的值（仅当仍是初版样例值）
UPDATE `sys_param`
   SET `param_value` = '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=',
       `remark` = '平台按厂家分发。默认值取自平台提供的真实报文，若非本院可用凭证请替换。'
 WHERE `param_key` IN ('MSGPUSH_HIP_TOKEN', 'MSGPUSH_CERTIFICATE')
   AND `param_value` LIKE 'uHto9nM6CWyLHAno%';

UPDATE `sys_param`
   SET `param_value` = 'PEIS',
       `remark` = '平台按厂家分配。示例（体检）为 PEIS，本系统须向医院索取对应值。'
 WHERE `param_key` = 'MSGPUSH_CALL_OPERATOR'
   AND `param_value` = 'DHZZ';

-- 7) 返回成功标识的说明
UPDATE `sys_param`
   SET `remark` = CONCAT('网关返回 JSON（形如 code / message 两个字段）而非 XML，',
              '且业务失败也返回 HTTP 200。留空按 2xx 判定会误报成功 —— ',
              '拿到成功返回后把成功标志填这里。')
 WHERE `param_key` = 'MSGPUSH_SUCCESS_KEY'
   AND (`remark` IS NULL OR `remark` NOT LIKE '%JSON%');

-- ---------------------------------------------------------------------
-- 8) 新增 11 个参数（全新安装由 58 号写入，这里补已执行过 58 号初版的环境）
-- ---------------------------------------------------------------------
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SENDER_CODE', '发送方代码 senderCode', '', 'msgpush', 20, 1, 'text',
       NULL, '', 0, NULL,
       '平台按厂家分配。真实示例（学生体检）为 PE，本系统须向医院索取，留空会被判校验不通过。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_SENDER_CODE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RECEIVED_CODE', '接收方代码 receivedCode', 'MP', 'msgpush', 21, 1, 'text',
       NULL, 'MP', 0, NULL, '真实示例为 MP（消息平台）。若医院另有约定按现场改。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RECEIVED_CODE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_TARGET_TYPE', '目标类型 targetType', '1', 'msgpush', 22, 1, 'text',
       NULL, '1', 0, NULL, '真实示例为 1。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_TARGET_TYPE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CONVERT_TYPE', '转换类型 convertType', '0', 'msgpush', 23, 1, 'text',
       NULL, '0', 0, NULL, '真实示例为 0。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CONVERT_TYPE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_FORMAT', '数据格式 format', 'HL7V3', 'msgpush', 24, 1, 'text',
       NULL, 'HL7V3', 0, NULL, '真实示例为 HL7V3（不是 xml），填错会被判校验不通过。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_FORMAT');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SERVICE_ID', '服务标识 serviceId', 'sendMessagesToCenterPlatform', 'msgpush', 25, 1, 'text',
       NULL, 'sendMessagesToCenterPlatform', 0, NULL, '真实示例与 serverName 同值。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_SERVICE_ID');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_VISITOR_IP', '调用方 IP visitorIp', '', 'msgpush', 26, 1, 'text',
       NULL, '', 0, NULL,
       '平台按此记录来访来源。留空则由系统自动取本机 IP，一般无需填。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_VISITOR_IP');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RECEIVER_SERVER', '接收服务 receiverServer', 'null', 'msgpush', 27, 1, 'text',
       NULL, 'null', 0, NULL, '真实示例为字符串 null（不是空节点），照抄即可。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RECEIVER_SERVER');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RECEIVER_URL', '接收地址 receiverUrl', 'null', 'msgpush', 28, 1, 'text',
       NULL, 'null', 0, NULL, '真实示例为字符串 null（不是空节点），照抄即可。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RECEIVER_URL');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SENDER_ORG_CODE', '发送方机构码 senderOrgCode', '', 'msgpush', 29, 1, 'text',
       NULL, '', 0, NULL, '真实示例为空，可留空。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_SENDER_ORG_CODE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RECEIVED_ORG_CODE', '接收方机构码 receivedOrgCode', '', 'msgpush', 30, 1, 'text',
       NULL, '', 0, NULL, '真实示例为空，可留空。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RECEIVED_ORG_CODE');

COMMIT;

-- 自检：msgpush 分组应有 30 行
--   SELECT COUNT(*) FROM `sys_param` WHERE `param_group` = 'msgpush';