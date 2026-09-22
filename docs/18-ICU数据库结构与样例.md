# 18 · ICU 数据库（zing_icu_db_prod）结构与样例

>  doctor 系统的**只读源库**。本文由实库导出整理，供开发时查字段、写 SQL、判断「这个数据到底有没有」使用。
>
> - 导出时间：2026-09-22　实例：172.88.1.42:23306　版本：**MariaDB 10.5.6**（不是达梦，也不是 MySQL 8）
> - 库中是**真实数据**，本文样例已做脱敏（姓名 / 住院号掩码），请勿把未脱敏数据贴到文档或聊天里
> - 结构以实库为准，怀疑本文过期时按 §8 的命令重新导出

---

## 1. 怎么连

```bash
# 本机客户端路径（MySQL 8.0 客户端可连 MariaDB）
MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

# 只读连接（-D 指定 ICU 库）
"$MYSQL" --default-character-set=utf8mb4 -h 172.88.1.42 -P 23306 -u root -p -D zing_icu_db_prod
```

> ⚠️ **别用 root 做日常查询**。让 DBA 建一个只读账号：
>
> ```sql
> CREATE USER 'zing_ro'@'%' IDENTIFIED BY '<强口令>';
> GRANT SELECT ON zing_icu_db_prod.* TO 'zing_ro'@'%';
> ```
>
> 另外：`conf/db.conf` 目前被 git 跟踪且内含 `root/zing@123`，往里写这个库的口令会被提交上去；
> 开发期想连，建议用上面的命令行或临时 RSA / my.cnf，不要写进版本库文件。

---

## 2. 库整体

| 项 | 值 |
|---|---|
| 实例版本 | MariaDB 10.5.6（容器内 3306，映射 23306） |
| 库清单 | `zing_icu_db_prod`（ICU 业务）、`zing_api_db_prod`、`zing_ai_cdss_db_prod` |
| ICU 库表数 | **793**（778 InnoDB + 2 视图：`quality_index_summary`、`V_ICU_TWD_IO`） |
| 字符集 | `utf8`（注意不是 utf8mb4，含生僻字姓名可能存不进去） |
| 主键风格 | 统一 `varchar(32)` 雪花 ID，**没有自增主键** |
| 软删除 | 每张业务表都有 `del_flag`，查询必须带条件（见 §5.8 类型陷阱） |
| 租户 | 全库 `tenant_id` 目前只有 `'1'` 一个值，但保留该字段做多院区扩展 |

### 表命名分组（793 张表里常用的几类）

| 前缀 | 含义 | 代表表 |
|---|---|---|
| `config_` | 各类配置 / 字典（药品、职工、班次、监测项、床位） | `config_drug`、`config_staff`、`config_shift`、`config_observe_item` |
| `patient_info_` | 患者主数据与附属信息 | `patient_info`、`patient_info_diagnosis`、`patient_info_lis*` |
| `patient_advice_*` | 医嘱（API 原始 / 执行 / 每小时剂量 / 药品明细） | `patient_advice_execute`、`patient_advice_execute_drug` |
| `patient_observe_*` | 监测评估（体征、评分）记录 | `patient_observe_module_item_record` |
| `patient_measure_*` | 护理措施记录 | `patient_measure_module_item_record` |
| `patient_tube_*` | 导管（中心静脉 / 气管插管）观察记录 | `patient_tube_record` |
| `quality_*` | 质控（指标、规则、计算日志） | `quality_count_rule`、`quality_index` |
| `icu_*` / `patient_shift*` | 医生 / 护士交班 | `icu_doctor_shift_record`、`icu_shift_doctor_record` |
| `sys_*` / `QRTZ_*` | ICU 系统自身的用户权限与定时任务 | `sys_user`、`QRTZ_JOB_DETAILS` |

### 数据量 TOP（**写 SQL 前先看这里**）

