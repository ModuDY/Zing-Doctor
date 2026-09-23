-- =====================================================================
-- 24_quality_config_guard.sql
-- 质控配置写保护开关（MySQL / MariaDB 版）
--
-- 对应达梦版：sql/24_quality_config_guard.sql（真源，改动请先改那边）
-- 方言差异仅为「双引号 → 反引号」的表/列限定符；另多一条 sys_param_group
-- 的补建（MySQL 侧不一定有 14 预置的 quality 分组），达梦侧由 14 预置故不写。
--   QUALITY_CONFIG_WRITE_OPEN  质控配置写接口总开关：1 开 / 0 关（默认 0）
--                              开 = 参数设置页可直接改质控配置（事实层/指标/口径），
--                                   不用 SSH 改 application.yml 重启；
--                              关 = 回到「IP 白名单 / 写接口 Token」严格模式。
--   在「参数设置 - 质控配置」分组里切换，保存即生效（带缓存自动清）。
--   幂等：已存在不覆盖。
-- =====================================================================

-- 质控配置分组（不存在才建）
INSERT INTO `sys_param_group` (`group_code`, `group_name`, `sort_no`, `status`, `remark`)
SELECT 'quality', '质控配置', 40, 1, '质控指标中台：配置写保护与运行开关'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `sys_param_group` WHERE `group_code` = 'quality'
 );

-- 写保护总开关（默认关：保留严格模式）
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_type`, `param_group`, `sort_no`, `status`, `remark`)
SELECT 'QUALITY_CONFIG_WRITE_OPEN', '质控配置写保护总开关', '0', 'switch', 'quality', 1, 1,
       '1=参数页可直接改质控配置（内网/单人用，免配白名单与 Token）；0=严格模式（需 IP 白名单或 X-Quality-Config-Token）。上公网/多人使用请改回 0'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `sys_param` WHERE `param_key` = 'QUALITY_CONFIG_WRITE_OPEN'
 );
