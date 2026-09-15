# 数据库变更清单（升级必读）

> ⚠️ **每次部署前先看这个文件。**
>
> 应用容器里**没有达梦客户端**，`install.sh` **不会、也无法自动执行 SQL**。
> 只要本次交付包含表结构变更，就必须由人工在数据库上执行，否则新代码一上来就会 500。
>
> 典型症状（页面打开即报错）：
> ```
> ### Error querying database. Cause: dm.jdbc.driver.DMException: 无效的列名[xxx]
> ```
>
> **执行完不需要重启容器**，MyBatis 每次重新解析 SQL，列加上后刷新页面即生效。

## 怎么用

1. 用 DM 管理工具 / disql 连到达梦（SYSDBA 或同权限账号）
2. 翻到下面「批次记录」，从自己**还没执行**的批次开始，按 ①②③ 顺序执行
3. 某一块报「已存在 / already exists」→ 说明已执行过，**跳过该块继续**
4. 全新部署不用来这里，`sql/` 目录下按序号（01 → 12）依次全跑即可

## 执行状态速查

```sql
-- 某张表有哪些列（对照清单确认缺什么）
SELECT UPPER(COLUMN_NAME) FROM USER_TAB_COLUMNS WHERE UPPER(TABLE_NAME) = 'SOFA_SCORE_RECORD';

-- 某张表是否存在
SELECT TABLE_NAME FROM USER_TABLES WHERE UPPER(TABLE_NAME) = 'ZING_SYS_PARAM';

-- 某配置是否已写入
SELECT COUNT(*) FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'ARCHIVE_DIR';
```

---

## 批次记录

### 2026-09-15 · 评分文书归档

**涉及**

- `sofa_score_record`、`apache2_score_record` 增加 `archive_status` / `archive_time` / `file_path` 三列
- 新建 `zing_sys_param`（系统参数表，参数设置页面）
- 新建 `zing_archive_log`（归档推送流水表）
- 注册页面 `param-config`（参数设置）
- 写入两条默认参数：`ARCHIVE_API_URL`、`ARCHIVE_DIR`

**不执行的后果**：SOFA、APACHE II 评分页打开即 500（无效的列名 archive_status）；参数设置页面因表不存在无法读写。

**① 评分记录加列**

```sql
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "archive_status" TINYINT DEFAULT 0;
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "archive_time" TIMESTAMP;
ALTER TABLE "zing_doctor_db_prod"."sofa_score_record" ADD COLUMN "file_path" VARCHAR(500);

ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "archive_status" TINYINT DEFAULT 0;
ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "archive_time" TIMESTAMP;
ALTER TABLE "zing_doctor_db_prod"."apache2_score_record" ADD COLUMN "file_path" VARCHAR(500);
```

**② 系统参数表**

```sql
CREATE TABLE "zing_doctor_db_prod"."zing_sys_param" (
    "id"           BIGINT        IDENTITY(1,1) NOT NULL,
    "param_key"    VARCHAR(64)   NOT NULL,
    "param_name"   VARCHAR(128)  NOT NULL,
    "param_value"  VARCHAR(1000),
    "param_group"  VARCHAR(64)   DEFAULT 'archive',
    "sort_no"      INT           DEFAULT 0,
    "status"       TINYINT       DEFAULT 1,
    "remark"       VARCHAR(500),
    "create_by"    VARCHAR(64),
    "create_time"  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_by"    VARCHAR(64),
    "update_time"  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_sys_param" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_zing_sys_param_key" ON "zing_doctor_db_prod"."zing_sys_param" ("param_key");
```

**③ 归档推送流水表**

```sql
CREATE TABLE "zing_doctor_db_prod"."zing_archive_log" (
    "id"             BIGINT        IDENTITY(1,1) NOT NULL,
    "biz"            VARCHAR(32),
    "record_id"      BIGINT,
    "in_hospital_no" VARCHAR(64),
    "patient_name"   VARCHAR(64),
    "doc_code"       VARCHAR(32),
    "score_date"     VARCHAR(10),
    "file_path"      VARCHAR(500),
    "api_url"        VARCHAR(500),
    "op_type"        VARCHAR(16),
    "success"        TINYINT       DEFAULT 0,
    "http_status"    INT,
    "resp_code"      INT,
    "resp_message"   VARCHAR(1000),
    "operator"       VARCHAR(64),
    "create_time"    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_archive_log" PRIMARY KEY ("id")
);
CREATE INDEX "idx_zing_archive_log_rec" ON "zing_doctor_db_prod"."zing_archive_log" ("biz", "record_id");
CREATE INDEX "idx_zing_archive_log_no"  ON "zing_doctor_db_prod"."zing_archive_log" ("in_hospital_no");
```

**④ 页面注册**

```sql
DELETE FROM "zing_doctor_db_prod"."zing_page_config" WHERE "page_code" = 'param-config';
INSERT INTO "zing_doctor_db_prod"."zing_page_config"
    ("page_code", "page_name", "frontend_path", "remark", "status")
VALUES ('param-config', '参数设置', '/page/param-config', '系统参数设置：文书归档接口地址、归档目录等', 1);
```

**⑤ 默认参数**

> 达梦不允许没有 FROM 的 SELECT，这里的 `FROM DUAL` 不能省。

```sql
INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no", "status", "remark")
SELECT 'ARCHIVE_API_URL', '文书归档接口地址', '', 'archive', 1, 1,
       '评分文书归档推送地址（完整 URL，含 jodName），SOFA 与 APACHE II 共用'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'ARCHIVE_API_URL'
  );

INSERT INTO "zing_doctor_db_prod"."zing_sys_param"
    ("param_key", "param_name", "param_value", "param_group", "sort_no", "status", "remark")
SELECT 'ARCHIVE_DIR', '归档目录', '/ICU/#in_hospital_no#/#doc_code#/#score_date#', 'archive', 2, 1,
       '文书存放目录规则：占位符 #in_hospital_no# 住院号 / #doc_code# 文书编码(sofa|apache2) / #score_date# 评分日期，也支持 #patient_id# / #patient_name#'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_key" = 'ARCHIVE_DIR'
  );
```

**执行后配置**：进「参数设置」页面把 `ARCHIVE_API_URL` 填成院方归档接口地址，`ARCHIVE_DIR` 按院方实际目录结构调整（默认值可直接用）。
