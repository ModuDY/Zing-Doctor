# zing-doctor 医生系统（ICU 抗菌药物管理与临床决策）

面向 ICU 临床信息系统的独立医生系统，由 ICU 系统通过**外链免登录**方式嵌入打开，围绕抗菌药物管理（ASP）与危重症临床决策提供多维度支持。

- Spring Boot 2.7 + Java 8 后端，MyBatis-Plus 双数据源（主库 `zing_doctor_db_prod` / 只读 `zing_icu_db_prod`），数据库为**达梦 DM8**
- 外链免登录机制：外部系统（ICU）通过 `GET /entry/{pageCode}?expire=&sign=&业务参数`（签名模式）或 `GET /entry/{pageCode}?extToken=&业务参数`（ICU 明文模式）打开任意已注册页面
- Vue3 前端（history 路由，每页面可外链直达）、Docker / 内网直连部署

## 功能矩阵

| 维度 | 功能 | pageCode | 前端路由 |
|---|---|---|---|
| 第一维度 | 疑似感染患者列表 | `abx-patient-list` | `/page/abx-patient-list` |
| 第一维度 | 经验性抗感染治疗决策（规则引擎推荐 + 医生决策留痕） | `abx-decision` | `/page/abx-decision` |
| 第二维度 | PK/PD 抗菌药物剂量优化（肾功能/体重/低蛋白/CRRT，TDM 目标） | `abx-pkpd` | `/page/abx-pkpd` |
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
| 辅助 | 患者出科统计（支持 CSV 导出） | `discharge-stats` | `/page/discharge-stats` |
| 辅助 | SOFA 评分（序贯器官衰竭评估，6 项 0~24 分；含文书 PDF、指标趋势） | `sofa-score` | `/page/sofa-score` |
| 辅助 | SOFA 评分总览（总分分布 + ΔSOFA 恶化预警） | `sofa-overview` | `/page/sofa-overview` |
| 辅助 | SOFA 配置管理（取数项映射 / 升压药阈值 / 换算系数 / 默认体重） | `sofa-config` | `/page/sofa-config` |

> 共 17 个已注册外链页面；`abx-ddd-patients` / `abx-mdro-patients` 为总览页内部跳转的明细页，不经 `/entry` 外链进入。

## 快速开始

```bash
# 1. 初始化达梦数据库（推荐直接跑 install.sh，自动建模式 zing_doctor_db_prod 并建表）
#    手动方式：SYSDBA 执行 00_init_user.sql 建模式，再执行 01/02 建表与种子数据
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

ICU 明文模式联调无需签发，直接用配置的静态 `extToken` 访问：

```
http://<host>:2001/entry/abx-decision?extToken=zing-icu-link-token-2026&inHospitalNo=<住院号>
```

## 交付包命名规范

统一格式：`zing-doctor-<版本>-<形态>-<yyyyMMdd>.tar.gz`（或 `.zip`），`<版本>` 取自 `pom.xml` 的 `<version>`。

| 形态 | 内容 | 适用场景 | 体积参考 |
|---|---|---|---|
| `full` | 含源码，可在服务器重建 | 归档 / 需重新构建 | ~62 MB |
| `runtime` | 纯运行时，含离线 `docker-compose` 二进制 | 服务器无 `docker compose` 且无外网 | ~62 MB |
| `runtime-slim` | 纯运行时且去掉 compose 二进制 | 服务器自带 `docker compose`（推荐） | ~29 MB |

示例：`zing-doctor-1.0.0-runtime-slim-20260911.tar.gz`。完整规则与打包步骤见 [部署说明 §7](docs/03-部署说明.md)。

## 文档

- [架构设计](docs/01-架构设计.md)
- [外链传参规范](docs/02-外链传参规范.md)
- [部署说明](docs/03-部署说明.md)
- [安装部署手册（达梦，实操）](docs/04-安装部署手册.md)
- [第二维度 PK/PD 剂量优化 · 产品设计](docs/05-第二维度-PKPD剂量优化-产品设计.md)
- [SOFA 评分 · 产品设计](docs/06-SOFA评分-产品设计.md)
- 一键部署：解压后执行 `bash install.sh`（自动初始化达梦 + 构建启动，支持内网离线）

## 目录结构

```
zing-doctor/
├── src/main/java/com/zing/doctor/
│   ├── common/                  统一返回 Result / 业务异常 / 全局异常处理
│   ├── config/                  Web 拦截器注册、MyBatis-Plus 配置
│   ├── external/                外链免登录：签名工具/服务、/entry 入口、API 拦截器、令牌签发
│   ├── icu/                     ICU 数据适配层（只读）：接口契约 + Mock/Sql 双实现
│   └── module/                  业务模块
│       ├── antibiotic/          维度一~四：抗感染决策、PK/PD、DDD、细菌监测、词库配置
│       ├── sepsis/              脓毒症休克集束化治疗
│       ├── apache2/             APACHE II 评分
│       ├── ards/                ARDS 监测
│       └── handover/            医生交班览表 / 出科统计
├── src/main/resources/          application.yml
├── sql/                         建库与初始化脚本（00 建模式 / 01 建表 / 02 种子 / 03+ 增量）
├── frontend/                    Vue3 前端（src/views 页面、src/router 路由、src/api 接口）
├── docs/                        设计文档
├── lib/                         达梦 JDBC 驱动（随包交付）
├── app/                         后端可执行包 zing-doctor.jar
├── tools/                       内网部署辅助工具（db-init / docker-compose 二进制）
├── Dockerfile / docker-compose.yml  容器部署
└── install.sh                   一键安装部署脚本
```
