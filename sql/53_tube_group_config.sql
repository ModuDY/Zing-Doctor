-- =====================================================================
-- 53_tube_group_config.sql
-- 导管类型分组配置表（自定义导管类型归组 + 危重等级）
-- （达梦 DM8）MySQL 镜像见 sql/mysql/53_tube_group_config.sql
--
-- 功能：导管统计页「导管类型管理」弹窗
-- 后端：module/tube（/api/tube/groups/*），TubeGroupConfig / TubeGroupConfigService
-- 归类优先级：本表配置 > 三管白名单（CVC/导尿管/气道导管）> 其他
-- 危重等级：高（气道/CVC 默认）/ 中（导尿管默认）/ 低（其余默认）
-- 幂等：可重复执行。
-- =====================================================================

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE OWNER = 'zing_doctor_db_prod' AND TABLE_NAME = 'config_tube_group';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."config_tube_group" (
            "id"           BIGINT NOT NULL,
            "tube_code"    VARCHAR(64) NOT NULL,
            "tube_name"    VARCHAR(128),
            "group_name"   VARCHAR(64) DEFAULT ''其他'' NOT NULL,
            "risk_level"   VARCHAR(8)  DEFAULT ''中'' NOT NULL,
            "sort_no"      INT DEFAULT 0,
            "remark"       VARCHAR(1000),
            "status"       TINYINT DEFAULT 1 NOT NULL,
            "create_by"    VARCHAR(64),
            "create_time"  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_by"    VARCHAR(64),
            "update_time"  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_config_tube_group" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE OWNER = 'zing_doctor_db_prod'
       AND TABLE_NAME = 'config_tube_group'
       AND INDEX_NAME = 'uk_config_tube_group_code';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "uk_config_tube_group_code" ON "zing_doctor_db_prod"."config_tube_group" ("tube_code")';
    END IF;
END;
/
