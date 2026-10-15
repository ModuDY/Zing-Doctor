-- =====================================================================
-- 58_msg_push.sql
-- 高风险预警多通道推送：参数分组 + 配置项 + 发送审计表（达梦 DM8）
-- MySQL 镜像见 sql/mysql/58_msg_push.sql
--
-- 背景：
--   高风险预警目前闭环在「院内主动弹窗」（5 类规则，见 WorkbenchEnrichService），
--   短信 / 院内通讯软件尚缺网关 —— 需医院提供集成平台。本脚本只落地配置与审计，
--   后端 module/msgpush 按模板渲染报文并发送；本期仅提供「测试发送」，不自动推送。
--
-- 接口（福州市第二总医院《消息推送接口说明书》）：
--   统一入口 WebService + XML 报文，方法 sendMessagesToCenterPlatform；
--   入参 msgHeader（serverName / format / callOperator / certificate / 区划代码）
--      + msgBody（hipMessageNo 32 位 UUID 去横杠 / hipAction / hipToken /
--                 mobile / startTime / content）。
--   msgBody 各字段不得为空；含特殊字符须用 CDATA；时间格式 YYYY-MM-DD HH:mm:ss。
--   地址以现场为准：文档写 Common-Service?wsdl，现场实际给 Rh-Service。
--
-- 为什么头和体都做成模板：
--   SOAP 细节（是否 soap:Envelope 包裹、msgHeader/msgBody 是字符串参数还是 XML
--   节点、SOAPAction 是否必发）取决于现场 WSDL，文档没写。做成带占位符的模板后，
--   现场用 SoapUI 调通、把报文贴进「参数设置」即可，不必改代码重新发版。
--   占位符形如 #hipMessageNo#，与 ARCHIVE_DIR 的 #占位符# 风格一致。
--
-- 幂等：可重复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 参数分组：消息推送（供 MSGPUSH_* 参数挂载）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param_group"
    ("id", "group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param_group"),
       'msgpush', '消息推送', 60, 'ChatDotSquare',
       '高风险预警多通道推送：集成平台地址、报文模板、凭证与接收号码。改完点「测试发送」即可联调。', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param_group" WHERE "group_code" = 'msgpush');

