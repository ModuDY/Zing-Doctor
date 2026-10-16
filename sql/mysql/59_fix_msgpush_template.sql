-- 59_fix_msgpush_template.sql（MySQL 镜像）
-- 修正 58 号写入的报文模板默认值：片段不应带 XML 声明
-- 对应达梦版：sql/59_fix_msgpush_template.sql（真源，改动请先改那边）
--
-- 背景：58 号给 MSGPUSH_HEADER_XML / MSGPUSH_BODY_XML 的默认值带了
--   `<?xml ...?>` 声明和一层 <root>，但它们是片段、会被原样嵌进信封，
--   渲染出的报文里会有三份 XML 声明 —— 网关必然解析失败。
--   改为片段各自以 <msgHeader> / <msgBody> 为根，声明只留在信封里。
--
-- 幂等且不覆盖现场改动：仅当值仍是 58 号的旧默认（含 `<?xml version=`）时才更新。
-- =====================================================================
SET NAMES utf8mb4;

UPDATE `sys_param`
   SET `param_value` = CONCAT('<msgHeader>', '\n',
              '<serverName>sendMessagesToCenterPlatform</serverName>', '\n',
              '<format>xml</format>', '\n',
              '<callOperator>#callOperator#</callOperator>', '\n',
              '<certificate>#certificate#</certificate>', '\n',
              '<applyDistrictCode>#districtCode#</applyDistrictCode>', '\n',
              '<execDistrictCode>#districtCode#</execDistrictCode>', '\n',
              '</msgHeader>'),
       `remark` = 'msgHeader 片段（勿写 XML 声明，声明只在信封里）。占位符：#callOperator# #certificate# #districtCode#。按现场 WSDL 调整。'
 WHERE `param_key` = 'MSGPUSH_HEADER_XML'
   AND `param_value` LIKE '<?xml version=%';

UPDATE `sys_param`
   SET `param_value` = CONCAT('<msgBody>', '\n',
              '<hipMessageNo>#hipMessageNo#</hipMessageNo>', '\n',
              '<hipAction>#hipAction#</hipAction>', '\n',
              '<hipToken>#hipToken#</hipToken>', '\n',
              '<mobile>#mobile#</mobile>', '\n',
              '<startTime>#startTime#</startTime>', '\n',
              '<content>#content#</content>', '\n',
              '</msgBody>'),
       `remark` = 'msgBody 片段（勿写 XML 声明）。占位符：#hipMessageNo#（32位UUID，系统生成）#hipAction# #hipToken# #mobile# #startTime# #content# #patientName# #bedNo# #ruleName#。'
 WHERE `param_key` = 'MSGPUSH_BODY_XML'
   AND `param_value` LIKE '<?xml version=%';

COMMIT;

-- 自检：HEADER/BODY 应为「片段无声明」，ENVELOPE 为「含声明」
--   SELECT `param_key`, IF(`param_value` LIKE '<?xml version=%', '含声明', '片段无声明')
--     FROM `sys_param`
--    WHERE `param_group` = 'msgpush'
--      AND `param_key` IN ('MSGPUSH_HEADER_XML','MSGPUSH_BODY_XML','MSGPUSH_ENVELOPE');