-- 58_msg_push.sql（MySQL 镜像）
-- 高风险预警多通道推送：参数分组 + 配置项 + 发送审计表
-- 对应达梦版：sql/58_msg_push.sql（真源，改动请先改那边）
-- 方言差异：双引号 → 反引号；|| → CONCAT；换行用 \n；达梦版显式取 MAX(id)+1，
--          MySQL 侧 id 为 AUTO_INCREMENT，不传即可。
-- 幂等：可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

INSERT INTO `sys_param_group`
    (`group_code`, `group_name`, `sort_no`, `icon`, `remark`, `status`)
SELECT 'msgpush', '消息推送', 60, 'ChatDotSquare',
       '高风险预警多通道推送：集成平台地址、报文模板、凭证与接收号码。改完点「测试发送」即可联调。', 1
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'msgpush');

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
SELECT 'MSGPUSH_CONTENT_TYPE', '请求 Content-Type', 'text/xml;charset=UTF-8', 'msgpush', 5, 1, 'text',
       NULL, 'text/xml;charset=UTF-8', 0, NULL, 'XML 报文用 text/xml；SOAP 服务可能是 application/soap+xml，按现场调。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CONTENT_TYPE');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_SOAP_ACTION', 'SOAPAction', '', 'msgpush', 6, 1, 'text',
       NULL, '', 0, NULL, '留空则不发送该请求头；SOAP 1.1 网关通常填与方法同名的动作。'
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
       CONCAT('<msgHeader>', '\n',
              '<serverName>sendMessagesToCenterPlatform</serverName>', '\n',
              '<format>xml</format>', '\n',
              '<callOperator>#callOperator#</callOperator>', '\n',
              '<certificate>#certificate#</certificate>', '\n',
              '<applyDistrictCode>#districtCode#</applyDistrictCode>', '\n',
              '<execDistrictCode>#districtCode#</execDistrictCode>', '\n',
              '</msgHeader>'),
       'msgpush', 8, 1, 'textarea',
       NULL, '', 0, NULL,
       'msgHeader 片段（勿写 XML 声明，声明只在信封里）。占位符：#callOperator# #certificate# #districtCode#。按现场 WSDL 调整。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_HEADER_XML');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_BODY_XML', 'msgBody 报文模板',
       CONCAT('<msgBody>', '\n',
              '<hipMessageNo>#hipMessageNo#</hipMessageNo>', '\n',
              '<hipAction>#hipAction#</hipAction>', '\n',
              '<hipToken>#hipToken#</hipToken>', '\n',
              '<mobile>#mobile#</mobile>', '\n',
              '<startTime>#startTime#</startTime>', '\n',
              '<content>#content#</content>', '\n',
              '</msgBody>'),
       'msgpush', 9, 1, 'textarea',
       NULL, '', 0, NULL,
       'msgBody 片段（勿写 XML 声明）。占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_BODY_XML');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_HIP_TOKEN', 'hipToken', 'uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
       'msgpush', 10, 1, 'text',
       NULL, '', 0, NULL, '由医院现场实施分发给每个第三方，样例值为文档内容，上线前必须替换。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_HIP_TOKEN');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_ACTION', '消息类型代码', 'zzxtdx_jcpt', 'msgpush', 11, 1, 'text',
       NULL, 'zzxtdx_jcpt', 0, NULL, '对应 hipAction。重症系统为 zzxtdx_jcpt，见《事件类型字典》。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_ACTION');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CALL_OPERATOR', '调用方标识', 'DHZZ', 'msgpush', 12, 1, 'text',
       NULL, 'DHZZ', 0, NULL, '对应 msgHeader 的 callOperator，由医院侧分配。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_CALL_OPERATOR');

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'MSGPUSH_CERTIFICATE', '平台凭证 certificate', 'uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
       'msgpush', 13, 1, 'text',
       NULL, '', 0, NULL, '对应 msgHeader 的 certificate，样例值为文档内容，上线前必须替换。'
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
       NULL, '', 0, NULL, '返回体中包含该串即判成功（如 <code>0</code>）；留空按 HTTP 2xx 判定。'
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
SELECT 'MSGPUSH_ENVELOPE', '请求体模板（信封）',
       CONCAT('<?xml version="1.0" encoding="utf-8"?>', '\n',
              '<root>', '\n',
              '#msgHeader#', '\n',
              '#msgBody#', '\n',
              '</root>'),
       'msgpush', 19, 1, 'textarea',
       NULL, '', 0, NULL,
       '最终 HTTP 请求体。#msgHeader# / #msgBody# 原样嵌入上面两段；走 SOAP 就按现场 WSDL 在此套上 soap:Envelope。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sys_param` WHERE `param_key` = 'MSGPUSH_ENVELOPE');

COMMIT;

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