| 表 | 行数 | 说明 |
|---|---|---|
| `patient_info_lis_item` | **8118 万** | 检验明细，最大的坑，禁止无索引全表扫 |
| `patient_observe_module_item_history` | 1425 万 | 监测项历史 |
| `patient_observe_module_item_record` | 1348 万 | 监测项记录（当前值） |
| `patient_original_advice` | 791 万 | HIS 原始医嘱 |
| `patient_info_lis` | 748 万 | 检验报告主表 |
| `patient_measure_module_item_record` | 745 万 | 措施记录 |
| `patient_info_check` | 599 万 | 检查报告（CT / 超声等） |
| `patient_advice` | 551 万 | 医嘱执行计划 |
| `patient_advice_execute` | 69 万 | 医嘱执行记录 |
| `patient_info` | 5654 | **患者主表很小**，可以放心用它做驱动表 |
| `patient_info_diagnosis` | 4.8 万 | 患者诊断 |

> 结论：**永远用 `patient_info`（5654 行）做驱动表**，再用 `patient_id` / `in_hospital_no` 去关联大表；反过来先扫大表必挂。

---

## 3. 当前代码已经引用的 6 张表

代码里显式带 `"zing_icu_db_prod"."xxx"` 的表只有这 6 张（`IcuPatientMapper` / `IcuDrugMapper` / `StaffSignatureService` / `QualityCountRuleMapper`）。
**它们的字段变动直接影响线上功能，改库前先确认这边。**

### 3.1 `patient_info` —— 患者主数据（118 列，5654 行，在科 65 人）

`@Select` 里被用到的字段与实际类型的对照：

| 字段 | 类型 | 代码里的用法 | 实库情况 |
|---|---|---|---|
| `id` | varchar(32) | `AS patient_id`，与诊断/检验表 join | 雪花字符串 |
| `in_hospital_no` | varchar(32) | 患者唯一业务号 | 与 `patient_info_lis_item` 关联靠它 |
| `name` | varchar(100) | 姓名 | 真实姓名，**展示需脱敏** |
| `gender` | varchar(10) | 性别 | 值为「男 / 女」**中文**，不是 0/1 |
| `age` | varchar(10) | 年龄 | 字符串，65 条在科记录**全是数字** |
| `ward_name` | varchar(50) | `AS department` | ⚠️ 样例里**有空值**（见 §5） |
| `bed_code` | varchar(20) | 床号 | 如 `0615` |
| `weight` / `height` | varchar(20) | `AS weight/height` | ⚠️ 值是「卧床」这类**非数字**，见 §5.2 |
| `is_sepsis_shock` | tinyint(1) | `= 1` 筛脓毒性休克 | ⚠️ **全表 0 条非空**，见 §5.1 |
| `multidrug_resistant_bacteria` | varchar(30) | 多重耐药菌标记 | ⚠️ **全表 NULL**，见 §5.1 |
| `resistant_bacteria` | varchar(30) | 耐药菌 | ⚠️ **全表 NULL** |
| `allergy_content` | varchar(300) | 过敏史 | 65 条中 62 条有值，内容是自由文本（「未发现」） |
| `is_in_depart` | tinyint(1) | `= 1` 在科 | 在科 65（另 `del_flag=0`） |
| `del_flag` | **tinyint(1)** | `= 0` | 见 §5.8 |
| `in_depart_time` | datetime | 入科时间，排序用 | — |
| `is_ards` | tinyint(1) | ARDS 程度（0 无 / 1 中 / 2 重） | ⚠️ 全表仅 1 条非空 |

其他开发常用字段：`in_hospital_serial_no`（每次住院不同）、`diagnosis_content`（longtext 全量诊断文本）、
`in_depart_diagnosis_content`（入科诊断）、`out_hospital_time`、`out_vest_type`（转归：1转科 2出院 3死亡）、
`apache_info`、`cure_grade`（危重等级）、`charge_doctor_code/name`（主管医生）。

**样例（脱敏，在科患者）**