-- ---------------------------------------------------------------------
-- 2) 配置项
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_ENABLED', '启用预警推送', '0', 'msgpush', 1, 1, 'switch',
       NULL, '0', 0, NULL,
       '总开关。关闭后不自动推送，但「测试发送」仍可用 —— 现场联调不受开关影响。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_ENABLED');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_DRY_RUN', '演练模式', '1', 'msgpush', 2, 1, 'switch',
       NULL, '1', 0, NULL,
       '开启时只渲染并记录报文、不真正发出。现场联调通过后再关掉。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_DRY_RUN');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_URL', '推送接口地址', 'http://100.120.1.151:8693/Rh-Service', 'msgpush', 3, 1, 'text',
       NULL, '', 1, NULL,
       '医院集成平台地址。文档为 Common-Service?wsdl，现场实际为 Rh-Service，以现场为准。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_URL');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_METHOD', '请求方法', 'POST', 'msgpush', 4, 1, 'select',
       '[{"label":"POST","value":"POST"},{"label":"GET","value":"GET"}]',
       'POST', 0, NULL, 'WebService 调用固定 POST；保留 GET 仅为兼容其他网关。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_METHOD');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_TYPE', '请求 Content-Type', 'text/xml;charset=UTF-8', 'msgpush', 5, 1, 'text',
       NULL, 'text/xml;charset=UTF-8', 0, NULL, 'XML 报文用 text/xml；SOAP 服务可能是 application/soap+xml，按现场调。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_TYPE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_SOAP_ACTION', 'SOAPAction', '', 'msgpush', 6, 1, 'text',
       NULL, '', 0, NULL, '留空则不发送该请求头；SOAP 1.1 网关通常填与方法同名的动作。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_SOAP_ACTION');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_HEADERS', '附加请求头', '', 'msgpush', 7, 1, 'textarea',
       NULL, '', 0, NULL, '一行一个 Key: Value；如 Authorization: Bearer xxx（不需要就留空）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_HEADERS');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_HEADER_XML', 'msgHeader 报文模板',
       '<?xml version="1.0" encoding="utf-8"?>' || CHR(10)
       || '<root>' || CHR(10)
       || '<serverName>sendMessagesToCenterPlatform</serverName>' || CHR(10)
       || '<format>xml</format>' || CHR(10)
       || '<callOperator>#callOperator#</callOperator>' || CHR(10)
       || '<certificate>#certificate#</certificate>' || CHR(10)
       || '<applyDistrictCode>#districtCode#</applyDistrictCode>' || CHR(10)
       || '<execDistrictCode>#districtCode#</execDistrictCode>' || CHR(10)
       || '</root>',
       'msgpush', 8, 1, 'textarea',
       NULL, '', 0, NULL,
       '可用占位符：#callOperator# #certificate# #districtCode# #hipMessageNo# #startTime#。按现场 WSDL 调整。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_HEADER_XML');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_BODY_XML', 'msgBody 报文模板',
       '<?xml version="1.0" encoding="UTF-8"?>' || CHR(10)
       || '<root>' || CHR(10)
       || '<hipMessageNo>#hipMessageNo#</hipMessageNo>' || CHR(10)
       || '<hipAction>#hipAction#</hipAction>' || CHR(10)
       || '<hipToken>#hipToken#</hipToken>' || CHR(10)
       || '<mobile>#mobile#</mobile>' || CHR(10)
       || '<startTime>#startTime#</startTime>' || CHR(10)
       || '<content>#content#</content>' || CHR(10)
       || '</root>',
       'msgpush', 9, 1, 'textarea',
       NULL, '', 0, NULL,
       '可用占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_BODY_XML');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_HIP_TOKEN', 'hipToken', 'uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
       'msgpush', 10, 1, 'text',
       NULL, '', 0, NULL, '由医院现场实施分发给每个第三方，样例值为文档内容，上线前必须替换。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_HIP_TOKEN');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_ACTION', '消息类型代码', 'zzxtdx_jcpt', 'msgpush', 11, 1, 'text',
       NULL, 'zzxtdx_jcpt', 0, NULL, '对应 hipAction。重症系统为 zzxtdx_jcpt，见《事件类型字典》。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_ACTION');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CALL_OPERATOR', '调用方标识', 'DHZZ', 'msgpush', 12, 1, 'text',
       NULL, 'DHZZ', 0, NULL, '对应 msgHeader 的 callOperator，由医院侧分配。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CALL_OPERATOR');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CERTIFICATE', '平台凭证 certificate', 'uHto9nM6CWyLHAnoDKBajUEWtoLGBkBmayohdFOxJCQSOZwPRuqHeA==',
       'msgpush', 13, 1, 'text',
       NULL, '', 0, NULL, '对应 msgHeader 的 certificate，样例值为文档内容，上线前必须替换。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CERTIFICATE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_DISTRICT_CODE', '区划代码', '01', 'msgpush', 14, 1, 'text',
       NULL, '01', 0, NULL, 'applyDistrictCode 与 execDistrictCode 共用同一值。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_DISTRICT_CODE');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RECEIVERS', '接收号码', '', 'msgpush', 15, 1, 'textarea',
       NULL, '', 0, NULL,
       '逗号分隔，测试发送默认发到这些号码。后续要按值班医生/科室路由时再扩展。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RECEIVERS');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_RULES', '触发推送的预警类型', 'ARDS_VENTILATED,GCS_DROP_2', 'msgpush', 16, 1, 'textarea',
       NULL, '', 0, NULL,
       '逗号分隔。工作台 5 类预警：ARDS_VENTILATED、GCS_DROP_2、SOFA_NOT_TODAY、APACHE_NOT_TODAY。本期仅登记。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_RULES');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_SUCCESS_KEY', '返回成功标识', '', 'msgpush', 17, 1, 'text',
       NULL, '', 0, NULL, '返回体中包含该串即判成功（如 <code>0</code>）；留空按 HTTP 2xx 判定。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_SUCCESS_KEY');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_TIMEOUT_MS', '超时毫秒', '5000', 'msgpush', 18, 1, 'number',
       NULL, '5000', 0, '^[0-9]{1,6}$', '单次调用超时。短信网关慢时可调到 10000。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_TIMEOUT_MS');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_ENVELOPE', '请求体模板（信封）',
       '<?xml version="1.0" encoding="utf-8"?>' || CHR(10)
       || '<root>' || CHR(10)
       || '#msgHeader#' || CHR(10)
       || '#msgBody#' || CHR(10)
       || '</root>',
       'msgpush', 19, 1, 'textarea',
       NULL, '', 0, NULL,
       '最终 HTTP 请求体。#msgHeader# / #msgBody# 原样嵌入上面两段；走 SOAP 就按现场 WSDL 在此套上 soap:Envelope。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_ENVELOPE');

COMMIT;

-- ---------------------------------------------------------------------
-- 3) 发送审计表：每次发送（含测试与演练）都留痕，可追可查
-- ---------------------------------------------------------------------
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'msg_push_log';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."msg_push_log" (
            "id"            BIGINT NOT NULL,
            "scene"         VARCHAR(32) NOT NULL,
            "rule_code"     VARCHAR(64),
            "patient_id"    VARCHAR(64),
            "patient_name"  VARCHAR(64),
            "bed_no"        VARCHAR(64),
            "dept_code"     VARCHAR(64),
            "mobile"        VARCHAR(64),
            "content"       VARCHAR(2000),
            "url"           VARCHAR(500),
            "http_status"   INT,
            "success"       TINYINT DEFAULT 0 NOT NULL,
            "dry_run"       TINYINT DEFAULT 0 NOT NULL,
            "request_body"  CLOB,
            "resp_body"     VARCHAR(2000),
            "error_msg"     VARCHAR(1000),
            "duration_ms"   INT,
            "operator"      VARCHAR(64),
            "create_time"   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_msg_push_log" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'msg_push_log'
       AND INDEX_NAME = 'idx_mpl_create_time';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_mpl_create_time" ON "zing_doctor_db_prod"."msg_push_log" ("create_time")';
    END IF;
END;
/

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value" FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_group" = 'msgpush' ORDER BY "sort_no";   -- 应返回 19 行
--   SELECT COUNT(*) FROM "zing_doctor_db_prod"."msg_push_log";  -- 应为 0
-- ---------------------------------------------------------------------
