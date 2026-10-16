# 医生决策系统

面向 ICU 临床信息系统的独立医生决策系统，由 ICU 系统通过**外链免登录**方式嵌入打开，围绕抗菌药物管理（ASP）与危重症临床决策提供多维度支持。

- Spring Boot 2.7 + Java 8 后端，MyBatis-Plus 双数据源（主库 `zing_doctor_db_prod` / 只读 `zing_icu_db_prod`）
- 数据库支持**达梦 DM8**（默认，信创）与 **MySQL 8+ / MariaDB 10.5+**：两套 SQL 脚本（`sql/` / `sql/mysql/`）配两套安装脚本（`install.sh` / `install-mariadb-debian.sh`），新增脚本需两边同步，规则见 [DATABASE-CHANGES.md](DATABASE-CHANGES.md)
- 外链免登录机制：外部系统（ICU）通过 `GET /entry/{pageCode}?expire=&sign=&业务参数`（签名模式）或 `GET /entry/{pageCode}?extToken=&业务参数`（ICU 明文模式）打开任意已注册页面
- Vue3 前端（history 路由，每页面可外链直达）、Docker / 内网直连部署

## 功能矩阵

| 维度 | 功能 | pageCode | 前端路由 |
|---|---|---|---|
| 第一维度 | 疑似感染患者列表 | `abx-patient-list` | `/page/abx-patient-list` |
| 第一维度 | 经验性抗感染治疗决策（规则引擎推荐 + 医生决策留痕） | `abx-decision` | `/page/abx-decision` |
| 第二维度 | PK/PD 抗菌药物剂量优化（肾功能/体重/低蛋白/CRRT，TDM 目标） | `abx-pkpd` | `/page/abx-pkpd` |
| 第二维度 | PK/PD 药物知识库配置（抗菌药物 PK/PD 参数界面化配置，优先于内置枚举） | `abx-pkpd-config` | `/page/abx-pkpd-config` |
| 第三维度 | 抗菌药物使用强度（DDD）分析 | `abx-ddd` | `/page/abx-ddd` |
| 第三维度 | 抗菌药物使用患者明细（由总览页内部跳转） | — | `/page/abx-ddd-patients` |
| 第三维度 | DDD 值配置管理 | `abx-ddd-config` | `/page/abx-ddd-config` |
| 第四维度 | 细菌培养检出监测（含菌株排名/标本分布/高风险预警） | `abx-mdro` | `/page/abx-mdro` |
| 第四维度 | 细菌培养患者明细（由总览页内部跳转） | — | `/page/abx-mdro-patients` |
| 第四维度 | 细菌分类配置管理 | `abx-mdro-config` | `/page/abx-mdro-config` |
| 第五维度 | 医生交班览表（按完整全天班次汇总） | `handover-board` | `/page/handover-board` |
| 辅助 | 脓毒症休克集束化治疗（1H/3H/6H 自动判定） | `sepsis-bundle` | `/page/sepsis-bundle` |
| 辅助 | 抗菌药物识别词库配置 | `abx-word-config` | `/page/abx-word-config` |
| 辅助 | APACHE II 评分总览 | `apache2-overview` | `/page/apache2-overview` |
| 辅助 | APACHE II 评分评估（含 PDF 文书） | `apache2-score` | `/page/apache2-score` |
| 辅助 | ARDS 监测（柏林定义分级、肺保护性通气） | `ards-monitor` | `/page/ards-monitor` |
| 辅助 | ARDS 俯卧位通气治疗记录（37 项 × 时点矩阵，列表 + 归档） | `ards-prone-list` | `/page/ards-prone-list` |
| 辅助 | ARDS 俯卧位记录填写（由列表页内部跳转，也可外链直达） | `ards-prone-record` | `/page/ards-prone-record` |
| 辅助 | ARDS 数据采集映射配置（常规入口：**系统设置 → 参数设置 →「ARDS 数据映射」页签**） | `ards-prone-config` | `/page/ards-prone-config` |
| 辅助 | 患者出科统计（支持 CSV 导出） | `discharge-stats` | `/page/discharge-stats` |
| 辅助 | SOFA 评分（序贯器官衰竭评估，6 项 0~24 分；含文书 PDF、指标趋势） | `sofa-score` | `/page/sofa-score` |
| 辅助 | SOFA 评分总览（总分分布 + ΔSOFA 恶化预警） | `sofa-overview` | `/page/sofa-overview` |
| 辅助 | SOFA 配置管理（取数项映射 / 升压药阈值 / 换算系数 / 默认体重） | `sofa-config` | `/page/sofa-config` |
| 辅助 | 患者工作台（ICU 在科患者检索、床位概览与快捷入口） | `patient-workbench` | `/page/patient-workbench` |
| 辅助 | 患者诊疗摘要（基本信息、生命支持、评分、感染指标、当前抗菌药、待办） | `patient-summary` | `/page/patient-summary` |
| 辅助 | 历史患者检索（历史在科 / 已出科） | `history-patients` | `/page/history-patients` |
| 辅助 | 生命体征趋势（多指标同时间轴叠加，时间窗 6h / 24h / 3天 / 7天） | `vital-signs` | `/page/vital-signs` |
| 辅助 | 体液平衡分析（入量 / 出量 / 净平衡按时间窗汇总与明细） | `fluid-balance` | `/page/fluid-balance` |
| 辅助 | 肝功能趋势（ALT / AST / 胆红素 / 白蛋白等按参考范围展示） | `lab-liver` | `/page/lab-liver` |
| 辅助 | 肾功能与电解质趋势（肌酐 / 尿素 + 血气与生化两套电解质命名） | `lab-renal` | `/page/lab-renal` |
| 辅助 | 凝血功能趋势（PLT / PT / INR / APTT / Fib / D-二聚体） | `lab-coag` | `/page/lab-coag` |
| 辅助 | 镇痛镇静监测（RASS / CPOT 趋势与用药） | `sedation-monitor` | `/page/sedation-monitor` |
| 辅助 | 神经功能监测（神经功能评分趋势） | `neuro-monitor` | `/page/neuro-monitor` |
| 辅助 | 营养支持分析（热量 / 蛋白目标达成与营养制剂） | `nutrition-monitor` | `/page/nutrition-monitor` |
| 辅助 | 导管统计分析（CVC / 导尿 / 气道三管置管、拔管、非计划拔管与留置日） | `tube-stats` | `/page/tube-stats` |
| 辅助 | 导管相关感染确诊（VAP / CRBSI / CAUTI 医生确诊录入，质控指标源） | `device-infection` | `/page/device-infection` |
| 辅助 | 科室驾驶舱（患者负荷、器官支持、床位与设备、评分分布、本月质控） | `department-cockpit` | `/page/department-cockpit` |
| 辅助 | 血流动力学与心功能（CVP / CO / CI / SVR / ScvO₂ / LVEF 趋势，含 ADL 评分） | `hemodynamics` | `/page/hemodynamics` |
| 辅助 | 院感分析（单患者感染指标、培养与药敏、抗菌药使用时间轴） | `infection-watch` | `/page/infection-watch` |
| 辅助 | 床旁超声教学（标准切面与测量教学材料） | `echo-teaching` | `/page/echo-teaching` |
| 质控 | ARDS 全院筛查（按柏林标准筛出未识别 ARDS，挂质控/管理菜单组） | `ards-screen` | `/page/ards-screen` |
| 系统 | 参数设置（归档接口地址、归档目录等系统参数，含「ARDS 数据映射」页签入口） | `param-config` | `/page/param-config` |
| 系统 | 交付自检（医生库 / ICU 数据源 / 复评表 / 当前运行包构建信息） | `system-check` | `/page/system-check` |
| 质控 | 质控指标看板（127 条指标按域分组，含口径血缘下钻、覆盖率报告、事实层与计算批次） | `quality-board` | `/page/quality-board` |
| 质控 | 质控月度汇总（1-12 月横排，含季度/全年合计/月均/极值月与 Excel 导出） | `quality-monthly` | `/page/quality-monthly` |
| 质控 | 患者月度质控指标（单患者 1-12 月质控明细） | `quality-patient-monthly` | `/page/quality-patient-monthly` |
| 质控 | 质控指标配置（口径编辑、事实层配置、变更历史与回滚、批量导入导出，保存即热生效） | `quality-config` | `/page/quality-config` |