```text
id=2061168043770839042  in_hospital_no=3008***88  name=周*   gender=男  age=42
ward_name=(空)           bed_code=1616
weight=卧床  height=卧床  allergy_content=未发现
is_sepsis_shock=NULL  multidrug_resistant_bacteria=NULL  resistant_bacteria=NULL
in_depart_time=2026-06-01 03:30:00
diagnosis_content=1、多处损伤；2、创伤性湿肺；3、气胸……11、肝功能不全
```

### 3.2 `config_staff` —— 职工表（6577 行）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `user_id` | varchar(32) | 主键 / 关联 `sys_user` |
| `work_no` | varchar(100) | **工号**，唯一索引 `(work_no, tenant_id)`，签名关联靠它 |
| `realname` | varchar(100) | 姓名 |
| `pinyin` | varchar(50) | 拼音，搜索框用它做首字母匹配 |
| `staff_kind_code` | varchar(10) | 1 医生、3 护士；⚠️ **大量为空** |
| `depart_name` / `post_name` | varchar | 科室 / 职务（副主任医师等） |
| `status` / `del_flag` | tinyint(1) | 1 启用 / 0 正常 |

现状：6577 人全部 `status=1`、`del_flag=0`，`work_no` 无空值且唯一 —— 可以安全按工号 join。

### 3.3 `config_staff_ca_info` —— 电子签名（337 行）

`StaffSignatureService` 的数据源，用于文书上的手写签名图。

| 字段 | 类型 | 说明 |
|---|---|---|
| `work_no` | varchar(100) | 关联 `config_staff.work_no` |
| `realname` | varchar(100) | 姓名 |
| `signature_img` | **longtext** | Base64 PNG，平均 **6.5 KB**，337 条全有值 |
| `cert_sn` / `cert_dn` | text / varchar(50) | ⚠️ 实库**全为 NULL** |
| `cert_start_time` / `cert_end_time` | datetime | ⚠️ 全为 NULL，没法做证书有效期校验 |
| `image_update_time` | datetime | ⚠️ 全为 NULL |

> 结论：只有 337 人有签名图（职工 6577 人中的 5%），**签名图缺失要按「降级为打印姓名」处理**，
> 且不能依赖 `cert_*` 字段做任何有效期判断。

### 3.4 `config_shift` —— 班次（288 行）

`IcuPatientMapper` 里用于「按班次过滤数据」。注意 **同一班次名按 `shift_type` 重复多份**：

| shift_type | 含义 | 样例 |
|---|---|---|
| 1 | 默认 | 日班 08:00–16:59 / 中班 17:00–23:59 / 晚班 00:00–07:59 / 全天 08:00–次日07:59 |
| 2 | 监测 | 同上，另有一份全天 |
| 4 | 医嘱 | 日班 08:00–16:59 |

跨天靠 `is_begin_now` / `is_end_now` 表达（1 当天 / 0 次日 / 2 昨日），例如「全天」是 `begin 08:00 + is_end_now=0`（次日结束）。
`shift_tag`（日/中/夜）实库**多为 NULL**，别用它做判断。

### 3.5 `quality_count_rule` —— 质控统计项规则（82 条）

医生系统「质控指标看板」的规则来源，`QualityCountRuleMapper.syncFromIcu()` 跨库 INSERT…SELECT 同步。

| 字段 | 类型 | 说明 |
|---|---|---|
| `count_name` | varchar(100) | 指标名，如「中重度ARDS患者俯卧位通气实施率」 |
| `numerator_code` / `denominator_code` | varchar(50) | 分子 / 分母的**原子项代码**（`quality_311` / `quality_312` 这种） |
| `numerator_rate` / `denominator_rate` / `percent_rate` | **varchar(10)** | 放大系数，默认 `'1'` / `'100'` ⚠️ 字符串，计算要 `CAST` |
| `*_precision` | int | 小数位 |
| `quality_type_code` | varchar(20) | 质控类型，实库主要 `1` |
| `status` / `del_flag` | **varchar(1)** | ⚠️ 与其他表的 tinyint 不同，见 §5.8 |

