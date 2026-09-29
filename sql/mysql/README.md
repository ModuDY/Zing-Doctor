# MySQL / MariaDB 建表脚本

本目录是 `sql/` 下达梦 DM8 脚本的 **MySQL 8.x / MariaDB 10.5** 等价版本，由
`tools/dm_to_mysql.py` 自动转换 + 人工校验生成。**达梦原版脚本（`sql/*.sql`）保持不动**，
三套脚本并存，按部署的数据库类型选用。

> 适用数据库版本：**MySQL 8.0+** 或 **MariaDB 10.5+**（医院 ICU 库本身即 MySQL/MariaDB 系）。
> 不支持 MySQL 5.7（`CREATE USER IF NOT EXISTS` 需 MySQL 8.0.11+；5.7 已 EOL）。
>
> 一键安装见仓库根目录 **`install-mariadb-debian.sh`**，自动完成建库导表、部署 jar（systemd）、
> nginx 前端。它会 `SELECT VERSION()` 自动识别 MySQL / MariaDB 并选择对应 Spring profile。
>
> **数据库已在 Docker 中运行（宿主机映射端口，如 23306、root/zing@123）**——脚本不会再装数据库，
> 只装客户端 + JRE + nginx，然后通过 TCP 连容器库：
>
> ```bash
> sudo DB_PORT=23306 DB_ADMIN_PASSWORD='zing@123' \
>      APP_DB_PASSWORD='Zing@2026' ./install-mariadb-debian.sh
> ```
>
> 应用与数据库同机时 `DB_HOST` 默认 127.0.0.1（走映射端口）；异机时加 `DB_HOST=<库宿主IP>`。
> 干净 Debian、让脚本全自动 apt 安装本机 MariaDB 时，直接 `sudo ./install-mariadb-debian.sh`。
>
> **连接参数与安装目录都写在包内 `conf/db.conf`**（脚本自动读取，命令行环境变量可覆盖）：
> 库地址/端口/账号、`DB_TYPE`（应用机没有客户端时显式指定 mariadb / mysql）、
> `DB_CLI`（借容器内客户端）、以及 `APP_HOME`。
> **应用目录默认就是包所在目录**（解压在哪就在哪跑，升级直接解压覆盖；要固定到 `/opt/zing-doctor` 就填 `APP_HOME`）。

## 升级流程（MySQL / MariaDB 环境）

与达梦侧（`install.sh`）**不是同一套脚本**，判断逻辑也不同：

| 场景 | 动作 | 命令 |
|---|---|---|
| **含表 / 字段变更**（推荐默认做法） | 重跑安装脚本：`MAIN_SQL` 里的脚本**每次全量重跑**，全部幂等 | `sudo ./install-mariadb-debian.sh` |
| **只换程序**（jar / 前端，无结构变更） | 覆盖产物后重启服务 | `cp -r <新包>/app/. <运行目录>/app/` → `sudo systemctl restart zing-doctor` |
| **只改连接参数**（库地址 / 端口 / 账号） | 重写运行环境并重启：**不动数据库、不导 SQL、不更新前端** | 改 `conf/db.conf` → `sudo ./install-mariadb-debian.sh --config-only` |
| **要补 ICU 库索引**（需 DBA 知情） | 对医院生产库有变更，默认关闭 | `SETUP_ICU_INDEX=yes sudo -E ./install-mariadb-debian.sh` |

> 脚本每次都会建一遍库名（`CREATE DATABASE IF NOT EXISTS`，同名库不重建）并重跑全部表 / 种子脚本，
> **不会因重复执行而丢数据** —— 幂等由 `00b_idempotent_helpers_doctor.sql` 的存储过程、`IF NOT EXISTS`
> 与种子里的 `WHERE NOT EXISTS` 保证。唯一不支持的是 MySQL 5.7（`CREATE USER IF NOT EXISTS` 需 8.0.11+，5.7 已 EOL）。

