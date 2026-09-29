-- =====================================================================
-- 37) PK/PD 抗菌药物知识库（达梦 DM8）
--
-- 背景：
--   此前 PK/PD 药物参数硬编码在 AbxDrugKnowledge 枚举里，加新药必须改代码重新部署。
--   本表把知识库搬到数据库，配合「PK/PD 药物配置」页面做界面化增删改。
--   匹配逻辑：优先查本表（status=1），未命中再 fallback 到内置枚举。
--
-- 匹配规则（与枚举一致）：
--   1. 精确匹配 drug_name
--   2. contains 匹配（医嘱名含 drug_name 或 drug_name 含医嘱名）
--   3. 模糊匹配（字符重合度 ≥60%）
--
-- 幂等：表与索引均先查存在性再建（裸 CREATE 重跑会报对象已存在，导致
-- 自动初始化在此中断，33 号脚本曾因此炸过）；页面注册按 page_code
-- 删除后重建。可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/37_abx_pkpd_knowledge.sql
-- =====================================================================

-- 建表（幂等：已存在则跳过）。
-- id 不用 IDENTITY 自增：项目全部 36 个实体统一用 MyBatis-Plus ASSIGN_ID（雪花）
-- 在 Java 端生成主键，INSERT 会显式携带 id —— 达梦对 IDENTITY 列拒绝显式赋值，
-- 两者冲突会让保存接口直接 500。与 33 号等新表一致，id 用普通 BIGINT。
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'CONFIG_ABX_PKPD_KNOWLEDGE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
        CREATE TABLE "zing_doctor_db_prod"."config_abx_pkpd_knowledge" (
            "id"                   BIGINT NOT NULL,
            "drug_name"            VARCHAR(128)  NOT NULL,
            "drug_full_name"       VARCHAR(255),
            "pkpd_type"            VARCHAR(32)   NOT NULL,
            "target_param"         VARCHAR(32),
            "target_value"         VARCHAR(64),
            "protein_binding"      INT           DEFAULT 0,
            "clearance_route"      VARCHAR(32),
            "usual_dose"           VARCHAR(128),
            "dose_adjust"          VARCHAR(500),
            "high_protein_binding" TINYINT       DEFAULT 0,
            "tdm_required"         TINYINT       DEFAULT 0,
            "remark"               VARCHAR(500),
            "status"               TINYINT       DEFAULT 1 NOT NULL,
            "create_time"          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_time"          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_config_abx_pkpd_knowledge" PRIMARY KEY ("id")
        )';
    END IF;
END;
/

-- 唯一索引（幂等）：同一通用名只保留一条记录
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(INDEX_NAME) = 'UK_CONFIG_ABX_PKPD_DRUG';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "uk_config_abx_pkpd_drug" ON "zing_doctor_db_prod"."config_abx_pkpd_knowledge" ("drug_name")';
    END IF;
END;
/

COMMENT ON TABLE  "zing_doctor_db_prod"."config_abx_pkpd_knowledge" IS 'PK/PD 抗菌药物知识库（界面化配置，优先于内置枚举）';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."drug_name"            IS '药品通用名（匹配关键词，如 头孢呋辛）';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."drug_full_name"       IS '药品全称（展示用，如 注射用头孢呋辛钠）';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."pkpd_type"            IS 'PK/PD类型：TIME_DEPENDENT / CONCENTRATION_DEPENDENT / TIME_DEPENDENT_LONG_PAE';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."target_param"         IS '目标参数：%T>MIC / Cmax/MIC / AUC/MIC';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."target_value"         IS '目标值，如 ≥50-70%';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."protein_binding"      IS '蛋白结合率(%)';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."clearance_route"      IS '清除途径：renal / hepatic / dual';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."usual_dose"           IS '常用剂量';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."dose_adjust"          IS '肾功能/肝功能不全剂量调整说明';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."high_protein_binding" IS '高蛋白结合率（≥80%）：0否 1是';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."tdm_required"         IS '需要TDM监测：0否 1是';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."remark"               IS '备注';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_abx_pkpd_knowledge"."status"               IS '状态：1启用 0停用';

-- 页面注册（幂等）：/entry/{page_code} 只允许访问 sys_page_config 中已注册且启用的页面。
-- 前端路由与参数设置页的外链一览都已带上 abx-pkpd-config，老库漏登记会被
-- ExternalLinkService 判为「页面未注册」。
DELETE FROM "zing_doctor_db_prod"."sys_page_config"
 WHERE "page_code" = 'abx-pkpd-config';

INSERT INTO "zing_doctor_db_prod"."sys_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('abx-pkpd-config', 'PK/PD 药物知识库配置', '/page/abx-pkpd-config',
     '抗菌药物 PK/PD 参数界面化配置，优先于内置枚举', 1);

COMMIT;