**样例（真实内容，不涉及隐私）**

```text
count_name=中重度急性呼吸窘迫综合征（ARDS）患者俯卧位通气实施率
numerator_code=quality_311   denominator_code=quality_312   percent_unit=%
count_name=ICU抗菌药物治疗前病原学送检率          numerator_code=quality_12  denominator_code=quality_13
count_name=ICU呼吸机相关性肺炎（VAP）发病率        numerator_code=quality_24  denominator_code=quality_25
```

> 注意 ARDS 俯卧位指标 `quality_311/312` 就在里面 —— 与 doc 17《ARDS 俯卧位通气治疗记录》直接对应。

### 3.6 `config_drug` —— 药品字典（14983 行）

抗菌药字典同步（`AbxDrugSyncTask` / `IcuDrugMapper`）的来源。

关键字段：`drug_code`、`drug_name`（含医保标识和商品名，如「(乙)(泰能)注射用亚胺培南西司他丁钠(1:1)」）、
`drug_normal_name`（⚠️ 大量 NULL）、`spec`（规格）、`dose`、`unit_code`、`ddd_value`（DDD 值，⚠️ 大量 NULL）、
`is_antibiotics`（tinyint）、`antibiotics_color`、`is_anes`（麻醉药）、`drug_pinyin`（拼音检索）。

**样例**

```text
drug_code=2201260215
drug_name=(乙)(泰能)注射用亚胺培南西司他丁钠(1:1)
spec=1.0g  dose=1.0  unit_code=g  is_antibiotics=1  ddd_value=NULL  antibiotics_color=NULL
```

⚠️ 抗菌药识别口径问题极大，见 §5.5。

---

## 4. 接下来做功能会用到的表（速查）

除了上面 6 张，按医生系统的产品方向（docs 07–17）列一下对应的表，**行数=0 的表示这台库还没灌数据**：

| 功能方向 | 表 | 行数 | 用途 |
|---|---|---|---|
| 患者诊断 | `patient_info_diagnosis` | 4.8 万 | `diag_name` 诊断名、`diag_level` 01 主要 / 02 次要、`diag_time`；索引 `patient_id`?（有 `in_hospital_no`/`diag_code`/`diag_time`） |
| 检验明细 | `patient_info_lis_item` | 8118 万 | `lis_item_name` / `lis_item_result` / `lis_item_unit` / `check_time`；**索引：`in_hospital_no`、`lis_item_code`、`(lis_item_code,check_time)`、`check_time`** |
| 检验报告 | `patient_info_lis` | 748 万 | 报告主表 |
| 药敏试验 | `patient_info_lis_ast` + `patient_info_lis_ast_item` | **0 / 0** | ⚠️ 无数据，MDRO 分析没有源 |
| 多重耐药菌记录 | `patient_info_multiple_bacteria_record` | **0** | 字段齐全（`bacteria_sample` 标本、`bacteria_name` 菌名、`prevention_measure` 防控措施）但无数据 |
| 检查报告 | `patient_info_check` | 599 万 | CT / 超声等 |
| 医嘱原始 | `patient_original_advice` | 791 万 | HIS 开立 |
| 医嘱执行 | `patient_advice_execute` | 69 万 | ⭐ 抗菌药用量的实际来源（见下） |
| 医嘱每小时剂量 | `patient_advice_execute_item` | 294 万 | 走 `idx_pae_pi(patient_id)` |
| 医嘱药品明细 | `patient_advice_execute_drug` | 136 万 | 具体药品 / 剂量 / 速度 |
| 监测项目字典 | `config_observe_item` | 175 | `item_code` / `item_name` / `is_number` / 阈值 / 危急值 |
| 监测记录 | `patient_observe_module_item_record` | 1348 万 | `item_code` + `item_value`(varchar500) + `item_time`；**唯一索引 `(patient_id, item_code, item_time)`**，按患者+时间查很快 |
| 导管 / VAP | `patient_tube_record` / `_item` | 63 万 / 168 万 | 插管观察记录 |
| 医生交班 | `icu_doctor_shift_record`（6） / `icu_shift_doctor_record`（8） / `icu_doctor_shift_record_item`（9） | 极少 | 交班功能要用的表基本是空的 |
| 质控结果 | `quality_index_record` | 8.8 万 | 患者级质控结果 |
| 质控计算日志 | `quality_calculate_log` | 150 万 | 每次计算留痕 |
| 抗生素分析配置 | `config_antibiotic_observe_item` | **0** | ⚠️ 空的 |
| 设备 | `config_device` | 191 | 监护仪 / 呼吸机绑定（对应 `patient_info.monitor_code` 等） |