**与达梦侧的关键差别**：达梦会先探测 `zing_page_config` 是否已存在，老库**只跑增量清单**（`09`~`23`），
所以漏登记 `INCREMENTAL_SQL` 会「静默不补表」；本侧只有一个清单且每次全跑，漏登记表现为新装环境直接缺表 ——
出错更早、也更好查。两边都漏登记才会有运维事故，因此新增脚本务必按下面的规则同步。

## 新增脚本时的反向同步规则（重要）

**本目录是自动生成产物，不要在这里手工写 SQL。** 达梦版 `sql/NN_xxx.sql` 才是真源：

```bash
# 1) 改达梦版 sql/NN_xxx.sql  →  2) 重新转换  →  3) 人工复核 + 真机导入验证
python tools/dm_to_mysql.py
```

转换产务必提交，之后还要：

- 登记到 `install-mariadb-debian.sh` 的 `MAIN_SQL` 数组（顺序按依赖）；
- 登记到 `install.sh` 的 `FULL_SQL` / `FULL_SQL_JDBC` / `INCREMENTAL_SQL` 三个达梦清单；
- 完整步骤见根目录 [DATABASE-CHANGES.md](../../DATABASE-CHANGES.md)「双数据库：脚本归属与新增脚本四步规则」。

## 兼容性说明

脚本只使用 MySQL 8 与 MariaDB 10.5 共有的通用语法，已逐项核实：

| 特性 | MariaDB 10.5 | 说明 |
|---|---|---|
| `CREATE DATABASE/TABLE IF NOT EXISTS` | ✅ | 幂等建库建表 |
| `CREATE PROCEDURE` + `DELIMITER` | ✅ | 幂等加列/建索引 |
| `PREPARE st FROM @var / EXECUTE / DEALLOCATE` | ✅ | 存储过程内动态 DDL |
| `information_schema.COLUMNS/STATISTICS` | ✅ | 对象存在性判断 |
| `SELECT ... FROM DUAL WHERE NOT EXISTS` | ✅ | 种子数据幂等插入 |
| `utf8mb4 / LONGTEXT / LONGBLOB / DECIMAL` | ✅ | 类型映射 |
| `CREATE USER IF NOT EXISTS / ALTER USER` | ✅ | 安装脚本建账号 |

已知差异（已在脚本里等价处理）：

| 达梦写法 | MySQL/MariaDB 等价做法 |
|---|---|
| 索引列用表达式（部分唯一索引）：`UNIQUE INDEX (... , CASE WHEN status = 1 THEN 1 ELSE NULL END)` | 不支持表达式索引 → 先加 `STORED` 生成列（`TINYINT AS (CASE WHEN status = 1 THEN 1 ELSE NULL END) STORED`），再对「普通列 + 生成列」建唯一索引；生成列为 NULL 的行同样不参与唯一性判断（见 `21_ards_prone.sql` 的 `uk_ards_prone_cell`） |
| 列默认值 `SEQ_xxx.NEXTVAL` | `AUTO_INCREMENT`（应用主键仍是雪花算法显式赋值） |

后端通过 `SPRING_PROFILES_ACTIVE=mariadb` 切换：驱动 `org.mariadb.jdbc.Driver`、
连接串 `jdbc:mariadb://`、分页方言 `DbType.MARIADB`，并复用 `MySqlDialectInterceptor`
把残留达梦方言（ROWNUM/SYSDATE/`||`/CAST AS VARCHAR 等）在执行前翻译成 MySQL 方言；
连接串带 `sessionVariables=sql_mode='ANSI_QUOTES'` 兼容双引号标识符。

## 文件清单

| 文件 | 作用 |
|---|---|
| `00_init_user.sql` | 建两个数据库（doctor 主库 / icu 只读库） |
| `00b_idempotent_helpers_doctor.sql` | 在主库建幂等存储过程（先执行） |
| `00c_idempotent_helpers_icu.sql` | 在 ICU 库建幂等存储过程（主库/ICU 异机时用） |
| `01_schema.sql` ~ `23_*.sql` | 表结构、索引、种子数据（按序号执行） |
| `24_fix_id_auto_increment.sql` | 修复「已建库」中 id 列缺 `AUTO_INCREMENT` 的表（幂等，全新库为空操作） |
| `install-all.sql` | **打包时自动合成**的一次性初始化脚本（上述文件按序拼接）。应用与库分两台机器、库侧由 DBA 维护时，把这一份拷过去执行一次即可：`mysql -uroot -p < install-all.sql` |
| `03_icu_indexes.sql` | ICU 只读库性能索引（加在医院现有库上，需 DBA 评估） |

