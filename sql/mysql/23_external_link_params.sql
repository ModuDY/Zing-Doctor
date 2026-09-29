-- =====================================================================
-- 23_external_link_params.sql
-- 外链集成参数（MySQL / MariaDB 版）
--   EXTERNAL_LINK_BASE_URL   医生决策系统对外访问基础地址（含端口），
--                            后端 /entry 302 跳转用；前端外链示例也据此显示真实地址
--   EXTERNAL_LINK_ICU_TOKEN  ICU 明文外链固定令牌；后端校验 extToken 时优先读它，
--                            未配置才回落 application.yml 的 zing.doctor.external-link.icu-token
--   幂等：已存在不覆盖
-- =====================================================================

-- 外链参数分组（不存在才建）
INSERT INTO `sys_param_group` (`group_code`, `group_name`, `sort_no`, `status`, `remark`)
SELECT 'external', '外链集成', 30, 1, 'ICU 系统外链对接：对外访问地址与固定令牌'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'external'
 );

-- 对外访问基础地址
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`, `status`, `remark`)
SELECT 'EXTERNAL_LINK_BASE_URL', '对外访问地址', '', 'external', 1, 1,
       '本系统对外可访问的根地址（含 http(s) 与端口），如 https://doctor.example.com:2001。后端 /entry 据此 302，外链示例据此拼真实链接；留空则按请求方 Host 跳转'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `sys_param` WHERE `param_key` = 'EXTERNAL_LINK_BASE_URL'
 );

-- ICU 固定令牌（默认空，在「参数设置-外链页面」点「随机生成」后填入）
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`, `status`, `remark`)
SELECT 'EXTERNAL_LINK_ICU_TOKEN', 'ICU 外链固定令牌', '', 'external', 2, 1,
       'ICU 外链模板中写死的 extToken 值。点「随机生成」自动更新；更换后 ICU 侧写死的旧值会立即失效，需同步更新'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `sys_param` WHERE `param_key` = 'EXTERNAL_LINK_ICU_TOKEN'
 );