**`patient_advice_execute`（88 列）里做用药分析常用的字段**：
`patient_id` / `in_hospital_no` / `name`（医嘱名）/ `advice_type`（药/诊疗/文本）/ `drug_method_name`（用法）/
`is_long`（0 临时 1 长期）/ `start_time` / `end_time` / `status`（0未执行 1执行 2暂停 3结束）/
`liquid_amount` / `drug_now_speed` / `freq_name`（频次）/ `open_staff_code`（开嘱医生工号）。
索引：`idx_pae_pi(patient_id)`、`idx_pae_all(patient_id,start_time,end_time,is_to_io,status,del_flag)`、`idx_pae_pp(patient_id,plan_start_time)`。

---

## 5. 真实数据体检（**这一节最关键，开发前必读**）

以下都是实库跑出来的结论，直接决定功能能不能出数。

### 5.1 三个「标记列」全是 NULL —— 依赖它们的筛选永远不命中

```sql
SELECT COUNT(*) FROM patient_info WHERE is_sepsis_shock IS NOT NULL;            -- 0（共 5938）
SELECT COUNT(*) FROM patient_info WHERE multidrug_resistant_bacteria IS NOT NULL; -- 0
SELECT COUNT(*) FROM patient_info WHERE resistant_bacteria IS NOT NULL;           -- 0
```

`IcuPatientMapper.selectSuspectPatients()` 的第一个 OR 分支 `pi.is_sepsis_shock = 1` **恒为假**，
DTO 里的 `septic_shock` / `mdr_bacteria` / `resistant_bacteria` 字段**恒为 NULL**。

### 5.2 `weight` / `height` 是 varchar，且在科患者里 0 条是数字

在科 65 人：`weight` 非空 65 条，但 `REGEXP '^[0-9.]+$'` 命中 **0** 条 —— 实际值是「**卧床**」这类文本。

→ 任何按体重算的剂量（PK/PD、俯卧位、BMI、肌酐清除率）**必须先做数值校验**，否则 `Double.parseDouble` 直接抛异常。
我在别的表也没找到稳定体重源，短期内建议：**取不到就提示手工录入**，别静默用 0。

### 5.3 疑似感染患者主查询：3 个分支只剩 1 个能用

```text
在科总数 65
  is_sepsis_shock = 1        → 命中 0  （§5.1 列全 NULL）
  诊断含感染/脓毒/肺炎等关键词 → 命中 46 ✅ 唯一有效分支
  降钙素原(PCT)检验存在       → 命中 0
```

### 5.4 PCT（降钙素原）在这台库里没有数据

近 60 天 `lis_item_name LIKE '%降钙素原%'` 查不到任何记录；`config_lis_item`（检验项目字典）只有 **8 条**
（`pC02`、`PH`、`p02`、`Na+`、血小板计数、白细胞计数等），也**不含 PCT**。

→ 第三个分支等同失效。要恢复要么换判据（如「WBC / CRP / IL-6」），要么确认院方 LIS 是否真的没上送 PCT。

### 5.5 抗菌药识别：官方标记只有 1 条，靠名称能捞到 787 条

