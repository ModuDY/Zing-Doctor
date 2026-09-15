-- =====================================================================
-- 医生决策系统 - 直连登录认证增量（达梦 DM8）
--
-- 背景：
--   此前系统只支持「外链免登录」一种访问方式，直接 IP+端口打开时，
--   所有 /api 请求都因缺少 X-External-PageCode 而被拦截（401），页面全空。
--   本次新增「账号密码直连登录」通道：
--     - 直连访问：/api/auth/login 换取 JWT，后续请求带 Authorization 头；
--     - 外链访问：仍走 extToken / expire+sign 校验，不需要登录（逻辑不变）。
--
-- 内容：
--   1) zing_sys_user  管理员/用户表
--
-- 幂等说明（重要）：
--   本脚本可被 install.sh 重复执行（新库全量初始化 + 老库增量升级都会跑）。
--   因此所有 DDL 都用 PL/SQL 先判断存在性再执行，重跑**不会**报
--   「对象已存在 / 序列已存在」，也就不会让 install.sh 误判为初始化失败。
--
-- 账号说明：
--   管理员账号不写在 SQL 里，由后端首次启动时自动写入（admin / zing@123），
--   避免密码哈希在脚本里写死；已有同名账号时不会覆盖。
--   如需自定义初始口令，启动时用环境变量覆盖：
--     ADMIN_USERNAME / ADMIN_PASSWORD / AUTH_JWT_SECRET
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/密码@host:14236
--   SQL> start /opt/zing-doctor/sql/13_auth.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 用户表（序列 / 表 / 唯一索引，均判断存在性）
-- ---------------------------------------------------------------------
DECLARE
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_SEQUENCES
     WHERE UPPER(SEQUENCE_OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(SEQUENCE_NAME)  = 'SEQ_ZING_SYS_USER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_sys_user" START WITH 1 INCREMENT BY 1';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'ZING_SYS_USER';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE "zing_doctor_db_prod"."zing_sys_user" (
            "id"              BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_zing_sys_user".NEXTVAL NOT NULL,
            "username"        VARCHAR(64)  NOT NULL,
            "real_name"       VARCHAR(64),
            "password_hash"   VARCHAR(200) NOT NULL,
            "status"          TINYINT      DEFAULT 1,
            "last_login_time" TIMESTAMP,
            "create_time"     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
            "update_time"     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT "pk_zing_sys_user" PRIMARY KEY ("id")
        )';
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "uk_zing_sys_user_name" ON "zing_doctor_db_prod"."zing_sys_user" ("username")';
    END IF;
END;
/

COMMENT ON TABLE  "zing_doctor_db_prod"."zing_sys_user" IS '直连登录账号表（用户名密码登录，区别于外链免登录）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_user"."username"        IS '登录账号（唯一）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_user"."real_name"       IS '姓名（页面右上角展示）';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_user"."password_hash"   IS '口令散列：pbkdf2$迭代次数$盐(Base64)$摘要(Base64)';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_user"."status"          IS '状态：1启用 0停用';
COMMENT ON COLUMN "zing_doctor_db_prod"."zing_sys_user"."last_login_time" IS '最近一次登录成功时间';

COMMIT;

-- 执行完成后无需手工插入账号：启动后端即自动创建 admin / zing@123。
