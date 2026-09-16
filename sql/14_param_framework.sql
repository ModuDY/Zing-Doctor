-- =====================================================================
-- 14) 参数框架升级：参数分组表 + 参数类型扩展
--
-- 背景：
--   参数设置页原为大表格，分组只是一个手填的文本字段，后期每加一个功能
--   模块的参数都要改前端代码。本脚本把它升级为「分组可维护 + 参数按类型
--   渲染」的通用框架：
--     1) zing_sys_param 增加 参数类型/选项/默认值/必填/校验正则 五列
--     2) 新建 zing_param_group 分组表，页面可自行增删分组
--     3) 预置分组与「外链工号自动注册」相关参数（开关默认关）
--
-- 幂等说明（重要）：
--   本脚本可被 install.sh 重复执行（新库全量初始化 + 老库增量升级都会跑）。
--   所有 DDL 都用 PL/SQL 先判断存在性再执行，重跑**不会**报
--   「列名已存在 / 对象已存在」，不会让 install.sh 误判为初始化失败。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/14_param_framework.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 系统参数表扩展列（逐列判断，已存在则跳过）
-- ---------------------------------------------------------------------
DECLARE
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ZING_SYS_PARAM'
       AND UPPER(COLUMN_NAME) = 'PARAM_TYPE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "param_type" VARCHAR(20) DEFAULT ''text''';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ZING_SYS_PARAM'
       AND UPPER(COLUMN_NAME) = 'OPTIONS';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "options" VARCHAR(1000)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ZING_SYS_PARAM'
       AND UPPER(COLUMN_NAME) = 'DEFAULT_VALUE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "default_value" VARCHAR(1000)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ZING_SYS_PARAM'
       AND UPPER(COLUMN_NAME) = 'REQUIRED';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "required" TINYINT DEFAULT 0';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ZING_SYS_PARAM'
       AND UPPER(COLUMN_NAME) = 'REGEX';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "regex" VARCHAR(200)';
    END IF;
END;
/

-- ---------------------------------------------------------------------
-- 2) 参数分组表（序列 / 表 / 唯一索引，均判断存在性）
-- ---------------------------------------------------------------------
-- 主键说明：id 为普通 BIGINT（不带 IDENTITY），运行期插入由程序生成 15 位全局唯一 ID
-- （毫秒时间戳13 + 机器号1 + 序列号1），见 ZingIdGenerator。
-- 序列 SEQ_xxx 只服务于各脚本里的初始化 INSERT（它们不写 id 列）；
-- 程序运行期 insert 一律自带 15 位 id，默认值不生效，两者不会冲突。
DECLARE
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_SEQUENCES
     WHERE UPPER(SEQUENCE_OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(SEQUENCE_NAME)  = 'SEQ_ZING_PARAM_GROUP';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_param_group" START WITH 1 INCREMENT BY 1';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'ZING_PARAM_GROUP';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE "zing_doctor_db_prod"."zing_param_group" (
            "id"           BIGINT       DEFAULT "zing_doctor_db_prod"."SEQ_zing_param_group".NEXTVAL NOT NULL,
            "group_code"   VARCHAR(64)  NOT NULL,
            "group_name"   VARCHAR(128) NOT NULL,
            "sort_no"      INT          DEFAULT 0,
            "icon"         VARCHAR(64),
            "remark"       VARCHAR(500),
            "status"       TINYINT      DEFAULT 1,
            "create_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_time"  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_zing_param_group" PRIMARY KEY ("id")
        )';
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "uk_zing_param_group_code" ON "zing_doctor_db_prod"."zing_param_group" ("group_code")';
    END IF;
END;
/

COMMENT ON TABLE  "zing_doctor_db_prod"."zing_param_group" IS '参数分组表（参数设置页左侧导航，页面可维护）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_param_group"."group_code" IS '分组编码（唯一，与 zing_sys_param.param_group 对应）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_param_group"."group_name" IS '分组名称（页面展示）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_param_group"."sort_no"    IS '排序号（升序）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_param_group"."icon"       IS '图标名（Element Plus 图标，可空）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_param_group"."status"     IS '状态：1启用 0停用（停用后不在导航显示）';

COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."param_type"    IS '参数类型：text/textarea/number/switch/select，页面据此渲染控件';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."options"       IS 'select 选项 JSON 数组：[{"label":"显示名","value":"存储值"}]';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."default_value" IS '默认值（参数值为空时取用）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."required"      IS '是否必填：1 必填 / 0 选填';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_param"."regex"         IS '校验正则（可选，保存时校验参数值）';

