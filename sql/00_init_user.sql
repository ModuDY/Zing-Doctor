-- =====================================================================
-- 医生决策系统 - 达梦模式初始化
-- 执行：以 SYSDBA 连接达梦执行一次（install.sh 自动执行，也可手动）
--       disql SYSDBA/Sa_20250815@100.120.1.102:14236
--       SQL> start /opt/zing-doctor/sql/00_init_user.sql
--
-- 说明：
--   医生决策系统统一使用 SYSDBA 连接达梦（与 ICU 系统访问方式一致），
--   通过显式模式前缀访问各模式。
--   本脚本创建医生决策系统自有模式 zing_doctor_db_prod（属主 SYSDBA），
--   医生决策系统自有表（sys_page_config 等）建于此模式下，不使用 SYSDBA 默认模式。
--   ICU 系统库模式（zing_icu_db_prod）由 ICU 只读数据源访问，本脚本不触碰。
-- =====================================================================

-- 创建医生决策系统自有模式（幂等：DbInit 会先查 SYS.SYSOBJECTS，模式已存在则跳过创建；
-- 达梦 8.1.3.140 不支持 CREATE SCHEMA IF NOT EXISTS，故不用该语法；
-- disql 中 CREATE SCHEMA 须以 "/" 结束）
CREATE SCHEMA "zing_doctor_db_prod" AUTHORIZATION SYSDBA;
/

-- 验证
--   SELECT * FROM ALL_OBJECTS WHERE OBJECT_NAME = 'ZING_DOCTOR_DB_PROD' AND OBJECT_TYPE = 'SCH';
--   SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = 'ZING_DOCTOR_DB_PROD';