> 共 45 个已注册外链页面（`sys_page_config` 内共 47 条，另 2 条是下面提到的明细页）；
> `abx-ddd-patients` / `abx-mdro-patients` 为总览页内部跳转的明细页，不经 `/entry` 外链进入。
>
> `ards-prone-config` 已注册、可外链直达，但常规入口是**系统设置 → 参数设置 →「ARDS 数据映射」页签**：映射是「37 项 × 多通道」的规则表（通道/匹配方式/匹配值/优先级/时间窗/单位换算），塞进键值型参数只能编辑一坨 JSON，无法表格浏览、候选选择与试采核对，故单独建表 `config_prone_item`，只把入口合进参数设置。
>
> 质控四个页面（看板 / 月度汇总 / 患者月度 / 指标配置）由 YAML 配置驱动（`src/main/resources/quality/`），指标增删改只改配置并调一次 `POST /api/quality/sync-index`，不改接口与前端。详见 [质控指标中台 · 产品设计](docs/06-质控/15-质控指标中台-产品设计.md)。

## 快速开始

```bash
# 1. 初始化达梦数据库（推荐直接跑 install.sh，自动建模式 zing_doctor_db_prod 并按依赖顺序建表/补列）
#    手动方式：SYSDBA 执行 00_init_user.sql 建模式，再执行 01/02 建表与种子数据；
#    03~52 为分模块增量脚本（按编号顺序执行），逐条说明与漏执行后果见 DATABASE-CHANGES.md
disql SYSDBA/Sa_20250815@100.120.1.102:14236
SQL> start /opt/zing-doctor/sql/00_init_user.sql
SQL> start /opt/zing-doctor/sql/01_schema.sql
SQL> start /opt/zing-doctor/sql/02_seed.sql

# 2. 本地启动后端（修改 application.yml 数据源为达梦连接）
mvn spring-boot:run

# 3. 本地启动前端（已配置 /api 与 /entry 代理到 8081）
cd frontend && npm install && npm run dev

# 4. 生成一条外链验证免登录进入
curl "http://localhost:8081/api/external/token?pageCode=abx-decision&baseUrl=http://localhost:5173&patientId=P1001"
```

