-- =====================================================================
-- 58_msg_push.sql（MySQL / MariaDB 镜像）
-- 高风险预警多通道推送：参数分组 + 配置项 + 发送审计表
-- 对应达梦版：sql/58_msg_push.sql（真源，改动请先改那边）
--
-- 接口要点（依平台提供的他厂家真实成功报文）：
--   SOAP 1.2，xmlns:zys="http://www.zysoft.com.cn/"，
--   操作元素 <zys:CallInterface>，两个入参 <zys:msgHeader> / <zys:msgBody>，
--   内容各自用 CDATA 承载一份独立 XML 文档。
--   SOAP 1.2 不用 SOAPAction（改由 Content-Type: application/soap+xml 标识）。
--   msgBody 含 patientName；msgHeader 共 21 个字段，缺项会被判「参数校验不通过」。
--
-- 幂等：可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 参数分组
-- ---------------------------------------------------------------------
INSERT INTO `sys_param_group`
    (`group_code`, `group_name`, `sort_no`, `icon`, `remark`, `status`)
SELECT 'msgpush', '消息推送', 60, 'ChatDotSquare',
       '高风险预警多通道推送：集成平台地址、报文模板、凭证与接收号码。改完点「测试发送」即可联调。', 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'msgpush');

-- ---------------------------------------------------------------------
-- 2) 配置项
-- ---------------------------------------------------------------------
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_ENABLED', '启用预警推送', '0', 'msgpush', 1, 1, 'switch',
       NULL, '0', 0, NULL,
       '总开关。关闭后不自动推送，但「测试发送」仍可用 —— 现场联调不受开关影响。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_ENABLED');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_DRY_RUN', '演练模式', '1', 'msgpush', 2, 1, 'switch',
       NULL, '1', 0, NULL,
       '开启时只渲染并记录报文、不真正发出。现场联调通过后再关掉。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_DRY_RUN');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_URL', '推送接口地址', 'http://100.120.1.151:8693/Rh-Service', 'msgpush', 3, 1, 'text',
       NULL, '', 1, NULL,
       '医院集成平台地址。文档为 Common-Service?wsdl，现场实际为 Rh-Service，以现场为准。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_URL');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_METHOD', '请求方法', 'POST', 'msgpush', 4, 1, 'select',
       '[{"label":"POST","value":"POST"},{"label":"GET","value":"GET"}]',
       'POST', 0, NULL, 'WebService 调用固定 POST；保留 GET 仅为兼容其他网关。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_METHOD');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CONTENT_TYPE', '请求 Content-Type', 'application/soap+xml; charset=UTF-8', 'msgpush', 5, 1, 'text',
       NULL, 'application/soap+xml; charset=UTF-8', 0, NULL,
       '真实报文是 SOAP 1.2，必须用 application/soap+xml（用 text/xml 会被网关拒）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CONTENT_TYPE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SOAP_ACTION', 'SOAPAction', '', 'msgpush', 6, 1, 'text',
       NULL, '', 0, NULL,
       'SOAP 1.2 已不用该请求头，留空即不发送。若现场网关实际是 SOAP 1.1 才需填（值要带双引号）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_SOAP_ACTION');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_HEADERS', '附加请求头', '', 'msgpush', 7, 1, 'textarea',
       NULL, '', 0, NULL, '一行一个 Key: Value；如 Authorization: Bearer xxx（不需要就留空）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_HEADERS');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_HEADER_XML', 'msgHeader 报文模板',
       CONCAT('<?xml version="1.0" encoding="UTF-8"?><root>', '\n',
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
       'msgpush', 8, 1, 'textarea',
       NULL, '', 0, NULL,
       CONCAT('独立 XML 文档，由信封以 CDATA 嵌入。系统自动生成 #stepNo# #msgUuid# #convertId#',
              ' #reqUuid# #msgUuidOrion# #visitorIp# #serverName#；其余走参数。')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_HEADER_XML');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_BODY_XML', 'msgBody 报文模板',
       CONCAT('<?xml version="1.0" encoding="UTF-8"?><root>', '\n',
              '<content>#content#</content>', '\n',
              '<hipAction>#hipAction#</hipAction>', '\n',
              '<hipMessageNo>#hipMessageNo#</hipMessageNo>', '\n',
              '<hipToken>#hipToken#</hipToken>', '\n',
              '<mobile>#mobile#</mobile>', '\n',
              '<patientName>#patientName#</patientName>', '\n',
              '<startTime>#startTime#</startTime>', '\n',
              '</root>'),
       'msgpush', 9, 1, 'textarea',
       NULL, '', 0, NULL,
       '独立 XML 文档，由信封以 CDATA 嵌入。#hipMessageNo# 为 32 位 UUID 去横杠（平台要求不可重复）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_BODY_XML');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_HIP_TOKEN', 'hipToken', '8H5eFjupeFJ/7BYZB1eTYL8jUZFMBMzG8jCVX79/WzY=',
       'msgpush', 10, 1, 'text',
       NULL, '', 0, NULL,
       '由医院现场实施分发。默认值取自平台提供的真实报文，若非本院可用凭证请替换。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_HIP_TOKEN');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_ACTION', '消息类型代码', 'zzxtdx_jcpt', 'msgpush', 11, 1, 'text',
       NULL, 'zzxtdx_jcpt', 0, NULL,
       '对应 hipAction。重症系统为 zzxtdx_jcpt；平台真实示例（体检）为 tjxt_jcpt。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_ACTION');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CALL_OPERATOR', '调用方标识 callOperator', 'PEIS', 'msgpush', 12, 1, 'text',
       NULL, '', 0, NULL,
       '平台按厂家分配。示例（体检）为 PEIS，本系统须向医院索取对应值。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CALL_OPERATOR');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CERTIFICATE', '授权码 certificate', '',
       'msgpush', 13, 1, 'text',
       NULL, '', 0, NULL,
       '平台按厂家分配的授权码，形式以平台分配的为准（说明书示例为 32 位 UUID、不含横杠，向医院集成平台索取本院的值）。本项不做格式校验，与 hipToken 填同值也允许。填错时网关返回 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CERTIFICATE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_DISTRICT_CODE', '区划代码', '01', 'msgpush', 14, 1, 'text',
       NULL, '01', 0, NULL, 'applyDistrictCode 与 execDistrictCode 共用同一值。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_DISTRICT_CODE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RECEIVERS', '接收号码', '', 'msgpush', 15, 1, 'textarea',
       NULL, '', 0, NULL,
       '逗号分隔，测试发送默认发到这些号码。后续要按值班医生/科室路由时再扩展。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RECEIVERS');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_RULES', '触发推送的预警类型', 'ARDS_VENTILATED,GCS_DROP_2', 'msgpush', 16, 1, 'textarea',
       NULL, '', 0, NULL,
       '逗号分隔。工作台 5 类预警：ARDS_VENTILATED、GCS_DROP_2、SOFA_NOT_TODAY、APACHE_NOT_TODAY。本期仅登记。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_RULES');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SUCCESS_KEY', '返回成功标识', '', 'msgpush', 17, 1, 'text',
       NULL, '', 0, NULL,
       CONCAT('网关返回 JSON（如 {"code":"50002","message":"参数校验不通过"}）而非 XML，',
              '且业务失败也返回 HTTP 200。留空按 2xx 判定会误报成功 —— ',
              '拿到成功返回后把成功标志（如 "code":"0"）填这里。')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_SUCCESS_KEY');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_TIMEOUT_MS', '超时毫秒', '5000', 'msgpush', 18, 1, 'number',
       NULL, '5000', 0, '^[0-9]{1,6}$', '单次调用超时。短信网关慢时可调到 10000。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_TIMEOUT_MS');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_ENVELOPE', '请求体模板（SOAP 1.2 信封）',
       CONCAT('<?xml version="1.0" encoding="UTF-8"?>', '\n',
              '<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:zys="http://www.zysoft.com.cn/">', '\n',
              '  <soap:Header/>', '\n',
              '  <soap:Body>', '\n',
              '    <zys:CallInterface>', '\n',
              '      <zys:msgHeader><![CDATA[#msgHeader#]]></zys:msgHeader>', '\n',
              '      <zys:msgBody><![CDATA[#msgBody#]]></zys:msgBody>', '\n',
              '    </zys:CallInterface>', '\n',
              '  </soap:Body>', '\n',
              '</soap:Envelope>'),
       'msgpush', 19, 1, 'textarea',
       NULL, '', 0, NULL,
       CONCAT('照平台真实报文对齐：SOAP 1.2 + zys 命名空间 + CallInterface + CDATA 承载两份独立 XML。',
              '现场 WSDL 若不同，只改本项即可。')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_ENVELOPE');

