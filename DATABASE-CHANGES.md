# 数据库变更清单（升级必读）

> ⚠️ **每次部署前先看这个文件。**
>
> 应用**运行容器**里没有达梦客户端，但 `install.sh` 在部署机上会尝试自动应用增量脚本
> （`apply_incremental`：本机 disql / 达梦容器内 disql / JDK + DbInit 三条通道，命中任一条即自动执行）。
> 所以**先跑 `install.sh`，再照着下面的清单逐项核对**——只有三条通道都没命中、或日志里明确报了失败，
> 才需要人工补执行。
>
> 自动增量只覆盖 `INCREMENTAL_SQL` 里列出的脚本（09/10/11/12/13/14/15/16/17/18/19/20/21/22/23）；全量初始化与
> 一次性脚本（如 06_abx_drug_dict.sql）不在其内。漏执行 = 新代码一上来就 500。
> MySQL/MariaDB 环境由 `install-mariadb-debian.sh` 每次全量重跑 `MAIN_SQL`，不存在「漏增量」问题，但脚本必须两边都有（见下方四步规则）。
>
> 典型症状（页面打开即报错）：
> ```
> ### Error querying database. Cause: dm.jdbc.driver.DMException: 无效的列名[xxx]
> ```
>
> **执行完不需要重启容器**，MyBatis 每次重新解析 SQL，列加上后刷新页面即生效。
>
> **双数据库**：同一套库表结构有达梦 DM8（`sql/` + `install.sh`）与 MySQL/MariaDB（`sql/mysql/` + `install-mariadb-debian.sh`）两套脚本，
> 新增脚本必须**两边同时落地**，规则见本文「双数据库：脚本归属与新增脚本四步规则」一节。

## 怎么用

1. 用 DM 管理工具 / disql 连到达梦（SYSDBA 或同权限账号）
2. 翻到下面「批次记录」，从自己**还没执行**的批次开始，按 ①②③ 顺序执行
3. 某一块报「已存在 / already exists」→ 说明已执行过，**跳过该块继续**
4. 全新部署不用来这里，`sql/` 目录下按序号（01 → 23）依次全跑即可

---

## 双数据库：脚本归属与新增脚本四步规则

同一套库表结构存在两份 SQL 实现。**达梦版是真源，MySQL/MariaDB 版是转换产物**，不要直接手改后者。

| | 达梦 DM8 | MySQL / MariaDB |
|---|---|---|
| 脚本目录 | `sql/`（手写，真源） | `sql/mysql/`（由 `tools/dm_to_mysql.py` 生成） |
| 安装 / 升级脚本 | `install.sh` | `install-mariadb-debian.sh` |
| 连接配置来源 | `docker-compose.yml` 的 `DOCTOR_URL` / `DOCTOR_USERNAME` / `DOCTOR_PASSWORD` | `conf/db.conf`（`DB_HOST` / `DB_PORT` / `DB_ADMIN_*` / `APP_DB_*` …） |
| Spring profile | `dm`（默认） | `mariadb` / `mysql`（安装脚本自动写入） |
| 数据库对象 | schema `zing_doctor_db_prod`（统一 SYSDBA 连接、显式模式前缀） | database `zing_doctor_db_prod` |
| 幂等手段 | 脚本内 PL/SQL 判存在 + JDBC 工具（`tools/db-init`）查元数据跳过 | `IF NOT EXISTS` + `sql/mysql/00b_*` 的幂等存储过程 |
| 每次升级是否全量重跑 | **否**：探测 `zing_page_config` 已存在则只跑增量清单 | **是**：`MAIN_SQL` 里的脚本每次全跑 |
| 需维护的清单 | `FULL_SQL` / `FULL_SQL_JDBC` / `INCREMENTAL_SQL`（三处） | `MAIN_SQL`（一处） |
| 跳过 SQL 的参数 | `--skip-db` | `--config-only` |
| 备份方式 | `dexp` 或 DBA 侧模式备份 | `mysqldump` / `mariadb-dump` |

### 新增一个 SQL 脚本，必须做完这四步