MySQL / MariaDB 环境不用手写 SQL —— 配好连接参数后一键初始化并部署：

```bash
# 连接写在 conf/db.conf（DB_HOST/DB_PORT/DB_ADMIN_*/APP_DB_*），也可环境变量覆盖
sudo ./install-mariadb-debian.sh                  # 完整安装 / 升级（每次重跑全部幂等脚本，不丢数据）
sudo ./install-mariadb-debian.sh --config-only    # 只同步连接配置并重启后端（不动数据库、不导 SQL）
```

> 两套脚本的**升级流程不同**（达梦：`install.sh` 先探测老库、只跑增量清单；MySQL/MariaDB：每次全量重跑，
> `--config-only` 只改配置）。对照表见 [安装部署手册 §4.1 / §4.2](docs/02-部署运维/04-安装部署手册.md)，
> 新增 SQL 的两边同步规则见 [DATABASE-CHANGES.md](DATABASE-CHANGES.md)「双数据库…四步规则」。

ICU 明文模式联调无需签发，直接用配置的静态 `extToken` 访问：

```
http://<host>:2001/entry/abx-decision?extToken=zing-icu-link-token-2026&inHospitalNo=<住院号>
```

## 质量保障（测试 / 检查 / CI）

本地与 CI 共用同一个脚本，不绑定具体 CI 平台：

```bash
bash tools/ci.sh                      # 后端测试 + 前端 lint/漏洞扫描/构建
bash tools/ci.sh --backend            # 只跑后端
bash tools/ci.sh --frontend           # 只跑前端
CI_SECURITY_SCAN=1 bash tools/ci.sh   # 额外跑后端依赖漏洞扫描（需联网更新 NVD 库）
```

| 校验项 | 命令 | 通过门槛 |
|---|---|---|
| 后端单元测试 | `mvn test` | 全绿（当前 307 个用例） |
| 前端代码检查 | `cd frontend && npm run lint` | error 为 0（warning 不阻塞） |
| 前端依赖漏洞 | `cd frontend && npm run audit:ci` | 生产依赖不允许 high 及以上 |
| 后端依赖漏洞 | `mvn -Psecurity-check verify` | CVSS ≥ 7 中断（需联网） |
| 前端格式化 | `cd frontend && npm run format` | 提交前本地执行 |

