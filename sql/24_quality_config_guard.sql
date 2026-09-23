-- =====================================================================
-- 24) 质控配置写保护总开关
--
-- 背景：
--   质控配置（事实层 / 指标 / 口径）改一行就能改全院质控口径，属于高危写操作；
--   而配置页与只读看板共用同一套外链鉴权 —— 拿到外链（ICU 模板里写死的
--   extToken）即可改生产口径，风险等级明显不匹配。因此写接口单独再加一层
--   QualityConfigGuard：IP 白名单 / 写接口 Token，两者都不配则一律 403。
--
--   但严格模式对「单人 / 小团队在内网部署」很别扭：想改一次配置得先 SSH 上
--   机器改 application.yml 再重启。本参数就是那个总开关：
--     1 = 跳过一切写权限检查，参数页可直接改（内网 / 开发用）
--     0 = 严格模式，仍需 IP 白名单或 X-Quality-Config-Token（默认）
--
-- 依赖：
--   必须在 14_param_framework.sql 之后执行 —— 本参数挂在它预置的
--   'quality' 分组下，且用到它扩展的 param_type / default_value 等列。
--
-- 幂等说明：
--   与 14 一致，可被 install.sh 重复执行；已存在则跳过，不会重复插入。
--
-- 不执行的后果：
--   功能不挂 —— Java 侧读不到该参数时回退 application.yml 的
--   zing.quality.config-write-open（默认 false，即严格模式）。
--   但「参数设置 - 质控配置」页里看不到这个开关，现场也就无法在页面上切换。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/24_quality_config_guard.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 质控配置写保护总开关
--
-- 值语义：
--   1 = 放行：POST /api/quality/config/** 不再校验 IP 白名单与写 Token，
--        参数页可直接改质控配置。仅适用于单人 / 小团队的内网部署。
--   0 = 严格（默认）：仍走 IP 白名单 / X-Quality-Config-Token 校验，
--        两者都不配时写接口一律 403（fail-closed）。
--
-- 上公网 / 多人使用 / 正式生产前必须改回 0。
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT 'QUALITY_CONFIG_WRITE_OPEN', '质控配置写保护总开关', '0', 'quality', 1, 1, 'switch',
       NULL, '0', 0, '^(0|1)$',
       '1=参数页可直接改质控配置（事实层/指标/口径），免配白名单与 Token，仅供内网或单人使用；0=严格模式（需 IP 白名单或 X-Quality-Config-Token，两者都不配则写接口一律 403）。上公网或多人使用请改回 0。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'QUALITY_CONFIG_WRITE_OPEN');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "param_key", "param_value", "param_type", "default_value"
--     FROM "zing_doctor_db_prod"."sys_param"
--    WHERE "param_group" = 'quality' ORDER BY "sort_no";
-- ---------------------------------------------------------------------