1. **写达梦版**：新建 `sql/NN_xxx.sql`，内部自带存在性判断 —— DDL 用 PL/SQL 查 `ALL_TABLES` / `ALL_TAB_COLUMNS`；
   种子写 `INSERT … SELECT … FROM DUAL WHERE NOT EXISTS`；页面注册先 `DELETE` 再 `INSERT`。
   达梦不允许无 `FROM` 的 `SELECT`，`FROM DUAL` 不能省。
2. **生成 MySQL 版**：`python tools/dm_to_mysql.py`，产出 `sql/mysql/NN_xxx.sql`。
   转换只做机械方言映射，**生成后必须人工复核并在真实 MySQL/MariaDB 实例导入验证** ——
   表达式索引（要改成 `STORED` 生成列）与 `SEQ_xxx.NEXTVAL` 默认值（改 `AUTO_INCREMENT`）这两类差异通常还需手工改写。
3. **登记达梦侧三处清单**（`install.sh` 第 123 / 148 / 192 行附近）：`FULL_SQL`、`FULL_SQL_JDBC`、`INCREMENTAL_SQL`。
   顺序 = 依赖顺序：建表在前、加列在后（`12` → `14`；`09/10/11` → `15`~`20`；`21` → `22` → `23`）。
   **漏登记 `INCREMENTAL_SQL` → 老库升级不补表**，页面报「无效的表或视图名」（`22_ards_prone_config.sql` 就这么踩过）。
4. **登记 MariaDB 侧一处清单**：`install-mariadb-debian.sh` 的 `MAIN_SQL` 数组，插到依赖脚本之后。
   该清单每次全跑，所以只需一处；漏登记的表现是新装环境直接缺表 —— 比达梦侧更早暴露、更好查。

### 一次性脚本与人工动作（两边都一样）

- `06_abx_drug_dict.sql` 是**一次性脚本**（裸 `CREATE TABLE`），不在达梦增量清单内；老库从未执行过需手动跑一次，
  否则抗菌药识别持续告警 `无效的表或视图名[zing_abx_drug_dict]`。
- `11_quality_count_rule.sql` 建表后，需在看板点一次「同步指标规则」从 ICU 侧灌数（脚本刻意不含同步语句）。
- `03_word_inc_*` / `04_abx_word_training.sql` 为词库增量，按需执行；MySQL 侧默认在 `install-mariadb-debian.sh` 里注释掉，需要时手动放出来。

### 漂移自检（建议每次交付打包前跑）

两边脚本集合与库结构必须对齐：

```bash
ls sql/*.sql | wc -l && ls sql/mysql/*.sql | wc -l   # 除 00b/00c/24_fix/install-all 外应一一对应

# 主库表数量对比（两边各查一次，结果应一致）
# 达梦 ：SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = 'ZING_DOCTOR_DB_PROD';
# MySQL：SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'zing_doctor_db_prod';
```

> 常见漂移原因：① 只改了达梦版忘了跑 `dm_to_mysql.py`；② 直接手改了 `sql/mysql/` 里的文件 —— 下次转换会被覆盖回去，改动凭空消失。

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

### 2026-09-18 · ARDS 采集映射配置表 + 记录主表日期扩列

新增「ARDS 数据映射」配置（参数设置页页签，37 项采集规则的通道/匹配方式/匹配值/优先级/单位换算，
替代硬编码关键字；缺失时解析器自动回退内置关键字，不影响采集）。

**涉及**

- 新建表：`ards_prone_config`（数据采集映射规则表，含唯一键 `uk_ards_prone_config`）
- 加列：`ards_prone_record` 增加 `admit_date`、`discharge_date`（依赖 21 建表，故必须排在其后）
- 执行脚本：`sql/22_ards_prone_config.sql`（CREATE 判存在、ADD COLUMN 判列存在、种子带
  `WHERE NOT EXISTS`，可重复执行）

**不执行的后果**：参数设置 →「ARDS 数据映射」页签点「一键从内置生成」500
「无效的表或视图名[ards_prone_config]」；采集本身不受影响（回退内置关键字）。