```text
config_drug 总行数        14983
is_antibiotics = 1            1   ← AbxDrugSyncTask 同步的就是这 1 条
is_antibiotics1（备用字段）   198   ← 值居然是 '01'/'02'/'03'，语义不明、不可用
按名称匹配常见抗菌药          787   ← 头孢/西林/霉素/沙星/培南/硝唑/康唑…
```

→ 现在「抗菌药物」相关页面（doc 07 经验性抗感染、doc 08 DDD 强度）**只有 1 个药**，等于空转。
项目里已有 **doc 14《抗菌药物识别词库配置》**，正好是这个坑的解法：把识别口径改成「名称匹配 + 本地词库维护」，
不要再把 `is_antibiotics` 当权威来源。

### 5.6 细菌培养 / 药敏相关表全是空表

`patient_info_lis_ast`、`patient_info_lis_ast_item`、`patient_info_multiple_bacteria_record`、
`config_antibiotic_observe_item` **行数均为 0** → doc 09《细菌培养检出监测 MDRO》在这个库上联调没有数据，
要联调得先让院方灌数，或者像 §5.5 一样准备 mock。

### 5.7 签名：337 人有图，证书字段全空

见 §3.3。签名图 Base64 平均 6.5 KB，取出来能直接 `<img src="data:image/png;base64,...">` 渲染，
但要注意 337/6577 的覆盖率，没有图的必须降级。

### 5.8 类型陷阱：`del_flag` / `status` 在不同表里类型不一致

| 表 | `del_flag` | `status` |
|---|---|---|
| `patient_info`、`config_staff`、`config_shift`、`patient_advice_execute` 等多数表 | **tinyint(1)** | tinyint(1) |
| `config_drug`、`quality_count_rule` | **varchar(1)** | varchar(1) |

写 SQL 时要分开：`pi.del_flag = 0`（数字）vs `src.del_flag = '0'`（字符串）。
`QualityCountRuleMapper` 现在写的是 `src."del_flag" = '0'`，**是对的**，别顺手改成 0 ——
虽然 MySQL 多数情况能隐式转换，到达梦环境下 varchar 列跟数字比可能走不上索引甚至报错。

### 5.9 其他零碎但会咬人的点

- `gender` 是中文「男/女」，不是编码
- `ward_name`（病区名）样例里有**空值**，前端展示要有兜底
- `is_ards` 全表仅 1 条非空，别指望用它筛 ARDS 患者
- 全库 `tenant_id = '1'`，写查询时可以带但别当过滤依据
- MySQL 8.0 客户端连 MariaDB 10.5 正常，但**服务端字符集是 `utf8`**，4 字节字符无法存入

---

## 6. 给开发的建议

1. **不要依赖单列标记做筛选**。参考 §5.1/§5.4，把「疑似感染」判据改成多源 OR（诊断 + 检验 + 医嘱），并支持配置化阈值。
2. **所有数值型取值都要校验**。`weight`/`height`/`lis_item_result` 都可
   能是非数字字符串（「卧床」、`<0.05`、`阳性`），Java 端统一用「解析失败 → 置空 + 提示」，不要 try/catch 静默吞。
3. **大表查询必须命中索引**。可用的驱动路径：
   - 检验：`patient_info_lis_item` 走 `in_hospital_no` 或 `(lis_item_code, check_time)`，**不要**对 `lis_item_name` 做全表 LIKE
   - 监测：`patient_observe_module_item_record` 走 `(patient_id, item_code, item_time)`
   - 医嘱：`patient_advice_execute` 走 `patient_id`
   - 诊断：`patient_info_diagnosis` 走 `in_hospital_no` / `diag_time`
   - 通式：**先从 `patient_info`（5654 行）拿 `id` / `in_hospital_no`，再去大表 EXISTS 或 IN**
4. **达梦 / MariaDB 方言**：此库是 MariaDB，Mapper 里写的是达梦方言（双引号标识符、`ROWNUM`），
   由 `MySqlDialectInterceptor` 执行前翻译；新写的 Mapper 保持同样风格即可，**不要混写成 MySQL 专用语法**。