-- ---------------------------------------------------------------------
-- 3) 预置分组（NOT EXISTS 幂等）
--    ⚠️ 达梦不允许没有 FROM 的 SELECT，这里的 FROM DUAL 不能省
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."zing_param_group"
    ("group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT 'archive', '文书归档', 10, 'Folder',
       'SOFA / APACHE II 评分文书归档相关参数（接口地址、归档目录规则）', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_param_group" WHERE "group_code" = 'archive');

INSERT INTO "zing_doctor_db_prod"."zing_param_group"
    ("group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT 'external', '外链集成', 20, 'Link',
       '第三方系统（ICU）外链访问相关参数，如工号自动注册', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_param_group" WHERE "group_code" = 'external');

INSERT INTO "zing_doctor_db_prod"."zing_param_group"
    ("group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT 'score', '评分配置', 30, 'EditPen',
       'SOFA / APACHE II 评分业务参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_param_group" WHERE "group_code" = 'score');

INSERT INTO "zing_doctor_db_prod"."zing_param_group"
    ("group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT 'quality', '质控配置', 40, 'DataAnalysis',
       '质控指标中台相关参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_param_group" WHERE "group_code" = 'quality');

INSERT INTO "zing_doctor_db_prod"."zing_param_group"
    ("group_code", "group_name", "sort_no", "icon", "remark", "status")
SELECT 'system', '系统设置', 90, 'Setting',
       '系统级通用参数', 1
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_param_group" WHERE "group_code" = 'system');

-- ---------------------------------------------------------------------
-- 4) 已有归档参数补齐类型与分组（重复执行无副作用）
-- ---------------------------------------------------------------------
UPDATE "zing_doctor_db_prod"."zing_sys_param"
SET "param_type" = 'textarea'
WHERE "param_key" = 'ARCHIVE_API_URL' AND ("param_type" IS NULL OR "param_type" = 'text');

UPDATE "zing_doctor_db_prod"."zing_sys_param"
SET "param_type" = 'text'
WHERE "param_key" = 'ARCHIVE_DIR' AND ("param_type" IS NULL OR "param_type" = 'text');

UPDATE "zing_doctor_db_prod"."zing_sys_param"
SET "param_group" = 'archive'
WHERE "param_group" IS NULL OR "param_group" = '';

-- ---------------------------------------------------------------------
-- 5) 外链工号自动注册参数（开关默认关）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "remark")
SELECT 'AUTO_REGISTER_ENABLED', '外链工号自动注册', '0', 'external', 10, 1, 'switch',
       '[{"label":"开启","value":"1"},{"label":"关闭","value":"0"}]', '0', 0,
       '开启后，第三方系统外链带来的工号在本系统不存在时自动注册账号。默认关闭：extToken 为固定明文，开启后任何持有外链者都可注册账号。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'AUTO_REGISTER_ENABLED');

INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "remark")
SELECT 'AUTO_REGISTER_PASSWORD_RULE', '自动注册默认口令规则', 'WORK_NO', 'external', 20, 1, 'select',
       '[{"label":"口令同工号","value":"WORK_NO"},{"label":"固定初始口令","value":"FIXED"}]', 'WORK_NO', 0,
       '自动注册账号的初始口令来源。选择「固定初始口令」时需同时配置 AUTO_REGISTER_FIXED_PASSWORD。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'AUTO_REGISTER_PASSWORD_RULE');

INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "remark")
SELECT 'AUTO_REGISTER_FIXED_PASSWORD', '自动注册固定初始口令', '', 'external', 30, 1, 'text',
       NULL, '', 0,
       '仅当「默认口令规则」为 FIXED 时生效。留空则回退为「口令同工号」。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'AUTO_REGISTER_FIXED_PASSWORD');

COMMIT;

-- ---------------------------------------------------------------------
-- 执行后自检（可选）
--   SELECT "group_code", "group_name", "sort_no" FROM "zing_doctor_db_prod"."zing_param_group" ORDER BY "sort_no";
--   SELECT "param_key", "param_value", "param_type" FROM "zing_doctor_db_prod"."zing_sys_param" ORDER BY "param_group", "sort_no";
-- ---------------------------------------------------------------------