**自动应用**：`sql/22_ards_prone_config.sql` 已加入 `install.sh` 的 `INCREMENTAL_SQL` 与全量清单
`FULL_SQL` / `FULL_SQL_JDBC`。

**人工补执行（自动通道未命中时）**：执行 `sql/22_ards_prone_config.sql` 全文，然后核对：

```sql
-- 应返回 1
SELECT COUNT(*) FROM ALL_TABLES
 WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ARDS_PRONE_CONFIG';
```

### 2026-09-17 · ARDS 俯卧位通气治疗记录：新增 5 张表 + 页面注册 + 参数种子

新增「ARDS 俯卧位通气治疗记录」模块（列表页 + 填写页，37 项参数 × 时点矩阵，复用现有归档接口
`doc_code = ARDS_PRONE_REC`）。

**涉及**（只新建表与配置数据，不改既有表）

- 新建表：`ards_prone_record`（记录主表）、`ards_prone_timepoint`（时点）、`ards_prone_cell`（单元格值）、
  `ards_prone_cell_log`（更正留痕）、`ards_prone_tp_tpl`（科室时点模板）
- 页面注册：`zing_page_config` 新增 `ards-prone-list`、`ards-prone-record`（外链 `/entry/{pageCode}` 依赖）
- 参数：`zing_param_group` 新增分组 `ards_prone`；`zing_sys_param` 新增 5 个键 ——
  `ARDS_PRONE_APACHE2_SHOW`、`ARDS_PRONE_DOC_CODE`、`ARDS_PRONE_TPL_NO`、
  `ARDS_PRONE_ARCHIVE_ENABLED`、`ARDS_PRONE_RECORD_PREFIX`
- 执行脚本：`sql/21_ards_prone.sql`（DDL 用 PL/SQL 判存在，页面注册先 DELETE 再 INSERT，种子带
  `WHERE NOT EXISTS`，可重复执行）

**不执行的后果**：侧栏「ARDS 俯卧位记录」可点开但接口报「表或视图不存在」/ 500；外链进
`ards-prone-list` 报「未注册的页面」；参数设置里没有 ARDS 分组，归档 `doc_code` 取默认值。

**自动应用**：`sql/21_ards_prone.sql` 已加入 `install.sh` 的 `INCREMENTAL_SQL`。

**人工补执行（自动通道未命中时）**：执行 `sql/21_ards_prone.sql` 全文，然后核对：

```sql
-- 应返回 5 行
SELECT TABLE_NAME FROM USER_TABLES WHERE UPPER(TABLE_NAME) LIKE 'ARDS_PRONE%';

-- 应返回 5
SELECT COUNT(*) FROM "zing_doctor_db_prod"."zing_sys_param" WHERE "param_group" = 'ards_prone';

-- 应返回 2
SELECT COUNT(*) FROM "zing_doctor_db_prod"."zing_page_config"
 WHERE "page_code" IN ('ards-prone-list','ards-prone-record');
```

### 2026-09-17 · 患者明细默认列：事实层补「床号 / 诊断」（配置数据更新）

患者明细的默认列调整为
**姓名 / 床号 / 住院号 / 诊断 / 入科时间 / 出科时间 / 入分子 / 入分母**（宽度 70/60/130/120/180/180/60/60）。

其中「床号 / 诊断」不是指标表达式要用的列，而是**明细要显示的列**：明细 SQL 只会 select
事实层投影过的列，事实层不带出来，页面上就是整列空白。入科 / 出科时间多数事实层原本就有投影。

**涉及**（只更新配置数据，不加表、不加列）

- `quality_fact_def.select_cols`：给 6 个基于 `patient_info` 的事实层追加两项 ——
  `"pi.bed_code AS bed_code"`、`"CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"`。
  涉及事实层：`fact_patient_stay`、`fact_abx_culture`、`fact_dvt`、`fact_sepsis_bundle`、
  `fact_assessment`、`fact_ards`。
  （`fact_device` / `fact_score_resource` / `fact_staff` / `fact_bed` 不基于患者主表，这两列在它们的
  指标明细里不会出现，属预期。）