- 测试位于 `src/test/java`，覆盖 SOFA / Apache II 评分、质控 DSL 与配置校验，以及 DDD 使用强度解析、PK/PD 肾功能计算、MDRO 判定口径、集束化休克判定与液体量等核心业务计算。
- 流水线定义见 `.github/workflows/ci.yml`；换 GitLab CI / Jenkins 时把命令照搬即可，无需改 `tools/ci.sh`。
- 前端 `xlsx` 依赖取自仓库内 `frontend/vendor/xlsx-0.20.3.tgz`（SheetJS 已迁出 npm，官方源在内网不可达，故随仓库固化）；**升级时替换该 tgz 并同步 `package.json` 里的路径**。

## 交付包命名规范

统一格式：`zing-doctor-deploy-v<版本>-<形态>.zip`，`<版本>` 取自 `pom.xml` 的 `<version>`。不带打包日期——版本即发布标识，所以**每打一次包自动升一个修订号**（`1.0.1` → `1.0.2`，详见 `docs/02-部署运维/03-部署说明.md`）。打包时间见包内 `build-info.properties`。

| 形态 | 内容 | 适用场景 | 体积参考 |
|---|---|---|---|
| `full` | 含源码，可在服务器重建 | 归档 / 需重新构建 | ~62 MB |
| `runtime` | 纯运行时，含离线 `docker-compose` 二进制 | 服务器无 `docker compose` 且无外网 | ~62 MB |
| `runtime-slim` | 纯运行时且去掉 compose 二进制 | 服务器自带 `docker compose`（推荐） | ~29 MB |

示例：`zing-doctor-1.0.0-runtime-slim-20260911.tar.gz`。完整规则与打包步骤见 [部署说明 §7](docs/02-部署运维/03-部署说明.md)。

打包命令：`tools/build-delivery.ps1`（`-Lite` / `-Build` / `-KeepDocs` / `-Sanitize`）。默认按**白名单**组装：后端/前端源码、`DbInit.java`、产品设计文档、`docs/ards-prone` 原型、设计稿、sourcemap 一律不进包，打完跑一次出库自检（命中 `.java` / `.map` / `*.vue` / `pom.xml` / `src/` 等即中止）。**外发给院方前加 `-Sanitize`**，把 `docker-compose.yml` 与 jar 内 `application.yml` 的出厂口令/密钥置为 `CHANGE_ME`（部署方填真实口令后才能启动）。

## 文档

文档已按主题分目录放在 `docs/`，**完整索引见 [docs/README.md](docs/README.md)**。下面是按用途挑的几份：

### 先看这几份

| 文档 | 什么时候看 |
|---|---|
| [安装部署手册（达梦，实操）](docs/02-部署运维/04-安装部署手册.md) | 要部署 / 升级，操作步骤 |
| [部署说明](docs/02-部署运维/03-部署说明.md) | 打包规则、三种交付形态、离线包 |
| [架构设计](docs/01-架构与规范/01-架构设计.md) | 想了解模块划分与数据来源 |
| [外链传参规范](docs/01-架构与规范/02-外链传参规范.md) | 要做 ICU 系统对接（外链免登录 + 签名） |
| [数据库变更清单](DATABASE-CHANGES.md) | 升级前必读：新增表/加列按批次记录，含**漏执行的后果**与人工补执行 SQL；达梦 / MySQL 双库的脚本归属与「新增脚本四步规则」也在其中 |

### 产品设计（按业务主题）

- **评分**：[SOFA](docs/03-评分/06-SOFA评分-产品设计.md) ｜ [APACHE II](docs/03-评分/13-APACHEII评分-产品设计.md)
- **抗感染四维度**：[经验性抗感染决策](docs/04-抗感染/07-第一维度-经验性抗感染决策-产品设计.md) ｜ [PK/PD 剂量优化](docs/04-抗感染/05-第二维度-PKPD剂量优化-产品设计.md) ｜ [DDD 使用强度](docs/04-抗感染/08-抗菌药物使用强度DDD-产品设计.md) ｜ [培养检出与 MDRO](docs/04-抗感染/09-细菌培养检出监测MDRO-产品设计.md) ｜ [抗菌药词库配置](docs/04-抗感染/14-抗菌药物识别词库配置-产品设计.md)
- **临床监测**：[ARDS 监测](docs/05-临床监测/10-ARDS监测-产品设计.md) ｜ [ARDS 俯卧位记录](docs/05-临床监测/17-ARDS俯卧位通气治疗记录-产品设计.md) ｜ [脓毒症集束化](docs/05-临床监测/11-脓毒症休克集束化治疗-产品设计.md) ｜ [院感与脓毒症筛查](docs/05-临床监测/22-院感监测（VAP-CLABSI-CAUTI）与脓毒症筛查口径方案.md) ｜ [营养支持分析](docs/05-临床监测/24-营养支持分析-数据盘点与实施方案.md) ｜ [镇痛镇静与急性脑损伤](docs/05-临床监测/20-镇痛镇静与急性脑损伤监测分析模块-实施方案.md)
- **质控**：[质控指标中台](docs/06-质控/15-质控指标中台-产品设计.md) ｜ [质控指标可视化配置](docs/06-质控/16-质控指标可视化配置-改造方案.md)
- **工作台与其他**：[医生交班览表与出科统计](docs/07-工作台与其他/12-医生交班览表与出科统计-产品设计.md) ｜ [科室驾驶舱](docs/07-工作台与其他/23-科室驾驶舱-实施方案.md)

