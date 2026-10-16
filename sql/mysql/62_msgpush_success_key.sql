-- =====================================================================
-- 62_msgpush_success_key.sql（MySQL 镜像）
-- 达梦版见 sql/62_msgpush_success_key.sql（真实返回样例与原因写在那份里）
--
-- 成功返回的 payload CDATA 是 {"code":"00000","data":"发送消息处理中....","message":"","error":""}，
-- 业务失败同样返回 HTTP 200 —— 本脚本把成功标识填成 "code":"00000"（仅在为空时填）；幂等可重复执行。
-- =====================================================================

UPDATE `sys_param`
   SET `param_value` = CASE WHEN `param_value` IS NULL OR TRIM(`param_value`) = ''
                            THEN '"code":"00000"' ELSE `param_value` END,
       `remark` = '网关把业务结果放在 SOAP 信封里，payload 的 CDATA 才是 JSON：成功 {"code":"00000","data":"发送消息处理中....","message":"","error":""}；失败如 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}，且业务失败同样返回 HTTP 200。本项按「返回体包含该串」判成功，默认 "code":"00000" —— 留空会退化成按 HTTP 2xx 判定，业务失败也会被记成发送成功。'
 WHERE `param_key` = 'MSGPUSH_SUCCESS_KEY';