**为什么改表而不是只改 YAML**：生产 `config-source=db`，事实层定义的真源是 `quality_fact_def`，
`facts/*.yaml` 只在配置表为空时作为「出厂种子」导入一次。**只改 YAML 不生效**。
（YAML 也已同步修改，保证双源一致；全新库由种子导入直接得到新定义。）

**不执行的后果**：明细里「床号 / 诊断」两列始终空白 —— **不报错**，只是看不到数，
容易被当成数据没采到。入科 / 出科时间不受影响。

**影响面**：分子分母的计算结果完全不变（不动 where / derive）；下次访问明细时物化表按新定义重建
（配置版本变化即失效缓存，重建前先 DROP 再 CREATE TABLE AS），因此首次下钻会慢一点。

**自动应用**：`sql/20_quality_fact_patient_default_cols.sql` 已加入 `install.sh` 的 `INCREMENTAL_SQL`。

**人工补执行（自动通道未命中时）**

```sql
UPDATE "zing_doctor_db_prod"."quality_fact_def"
   SET "select_cols" = REPLACE(
           "select_cols",
           '"pi.out_depart_time AS out_depart_time"',
           '"pi.out_depart_time AS out_depart_time","pi.bed_code AS bed_code","CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content"')
 WHERE "fact_name" IN ('fact_patient_stay','fact_abx_culture','fact_dvt',
                       'fact_sepsis_bundle','fact_assessment','fact_ards')
   AND "select_cols" LIKE '%"pi.out_depart_time AS out_depart_time"%'
   AND "select_cols" NOT LIKE '%AS bed_code%';

-- 核对：应返回 6 行
SELECT "fact_name" FROM "zing_doctor_db_prod"."quality_fact_def"
 WHERE "fact_name" IN ('fact_patient_stay','fact_abx_culture','fact_dvt',
                       'fact_sepsis_bundle','fact_assessment','fact_ards')
   AND "select_cols" LIKE '%AS bed_code%';
```

> 注：锚点用 `out_depart_time` 而不是「数组最后一项」，因为各事实层末项不同
> （`fact_patient_stay` 末尾是 `out_hospital_time`），且库里的定义很可能比当前 YAML 旧
> ——按末尾匹配会**静默不生效**（不报错）。REPLACE 的匹配对象是 `select_cols` 原文
> （JSON 序列化后无空格）。若某事实层在配置页被改过 select 列表、已不含该锚点，
> 该行不会被更新，需在配置页手工补这两列。

### 2026-09-16 · 人工录入审计与达标方向（含存量数据回填）

本批次修一个会**静默丢数据**的缺陷，并启用指标达标判定。

**涉及**

- `quality_metric_result` 增加 `value_source` / `operator` / `manual_note` 三列
- `quality_count_rule` 增加 `target_direction` 一列（达标方向）
- **存量数据回填**：把已录入的人工值标记为 `value_source = 'MANUAL'`（脚本内 UPDATE，不可省略）

**背景（为什么必须回填）**：MANUAL 类指标（如「ICU 实际病死率」）的值由科室手工录入，
而重算的删除范围是「整个周期」，判据只有 period_type + period_start，不看这个值是谁写的 ——
每月 1 日凌晨的定时批算会把这些录入值连引擎结果一起删掉，再写回空值占位行。
现场表现是「上个月录的数这个月不见了」，而日志里没有任何异常，极难定位。

加 `value_source` 之后，重算的删除范围会显式排除人工录入行。**回填不能省**：
不回填，存量人工值会被判成 AUTO，升级后第一次重算照样把它们删掉 ——
等于本次修复对老数据完全无效。

**背景（达标方向）**：`quality_count_rule` 早有 target_value / warning_value，
但缺方向列，配了阈值也判不出达标与否：「镇痛评估率 ≥ 90%」与「VAP 发病率 ≤ 5‰」
的阈值本身看不出方向，而同一个数值在这两类指标上结论正好相反。
`target_direction` 取 `UP`（越高越好，默认）/ `DOWN`（越低越好）。
存量行统一置 `UP`；**「越低越好」的那几条（发病率、病死率类）需在页面上改一次**，
否则会被判反 —— 判反的后果是把不达标显示成达标。

