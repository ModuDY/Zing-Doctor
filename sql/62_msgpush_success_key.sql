-- =====================================================================
-- 62_msgpush_success_key.sql
-- 「返回成功标识」按平台真实返回填默认值，业务失败不再被误判成成功
-- MySQL 镜像见 sql/mysql/62_msgpush_success_key.sql
--
-- 现场实测（2026-10-16，短信消息推送成功后的网关返回）：
--   <soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope"
--                     xmlns:zys="http://www.zysoft.com.cn/">
--     <soapenv:Body><zys:CallInterfaceResponse><zys:payload>
--       <![CDATA[{"code":"00000","data":"发送消息处理中....","message":"","error":""}]]>
--     </zys:payload></zys:CallInterfaceResponse></soapenv:Body>
--   </soapenv:Envelope>
--   两点：业务码在 SOAP 信封的 payload CDATA 里才是 JSON，成功码是 "00000"；
--   业务失败同样是 HTTP 200（如 {"code":"50002","message":"无效的授权码..."}），
--   所以「留空按 2xx 判成功」会把失败记成发送成功。
--
-- 本脚本一件事：把成功标识填成 "code":"00000"（判定方式是「返回体包含该串」）。
--   - 只在参数为空时填，现场已填过别的值的不覆盖；
--   - 说明同步更新为真实返回样例；平台若另有约定，改这一个参数即可。
--
-- 幂等：可重复执行。
-- =====================================================================

UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = CASE WHEN "param_value" IS NULL OR TRIM("param_value") = ''
                           THEN '"code":"00000"' ELSE "param_value" END,
       "remark" = '网关把业务结果放在 SOAP 信封里，payload 的 CDATA 才是 JSON：'
               || '成功 {"code":"00000","data":"发送消息处理中....","message":"","error":""}；'
               || '失败如 {"code":"50002","message":"无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)"}，'
               || '且业务失败同样返回 HTTP 200。'
               || '本项按「返回体包含该串」判成功，默认 "code":"00000" —— '
               || '留空会退化成按 HTTP 2xx 判定，业务失败也会被记成发送成功。'
 WHERE "param_key" = 'MSGPUSH_SUCCESS_KEY';