-- ---- 以下 11 项来自平台真实报文里按厂家分配的字段 ----
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

-- ---------------------------------------------------------------------
-- 3) 发送审计表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `msg_push_log` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene`        VARCHAR(32) NOT NULL COMMENT '场景：test=测试发送 / alert=预警推送',
    `rule_code`    VARCHAR(64) COMMENT '预警类型代码，测试发送时为空',
    `patient_id`   VARCHAR(64) COMMENT 'ICU 患者 ID',
    `patient_name` VARCHAR(64) COMMENT '患者姓名',
    `bed_no`       VARCHAR(64) COMMENT '床号',
    `dept_code`    VARCHAR(64) COMMENT '科室编码',
    `mobile`       VARCHAR(64) COMMENT '接收号码',
    `content`      VARCHAR(2000) COMMENT '短信内容',
    `url`          VARCHAR(500) COMMENT '实际调用的接口地址',
    `http_status`  INT COMMENT 'HTTP 状态码',
    `success`      TINYINT NOT NULL DEFAULT 0 COMMENT '1成功 0失败',
    `dry_run`      TINYINT NOT NULL DEFAULT 0 COMMENT '1演练（未真正发出）',
    `request_body` LONGTEXT COMMENT '实际发出的报文',
    `resp_body`    TEXT COMMENT '接口返回内容',
    `error_msg`    VARCHAR(1000) COMMENT '异常信息',
    `duration_ms`  INT COMMENT '耗时毫秒',
    `operator`     VARCHAR(64) COMMENT '操作人（测试发送时记录）',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_mpl_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '高风险预警消息推送审计';

-- ---------------------------------------------------------------------
-- 执行后自检（可选）：应返回 30 行
--   SELECT `param_key`, `param_value` FROM `sys_param`
--    WHERE `param_group` = 'msgpush' ORDER BY `sort_no`;
-- ---------------------------------------------------------------------