**不执行的后果**

- 缺 `value_source` → 人工录入接口 500（`无效的列名[value_source]`），重算保护也不生效
- 缺 `target_direction` → 「质控指标」看板加载失败（`无效的列名[target_direction]`）

**自动应用**：`sql/18_quality_manual_audit.sql`、`sql/19_quality_target_direction.sql`
已加入 `install.sh` 的 `INCREMENTAL_SQL`，老库升级时自动执行（幂等）。
下面是自动通道不可用时的人工补执行版本，两者等价。

**部署时会自动校验（本次新增行为）**：`install.sh` 应用完增量后会复查这四列，
缺列则**中断部署**并打印补救命令。此前它对执行失败只 warn 后继续 —— 那正是「漏执行却无人察觉」
的成因：生产库因此静默缺列，直到人工录入保存 500（`无效的列名[value_source]`）才暴露。
若三条通道（本机 disql / 达梦容器 disql / JDK + DbInit）都没命中，会跳过校验并 warn，
此时请按下面的 ①② 手工执行，再重跑 `install.sh`。

**① 结果表：人工录入归属与审计**

```sql
ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "value_source" VARCHAR(16) DEFAULT 'AUTO';
ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "operator" VARCHAR(64);
ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "manual_note" VARCHAR(500);

COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."value_source" IS '值归属：AUTO=引擎计算 / MANUAL=人工录入（重算时受保护，不被覆盖）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."operator"     IS '人工录入的操作人（审计留痕；引擎写入的行此列为空）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."manual_note"  IS '人工录入备注（说明取数依据，便于事后复核）';

-- 存量回填（关键）：升级前录入的值没有归属标记，不回填就会被下次重算抹掉
UPDATE "zing_doctor_db_prod"."quality_metric_result"
SET "value_source" = 'MANUAL'
WHERE "calc_status" = 'MANUAL'
  AND "metric_value" IS NOT NULL
  AND ("value_source" IS NULL OR "value_source" <> 'MANUAL');
```

**② 规则表：达标方向**

```sql
ALTER TABLE "zing_doctor_db_prod"."quality_count_rule" ADD COLUMN "target_direction" VARCHAR(16) DEFAULT 'UP';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_count_rule"."target_direction" IS '达标方向：UP=越高越好 / DOWN=越低越好；决定「达标」是 ≥ 还是 ≤ 目标值';
UPDATE "zing_doctor_db_prod"."quality_count_rule" SET "target_direction" = 'UP' WHERE "target_direction" IS NULL;
```

**执行状态速查**

```sql
-- ① 结果表三列是否就位
SELECT UPPER(COLUMN_NAME) FROM USER_TAB_COLUMNS
 WHERE UPPER(TABLE_NAME) = 'QUALITY_METRIC_RESULT'
   AND UPPER(COLUMN_NAME) IN ('VALUE_SOURCE', 'OPERATOR', 'MANUAL_NOTE');

-- ② 规则表方向列是否就位
SELECT UPPER(COLUMN_NAME) FROM USER_TAB_COLUMNS
 WHERE UPPER(TABLE_NAME) = 'QUALITY_COUNT_RULE'
   AND UPPER(COLUMN_NAME) = 'TARGET_DIRECTION';

-- ③ 回填是否完整（必须返回 0）
SELECT COUNT(*) AS "未标记的人工录入行"
  FROM "zing_doctor_db_prod"."quality_metric_result"
 WHERE "calc_status" = 'MANUAL' AND "metric_value" IS NOT NULL
   AND ("value_source" IS NULL OR "value_source" <> 'MANUAL');
```

**升级后必做**

到「质控指标」看板，把「越低越好」的指标（发病率、病死率、重返率类）逐条打开目标值配置，
将达标方向改为「越低越好」并保存一次 —— 默认的 `UP` 对这几条是反的。

---

### 2026-09-16 · 质控指标规则支持本院自建

