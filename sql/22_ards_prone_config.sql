-- =====================================================================
-- 22_ards_prone_config.sql
-- ARDS 俯卧位通气治疗记录 —— 采集映射配置（参数项 → 数据源项目）
--
-- 背景：
--   原先把「ARDS 参数项 ← 监护/LIS 项目」的匹配关键字硬编码在 ArdsProneDict，
--   现场数据元命名一变就要改代码发版。本脚本把映射搬到配置表，与 sofa_config /
--   apache2_config 同一套做法（config_type + config_key + config_value），
--   并追加 match_type / priority / window_min / unit_scale / unit_offset 表达力字段。
--
-- 内容：
--   1) ards_prone_config 表 + 序列 + 索引（监测项目映射配置）
--   2) ards_prone_record 的 admit_date / record_date 扩列：VARCHAR(10) → VARCHAR(20)
--      （支持显示格式 yyyy-MM-dd HH:mm；未执行时代码自动降级为只存日期）
--   3) zing_page_config 注册映射配置页（ards-prone-config）
--   4) 不预置种子：由配置页「一键从内置生成」按 ArdsProneDict 写入（幂等，现场可改）
--
-- 说明：本脚本不改动 ards_prone_cell 表结构。取值来源项目（item_code / 项目名称）
--       在采集时随接口返回并在「采集明细」中即时展示，不落库，
--       以避免「jar 已升级但库未加列」导致的取数报错。
--
-- 取值口径（与实现一致）：
--   · 规则按 priority 升序尝试（数字小优先），同优先级先监护（observe_item）后退检验（lis_item）
--   · match_type=code 按 item_code / lis_item_code 精确匹配；name 按项目名称包含匹配（忽略大小写与空格）
--   · config_value 逗号分隔多值，任一命中即可；命中时间超出该参数窗口（window_min 或字典默认 ±15/±60）跳过
--   · 该参数无启用规则或规则全部未命中 → 回退 ArdsProneDict 内置关键字（零回归）
--   · 窗口内无数据一律置空转手工，不沿用历史值
--
-- 幂等：所有 DDL 用 PL/SQL 先判存在性；页面注册用 DELETE + INSERT 守卫，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/22_ards_prone_config.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 映射配置表
-- ---------------------------------------------------------------------
DECLARE
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_SEQUENCES
     WHERE UPPER(SEQUENCE_OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(SEQUENCE_NAME)  = 'SEQ_ARDS_PRONE_CONFIG';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_ards_prone_config" START WITH 1 INCREMENT BY 1';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ARDS_PRONE_CONFIG';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
CREATE TABLE "zing_doctor_db_prod"."ards_prone_config" (
    "id"           BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_ards_prone_config".NEXTVAL NOT NULL,
    "config_type"  VARCHAR(32)  NOT NULL,
    "config_key"   VARCHAR(128) NOT NULL,
    "config_value" VARCHAR(500),
    "match_type"   VARCHAR(16)  DEFAULT ''name'' NOT NULL,
    "priority"     INT          DEFAULT 10,
    "window_min"   INT,
    "unit_scale"   DECIMAL(18, 6),
    "unit_offset"  DECIMAL(18, 6),
    "item_name"    VARCHAR(128),
    "remark"       VARCHAR(255),
    "sort_no"      INT          DEFAULT 1,
    "status"       TINYINT      DEFAULT 1,
    "create_by"    VARCHAR(64),
    "create_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"    VARCHAR(64),
    "update_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_ards_prone_config" PRIMARY KEY ("id")
)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(INDEX_NAME) = 'IDX_ARDS_PRONE_CONFIG_KEY';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX "zing_doctor_db_prod"."idx_ards_prone_config_key" '
            || 'ON "zing_doctor_db_prod"."ards_prone_config" ("config_key", "config_type", "status")';
    END IF;
END;
/

COMMENT ON TABLE  "zing_doctor_db_prod"."ards_prone_config" IS 'ARDS 俯卧位采集映射配置表（参数项 ← 数据源项目；表空或未命中回退字典内置关键字）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."config_type"  IS '数据源通道：observe_item 监护/呼吸机 / lis_item 检验/血气';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."config_key"   IS 'ARDS 参数编码（ArdsProneDict 的 key，如 hr / map / peep / pao2）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."config_value" IS '匹配值：item_code 列表 或 名称关键字列表（逗号分隔多值，任一命中即可）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."match_type"   IS '匹配方式：code 按 item_code 精确 / name 按项目名称包含（忽略大小写与空格）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."priority"     IS '优先级：数字小优先（同参数多渠道时表达「监护优先 / 检验兜底」）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."window_min"   IS '采集窗口覆盖（分钟），空则走字典默认（体征 ±15 / 检验 ±60）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."unit_scale"   IS '单位线性换算系数：值 × scale + offset（如 FiO₂ 0.4 → 40 时 scale=100）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."unit_offset"  IS '单位线性换算偏移量';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."item_name"    IS '项目名称（展示用）';
COMMENT ON COLUMN "zing_doctor_db_prod"."ards_prone_config"."status"       IS '状态：1启用 0停用（停用项不参与取数，该参数回退内置关键字）';

-- ---------------------------------------------------------------------
-- 2) 入院日期 / 记录日期扩列：VARCHAR(10) → VARCHAR(20)
--    显示格式要求 yyyy-MM-dd HH:mm（16 字符），原 10 位只能存日期。
--    代码侧自适应：未扩列时自动降级写 yyyy-MM-dd（降级期间不报错，只是少了时间）。
-- ---------------------------------------------------------------------
DECLARE
    v_len INT;
BEGIN
    SELECT MAX(DATA_LENGTH) INTO v_len FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ARDS_PRONE_RECORD'
       AND UPPER(COLUMN_NAME) = 'ADMIT_DATE';
    IF v_len IS NOT NULL AND v_len < 20 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."ards_prone_record" MODIFY "admit_date" VARCHAR(20)';
    END IF;

    SELECT MAX(DATA_LENGTH) INTO v_len FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ARDS_PRONE_RECORD'
       AND UPPER(COLUMN_NAME) = 'RECORD_DATE';
    IF v_len IS NOT NULL AND v_len < 20 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."ards_prone_record" MODIFY "record_date" VARCHAR(20)';
    END IF;
END;
/

-- ---------------------------------------------------------------------
-- 3) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM "zing_doctor_db_prod"."zing_page_config"
 WHERE "page_code" = 'ards-prone-config';
INSERT INTO "zing_doctor_db_prod"."zing_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES
    ('ards-prone-config', 'ARDS 俯卧位数据映射配置', '/page/ards-prone-config',
     'ARDS 俯卧位采集映射配置：参数项 → 监护/LIS 项目映射、候选选择、试采核对', 1);

-- ---------------------------------------------------------------------
-- 4) 核对（可选）
-- ---------------------------------------------------------------------
--   SELECT "config_key", "config_type", "match_type", "config_value", "priority", "status"
--     FROM "zing_doctor_db_prod"."ards_prone_config" ORDER BY "config_key", "priority";
--   SELECT "page_code", "page_name", "frontend_path", "status"
--     FROM "zing_doctor_db_prod"."zing_page_config" WHERE "page_code" = 'ards-prone-config';

COMMIT;