5. **联调用 mock**。现在 ICU 环境有很多数据缺口（PCT、药敏、交班），建议给 `ICU_DATA_PROVIDER=mock` 补上对应的
   示例数据，别阻塞前端联调。
6. **只读**。开发查询一律 SELECT；要给 ICU 库加索引必须走院方 DBA（`SETUP_ICU_INDEX` 默认关闭就是因为这个）。

---

## 7. 常用查询模板

```sql
-- ① 在科患者（含感染/耐药相关字段的真实可用性）
SELECT pi.id, pi.in_hospital_no, pi.name, pi.gender, pi.age,
       pi.ward_name, pi.bed_code, pi.in_depart_time,
       pi.is_sepsis_shock, pi.multidrug_resistant_bacteria, pi.weight
  FROM zing_icu_db_prod.patient_info pi
 WHERE pi.is_in_depart = 1 AND pi.del_flag = 0
 ORDER BY pi.in_depart_time DESC;

-- ② 某患者的诊断（按主诊断优先）
SELECT d.diag_name, d.diag_level, d.diag_code, d.diag_time
  FROM zing_icu_db_prod.patient_info_diagnosis d
 WHERE d.in_hospital_no = '<住院号>' AND d.del_flag = 0 AND d.status = 1
 ORDER BY d.diag_level, d.diag_time DESC;

-- ③ 某患者的某项检验（ⓘ 必须带 item_code 或 check_time 才能走索引）
SELECT li.lis_item_name, li.lis_item_result, li.lis_item_unit,
       li.lis_item_alarm_flag, li.check_time
  FROM zing_icu_db_prod.patient_info_lis_item li
 WHERE li.in_hospital_no = '<住院号>'
   AND li.del_flag = 0
   AND li.lis_item_name LIKE '%降钙素原%'
 ORDER BY li.check_time DESC
 LIMIT 20;

-- ④ 某患者的用药医嘱（抗菌药分析用）
SELECT ae.name, ae.advice_type, ae.drug_method_name, ae.freq_name,
       ae.start_time, ae.end_time, ae.status, ae.open_staff_name
  FROM zing_icu_db_prod.patient_advice_execute ae
 WHERE ae.in_hospital_no = '<住院号>' AND ae.del_flag = 0
 ORDER BY ae.start_time DESC;

-- ⑤ 取某人电子签名图（缺失要降级为打印姓名）
SELECT cs.realname, ci.signature_img
  FROM zing_icu_db_prod.config_staff cs
  LEFT JOIN zing_icu_db_prod.config_staff_ca_info ci
         ON ci.work_no = cs.work_no AND ci.del_flag = 0
 WHERE cs.work_no = '<工号>' AND cs.del_flag = 0;

-- ⑥ 质控指标规则（分子/分母原子项 + 放大系数）
SELECT count_name, numerator_code, denominator_code,
       numerator_rate, denominator_rate, percent_rate, sort_no
  FROM zing_icu_db_prod.quality_count_rule
 WHERE del_flag = '0'
 ORDER BY CAST(sort_no AS SIGNED);
```

---

## 8. 怎么重新导出（本文过期时）

```powershell
$m = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

# 全表清单 + 行数 + 注释
& $m --default-character-set=utf8mb4 -h <host> -P 23306 -u <ro用户> -p `
     -e "SELECT table_name, table_rows, table_comment FROM information_schema.TABLES WHERE TABLE_SCHEMA='zing_icu_db_prod' ORDER BY table_rows DESC;"

# 单表结构（字段 + 类型 + 注释）
& $m --default-character-set=utf8mb4 -h <host> -P 23306 -u <ro用户> -p `
     -e "SELECT column_name, column_type, column_comment FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zing_icu_db_prod' AND TABLE_NAME='<表名>' ORDER BY ordinal_position;"
```

导出的数据不要直接贴进本文件：患者姓名、住院号、身份证、电话一律脱敏。
结构有变化请同步更新本文 §3（代码引用表）与 §5（数据体检结论）。