**涉及**

- `quality_count_rule` 增加 `origin` / `local_override` 两列（规则来源标记）
- `origin = LOCAL` 的规则为**本院自建**，ruleId 统一用 `LOCAL_` 前缀
- ICU 来源的规则在本院改过口径后自动置 `local_override = 1`，此后同步不再覆盖分子 / 分母 / 放大系数

**背景**：规则层（分子 ÷ 分母）此前只能从 ICU 侧 `zing_icu_db_prod.quality_count_rule` 同步而来 ——
本院想加一条自己关心的指标，必须等重症侧先建好。加这两列后，质控指标配置页新增「指标规则」页签，
可直接选原子项自建规则，且下次「同步指标规则」不会把它冲掉。

**不执行的后果**：配置页「指标规则」页签加载失败（无效的列名[origin]），新增规则同样报错。
不影响已有看板，以及「指标配置」「事实层」两个页签。

**自动应用**：`sql/17_quality_rule_local.sql` 已加入 `install.sh` 的 `INCREMENTAL_SQL`，老库升级时会自动执行（幂等）。
下面这段 SQL 是自动通道不可用时的人工补执行版本，两者等价。

**① 规则表加来源列**

```sql
ALTER TABLE "zing_doctor_db_prod"."quality_count_rule" ADD COLUMN "origin" VARCHAR(16) DEFAULT 'ICU';
ALTER TABLE "zing_doctor_db_prod"."quality_count_rule" ADD COLUMN "local_override" TINYINT DEFAULT 0;

-- 存量行全部来自 ICU 同步，显式回填
UPDATE "zing_doctor_db_prod"."quality_count_rule" SET "origin" = 'ICU' WHERE "origin" IS NULL;
UPDATE "zing_doctor_db_prod"."quality_count_rule" SET "local_override" = 0 WHERE "local_override" IS NULL;

COMMENT ON COLUMN "zing_doctor_db_prod"."quality_count_rule"."origin" IS '来源：ICU 由重症侧同步（可被源端刷新）/ LOCAL 本院自建（同步不覆盖）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_count_rule"."local_override" IS '1 = ICU 来源但本院改过口径，同步时不覆盖分子/分母/放大系数';
```

**执行状态速查**

```sql
SELECT UPPER(COLUMN_NAME) FROM USER_TAB_COLUMNS
 WHERE UPPER(TABLE_NAME) = 'QUALITY_COUNT_RULE'
   AND UPPER(COLUMN_NAME) IN ('ORIGIN', 'LOCAL_OVERRIDE');
```

**② 前置条件：配置真源必须是 db**

自建规则走 `/api/quality/config/**`（受写白名单保护），而这组接口在
`zing.quality.config-source = yaml` 时整体拒绝写入。所以还需要：

```yaml
zing:
  quality:
    config-source: db
    config-write-ip-whitelist: <质控科/信息科网段>
```

否则页面能看不能存，保存会提示「真源为 YAML，写接口不可用」。

---

### 2026-09-16 · 质控病死率改为引用型自动计算

**涉及**

- `quality_metric_def` 增加 `numerator_metric` / `denominator_metric` 两列（分子/分母引用另一条指标的编码）
- `quality_30`（ICU 实际病死率）→ 自动算 `quality_16 ÷ quality_403`
- `quality_31`（ICU 患者预计病死率）→ 自动算 `quality_15 ÷ quality_403`
- `quality_302` / `quality_303`（ICU 医师总数 / 护士总数）→ 改为人工填报

**不执行的后果**：质控配置页打开即 500（无效的列名 numerator_metric）；两条病死率仍是人工录入，不会自动出数。

**① 指标定义表加列**

```sql
ALTER TABLE "zing_doctor_db_prod"."quality_metric_def" ADD COLUMN "numerator_metric" VARCHAR(32);
ALTER TABLE "zing_doctor_db_prod"."quality_metric_def" ADD COLUMN "denominator_metric" VARCHAR(32);
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."numerator_metric"   IS '分子引用的指标编码（率类跨事实层用），如 quality_16；非空则不编译本指标 SQL';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."denominator_metric" IS '分母引用的指标编码，如 quality_403';
```