### 数据源结构（写查询前必看）

- [ICU 数据库结构与样例](docs/01-架构与规范/18-ICU数据库结构与样例.md)：只读源库 `zing_icu_db_prod` 的表 / 字段 / 常用查询，**以及真实数据体检结论**（哪些列全是 NULL、哪些口径在这台库上取不到数）
- [达梦数据库结构与样例](docs/01-架构与规范/19-达梦数据库结构与样例.md)：达梦 DM8 侧医生库的表清单与行数、两台库对照、达梦方言写法

> 20~26 号是后补的方案文档（镇痛镇静 / 院感 / 营养 / 驾驶舱 / 数据核查等），完整清单与阅读顺序见 [docs/README.md](docs/README.md)。

一键部署：

- **达梦**：解压后执行 `bash install.sh`（自动初始化达梦 + 老库自动套用增量脚本 + 构建启动，支持内网离线）
- **MySQL / MariaDB**：配好 `conf/db.conf` 后执行 `sudo ./install-mariadb-debian.sh`（升级流程与达梦不同，对照见 [安装部署手册 §4.1 / §4.2](docs/02-部署运维/04-安装部署手册.md)，脚本说明见 [sql/mysql/README.md](sql/mysql/README.md))

## 目录结构

```
zing-doctor/
├── src/main/java/com/zing/doctor/
│   ├── common/                  统一返回 Result / 业务异常 / 全局异常处理
│   ├── config/                  Web 拦截器注册、MyBatis-Plus 配置
│   ├── external/                外链免登录：签名工具/服务、/entry 入口、API 拦截器、令牌签发
│   ├── icu/                     ICU 数据适配层（只读）：接口契约 + Mock/Sql 双实现
│   ├── module/                  业务模块
│   │   ├── antibiotic/          维度一~四：抗感染决策、PK/PD、DDD、细菌监测、词库配置
│   │   ├── sepsis/              脓毒症休克集束化治疗
│   │   ├── apache2/             APACHE II 评分
│   │   ├── ards/                ARDS 监测；prone = 俯卧位通气治疗记录（记录/时点/单元格/更正留痕/科室模板 + 采集映射配置）
│   │   └── handover/            医生交班览表 / 出科统计
│   └── quality/                 质控指标中台：DSL 引擎（YAML 口径 → SQL）+ 查询/计算/月度汇总/导出
├── src/main/resources/          application.yml、quality/（数据源/事实层/指标 YAML 配置）
├── conf/                        db.conf：MySQL/MariaDB 场景的连接配置（install-mariadb-debian.sh 读取）
├── sql/                         达梦 DM8 建库与初始化脚本（00 建模式 / 01 建表 / 02 种子 / 03+ 增量）—— 真源
│   └── mysql/                   MySQL / MariaDB 等价脚本（tools/dm_to_mysql.py 生成，勿手改）
├── frontend/                    Vue3 前端（src/views 页面、src/router 路由、src/api 接口）
├── docs/                        设计文档
├── lib/                         达梦 JDBC 驱动（随包交付）
├── app/                         后端可执行包 zing-doctor.jar
├── tools/                       内网部署辅助工具（db-init / dm_to_mysql.py / docker-compose 二进制）
├── Dockerfile / docker-compose.yml  容器部署
├── install.sh                   达梦场景：一键安装部署 / 升级（老库自动套用增量脚本）
└── install-mariadb-debian.sh    MySQL / MariaDB 场景：一键安装部署 / 升级（--config-only 只同步配置）
```