## 手工执行顺序（不用一键脚本时）

```bash
# 客户端：Debian 安装 mariadb-client（提供 mysql/mariadb 命令）
CLI="mariadb -h 127.0.0.1 -uroot -p"

# 1) 建库
$CLI < 00_init_user.sql
# 2) 主库幂等存储过程
$CLI zing_doctor_db_prod < 00b_idempotent_helpers_doctor.sql
# 2.5) 历史库修复：给缺 AUTO_INCREMENT 的 id 列补上（新建库为空操作；
#      已建过的库**必须**在种子脚本前执行，否则 02_seed 会报 Field 'id' doesn't have a default value）
$CLI zing_doctor_db_prod < 24_fix_id_auto_increment.sql
# 3) 主库结构 + 种子（顺序不可乱）
for f in 01_schema.sql 02_seed.sql 05_apache2_pdf.sql 06_abx_drug_dict.sql \
         07_sofa.sql 08_sofa_p1.sql 09_quality.sql 10_quality_config.sql \
         11_quality_count_rule.sql 12_archive.sql 13_auth.sql 14_param_framework.sql \
         15_quality_patient_fields.sql 16_quality_fatality_ref.sql \
         17_quality_rule_local.sql 18_quality_manual_audit.sql \
         19_quality_target_direction.sql 20_quality_fact_patient_default_cols.sql \
         21_ards_prone.sql 22_ards_prone_config.sql 23_ards_prone_sign_work_no.sql; do
  echo ">>> $f"; $CLI zing_doctor_db_prod < "$f"
done
# 4) ICU 库索引（可选，需 DBA 知情）
$CLI zing_icu_db_prod < 00c_idempotent_helpers_icu.sql
$CLI zing_icu_db_prod < 03_icu_indexes.sql
```

所有脚本幂等，可重复执行。存储过程脚本含 `DELIMITER`，必须用 `mysql`/`mariadb`
命令行客户端（或 Navicat 等能识别 DELIMITER 的工具）执行，不要用 JDBC 直接 source。

## 关于 MariaDB 版本

- Debian 11（bullseye）官方 apt 源自带的就是 **MariaDB 10.5 系列**最新安全补丁版，
  与 10.5.6 完全兼容、且修复了 10.5.6 之后的安全问题，**不建议锁定到精确的 10.5.6**。
- Debian 12 / Ubuntu 自带版本更新（10.11 / 11.x），本系统 SQL 为通用语法，可直接运行。
- 如确需精确 10.5.6，需自行配置 MariaDB 官方归档 apt 源。

## 与达梦版的主要差异

| 项 | 达梦 DM8 | MySQL/MariaDB |
|---|---|---|
| 主键 | `id` 默认 `SEQ_xxx.NEXTVAL`（省略时取序列值） | `id` 用 `AUTO_INCREMENT` 兜底；应用写入时是雪花算法显式赋值 |
| 标识符 | 双引号保小写 | 反引号；连接串 ANSI_QUOTES 兼容双引号 |
| 幂等 DDL | PL/SQL 匿名块 + ALL_TABLES | IF NOT EXISTS + 辅助存储过程 |
| 类型 | VARCHAR2/CLOB/BLOB/NUMBER | VARCHAR/LONGTEXT/LONGBLOB/DECIMAL |
| 注释 | COMMENT ON 独立语句 | 合并进 CREATE TABLE / ADD COLUMN |
| 表/库 | schema（模式） | database（库），USE 切换 |

## 重新生成

达梦脚本变更后重新转换：

```bash
python tools/dm_to_mysql.py
```

转换仅做机械方言映射，生成后需对变更文件人工复核，并在真实 MariaDB/MySQL 实例导入验证。