**② 人员数量改人工填报**

```sql
UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status" = 'MANUAL', "expr_version" = "expr_version" + 1, "update_time" = CURRENT_TIMESTAMP
 WHERE "index_code" IN ('quality_302', 'quality_303');
```

**③ 两条病死率改引用型**

```sql
-- ICU 实际病死率 = ICU死亡患者数 ÷ 同期患者总数（转出+出院）：人数比人数，系数 100
UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status" = 'IMPL', "value_type" = 'RATE', "scale" = 100,
       "numerator_metric" = 'quality_16', "denominator_metric" = 'quality_403',
       "dims" = '["depart_code"]',
       "expr_where" = NULL, "expr_numerator" = NULL, "expr_denominator_where" = NULL,
       "expr_version" = "expr_version" + 1, "update_time" = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_30';

-- ICU 患者预计病死率 = 预计病死率之和 ÷ 同期患者总数：分子已是百分数之和，系数只能是 1
UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status" = 'IMPL', "value_type" = 'RATE', "scale" = 1,
       "numerator_metric" = 'quality_15', "denominator_metric" = 'quality_403',
       "dims" = '["depart_code"]',
       "expr_where" = NULL, "expr_numerator" = NULL, "expr_denominator_where" = NULL,
       "expr_version" = "expr_version" + 1, "update_time" = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_31';
```

**④ 重算一次月度**：两条病死率由批次在被引用指标算完后组合，不重算则页面仍是旧的人工录入值。

> `scale` 是这一步最容易错的地方：`quality_30` 分子是人数 → 100；`quality_31` 分子本身已是百分数之和 → 必须是 `1`，错填 100 会放大一百倍，而看板上的数字看起来「像那么回事」。

**核对**

```sql
SELECT "index_code", "impl_status", "value_type", "scale", "numerator_metric", "denominator_metric"
  FROM "zing_doctor_db_prod"."quality_metric_def"
 WHERE "index_code" IN ('quality_30','quality_31','quality_302','quality_303')
 ORDER BY "index_code";
```

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

---

### 2026-09-16 · 直连登录鉴权 + 参数框架

> ✅ **本批次已由 `install.sh` 自动执行**：新库走全量初始化，老库（已初始化过）走增量升级
> （增量脚本清单：`12_archive.sql` → `13_auth.sql` → `14_param_framework.sql`，顺序不可调换，
> 14 要给 12 建的 `zing_sys_param` 加列），三个脚本都是幂等的，重跑不会有副作用。
> 只有当服务器既没有 `disql`、也没有达梦容器、也没有 `java` 时才会降级为手工执行，
> 届时 `install.sh` 会直接打印提示。下面内容保留为**手工兜底**与执行后自检用。

**涉及**

- 新建 `zing_sys_user`（直连登录账号表：账号密码登录，区别于外链免登录）
- `zing_sys_param` 增加 `param_type` / `options` / `default_value` / `required` / `regex` 五列
- 新建 `zing_param_group`（参数分组表）+ 预置 5 个分组（文书归档 / 外链集成 / 评分配置 / 质控配置 / 系统设置）
- 新增 3 个「外链工号自动注册」参数，**开关默认关**

**不执行的后果**：

- 不建 `zing_sys_user` → 直连打开页面登录报 500（表不存在）
- 不加那五列 → **参数设置页打开即 500**（`无效的列名[param_type]`）
- 不建 `zing_param_group` → 参数设置页左侧分组导航空白，参数列表取不到分组

**① 直连登录账号表**

可直接执行 `sql/13_auth.sql`（幂等：表已存在报「对象已存在」可忽略）。核心语句：

```sql
CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_sys_user" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."zing_sys_user" (
    "id"              BIGINT DEFAULT "zing_doctor_db_prod"."SEQ_zing_sys_user".NEXTVAL NOT NULL,
    "username"        VARCHAR(64)  NOT NULL,
    "real_name"       VARCHAR(64),
    "password_hash"   VARCHAR(200) NOT NULL,
    "status"          TINYINT      DEFAULT 1,
    "last_login_time" TIMESTAMP,
    "create_time"     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time"     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_sys_user" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_zing_sys_user_name" ON "zing_doctor_db_prod"."zing_sys_user" ("username");
```

> 管理员账号**不用手工插**：后端首次启动自动写入 `admin / zing@123`，已有同名账号不覆盖。
> 要改初始口令：启动时用环境变量 `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `AUTH_JWT_SECRET` 覆盖。

**② 参数框架**

可直接执行 `sql/14_param_framework.sql`（幂等：列名已存在 / 对象已存在均报错可忽略）。核心语句：

```sql
ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "param_type"    VARCHAR(20)   DEFAULT 'text';
ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "options"       VARCHAR(1000);
ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "default_value" VARCHAR(1000);
ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "required"      TINYINT       DEFAULT 0;
ALTER TABLE "zing_doctor_db_prod"."zing_sys_param" ADD COLUMN "regex"         VARCHAR(200);

CREATE SEQUENCE "zing_doctor_db_prod"."SEQ_zing_param_group" START WITH 1 INCREMENT BY 1;
CREATE TABLE "zing_doctor_db_prod"."zing_param_group" (
    "id"          BIGINT       DEFAULT "zing_doctor_db_prod"."SEQ_zing_param_group".NEXTVAL NOT NULL,
    "group_code"  VARCHAR(64)  NOT NULL,
    "group_name"  VARCHAR(128) NOT NULL,
    "sort_no"     INT          DEFAULT 0,
    "icon"        VARCHAR(64),
    "remark"      VARCHAR(500),
    "status"      TINYINT      DEFAULT 1,
    "create_time" TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT "pk_zing_param_group" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_zing_param_group_code" ON "zing_doctor_db_prod"."zing_param_group" ("group_code");
```

**执行后自检**：

```sql
SELECT "group_code", "group_name" FROM "zing_doctor_db_prod"."zing_param_group" ORDER BY "sort_no";
SELECT "param_key", "param_value", "param_type"
  FROM "zing_doctor_db_prod"."zing_sys_param" ORDER BY "param_group", "sort_no";
```

**执行后配置**：进「参数设置」→「外链集成」分组，`AUTO_REGISTER_ENABLED` 默认 **关闭**（外链 token 是固定明文，开启后任何持有外链者都能注册账号），需要时再打开。

---

### 2026-09-16 · 质控患者明细字段可配

> ⚠️ **本批次现场漏执行过一次**：打开「质控指标配置」直接 500，报 `无效的列名[patient_fields]`。
> `install.sh` 的老库增量通道会自动执行 `sql/15_quality_patient_fields.sql`，
> 但**只替换 jar + 重启、没有跑 `install.sh`** 时不会生效。换包后请务必 `cd 部署目录 && bash install.sh` 跑一次。

**涉及**

- `quality_metric_def` 增加 `patient_fields` 列（患者明细补充字段，JSON 数组）

**不执行的后果**

- 「质控指标配置」页面打开即 500：`无效的列名[patient_fields]`
- 看板、月度汇总读同一套实体，同样会报错

**① 加列**

```sql
ALTER TABLE "zing_doctor_db_prod"."quality_metric_def" ADD COLUMN "patient_fields" VARCHAR(2000);

COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_def"."patient_fields" IS '患者明细补充字段 JSON 数组，如 [{"key":"gender","label":"性别"}]；只能引用事实层已投影列，空表示只用默认列';
```

> 列已存在时会报「列已存在」类错误，**可忽略**。也可直接执行 `sql/15_quality_patient_fields.sql`（幂等，脚本会先查列是否存在）。

**执行后自检**

```sql
SELECT UPPER(COLUMN_NAME) FROM USER_TAB_COLUMNS
 WHERE UPPER(TABLE_NAME) = 'QUALITY_METRIC_DEF' AND UPPER(COLUMN_NAME) = 'PATIENT_FIELDS';
-- 返回 1 行即已生效；无需重启容器，刷新页面即可
```
