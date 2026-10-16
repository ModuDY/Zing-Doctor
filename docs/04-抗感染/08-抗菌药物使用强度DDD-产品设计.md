# 抗菌药物使用强度（DDD）· 产品设计方案

> 版本：设计稿 v1 · 2026-09-13
> 定位：第三维度。按 **WHO ATC/DDD 方法学**自动统计 ICU 抗菌药物使用强度（DDD 值 / 100 床日）、品种排名、药物分类与分级占比，用于科室抗菌药物合理性管控与等级评审指标上报。

## 一、背景与定位

### 1.1 解决什么问题

- 国家卫健委《抗菌药物临床应用管理》要求监测**抗菌药物使用强度（DDD）**，是 ICU 质控与等级评审的硬指标；
- 手工统计需要从医嘱里逐条累加剂量、按药品匹配 WHO DDD 值、再除以床日数，工作量大且易错；
- 科室需要知道「**用了多少、用在哪、谁用的、有没有超特殊级**」，才能做 PDCA 改进。

### 1.2 指标口径（标准定义）

```
使用强度（DDD值 / 100 床日） = 累计 DDDs / 同期收治患者床日数 × 100
DDDs（每条医嘱）              = 单次剂量 × 每日给药次数 × 使用天数 ÷ 该药 DDD 值
使用率                        = 使用抗菌药物患者数 / 同期患者总数 × 100%
```

## 二、核心计算逻辑

### 2.1 医嘱筛选（SQL 层）

`IcuPatientMapper#selectAbxAdviceForStats`：

```sql
SELECT ... FROM (
  SELECT pa.group_id, pa.in_hospital_no, pa.name, pa.freq_name, pa.drug_method_name,
         pa.start_time, pa.plan_start_time, pa.drug_one_dosage, pa.drug_one_dosage_unit,
         ROW_NUMBER() OVER (PARTITION BY pa.group_id, pa.name ORDER BY pa.start_time DESC) rn
  FROM patient_advice pa
  INNER JOIN patient_info pi ON pi.in_hospital_no = pa.in_hospital_no AND pi.del_flag = 0
  WHERE pa.del_flag = 0 AND pa.status IN ('1','3') AND pa.type_code = 'drug'
    AND pa.start_time >= #{startTime} AND pa.start_time < #{endTime}
    AND pi.depart_code = #{departCode}
) WHERE rn = 1
```

- 只取**药品类**医嘱（`type_code='drug'`）、有效状态（`status IN ('1','3')`，含已停止）；
- 按 `group_id + name` 去重，**同组同药只算最新一条**（避免一条医嘱被拆成多条明细重复计量）；
- 科室通过 `patient_info.depart_code` 过滤，`departCode` 为空查全部。

### 2.2 逐条医嘱计算（`calcAdviceDdds`）

| 步骤 | 规则 |
|---|---|
| ① 排除溶媒 | 命中溶媒关键词（氯化钠、葡萄糖、乳酸钠林格、灭菌注射用水、木糖醇、转化糖、果糖、复方氯化钠、甘油果糖、生理盐水）→ 跳过。**当前实现的 `isSolvent()` 恒返回 false（注释为「暂时不过滤，交给 DDD 匹配」），即实际依赖「溶媒匹配不到 DDD 配置」来天然排除** |
| ② 匹配 DDD 知识库 | 两轮匹配：先按 `drug_name` **包含**匹配通用名，再按 `keywords`（逗号分隔）包含匹配。**两轮都没命中 → 该条医嘱直接被丢弃、不计量** |
| ③ 解析单次剂量 | 优先取字段 `drug_one_dosage` + `drug_one_dosage_unit` 并按目标单位换算（g↔mg↔μg）；缺字段则从医嘱名称正则提取 `数字 + (g\|mg\|MU\|万单位)`，多个匹配取**最大值**（如「0.5g/瓶 1g」取 1g） |
| ④ 计算使用天数 | 有效开始 = max(`start_time`, 周期开始)；有效结束 = min(实际 `end_time` → `plan_end_time` → 周期结束)；再与**患者在科时间**取交集（`effectiveStart = max(…, in_depart_time)`、`effectiveEnd = min(…, out_depart_time)`，还在科用周期结束）。小时 ÷ 24 取 2 位小数，**不足 1 天按 1 天** |
| ⑤ 每日给药次数 | `freq_name` 查频次表，未命中默认 **1.0** |
| ⑥ DDDs | `单次剂量 × 每日次数 × 使用天数 ÷ dddValue`，4 位小数 HALF_UP |

### 2.3 频次映射表（`FREQ_MAP`）

| 频次 | 每日次数 | 频次 | 每日次数 |
|---|---|---|---|
| qd | 1.0 | q12h | 2.0 |
| bid | 2.0 | q24h | 1.0 |
| tid | 3.0 | q48h | 0.5 |
| qid | 4.0 | q72h | 0.33 |
| q4h | 6.0 | st / prn / 必要时 / 立即 | 1.0 |
| q6h | 4.0 | q8h | 3.0 |

> 未命中（如「每日三次」「q8-12h」「遵医嘱」）一律按 **1.0**，这会**低估**该条医嘱的 DDDs，是该模块最大的数据质量风险点。

### 2.4 床日数（分母）

- `calcPatientBedDays`（**用于 DDD 分母**）：在科交集时长 = max(入科时间, 周期开始) → min(出科时间, 周期结束)；小时 ÷ 24 保留 2 位小数，**不足 1 天按 1 天**；入科时间为空返回 1。
- `calcStayDaysForDisplay`（**用于患者明细展示**）：按「日期差 + 1」口径（9-01 至 9-30 = 30 天），与第四维度 MDRO 的「在科天数」保持一致。
- ⚠️ 两套口径**故意不同**（代码注释已说明）：分母用小时口径，展示用自然日口径。看明细里的「在科天数」反推 DDD 分母会对不上，需要在文档/UI 上说明。

### 2.5 汇总指标

| 指标 | 计算 |
|---|---|
| 总 DDDs | 所有有效医嘱 DDDs 之和 |
| 特殊级 DDDs | `manage_level = '特殊'` 的 DDDs 之和 |
| 特殊级占比 | 特殊级 DDDs ÷ 总 DDDs × 100% |
| 使用强度 | 总 DDDs ÷ 总床日数 × 100（无床日返回 0） |
| 特殊级使用强度 | 特殊级 DDDs ÷ 总床日数 × 100 |
| 使用率 | 使用抗菌药患者数（按 `in_hospital_no` 去重）÷ 总患者数 × 100% |
| 药物分类占比 | 按 `drug_class` 分组求 DDDs 及占比 |
| 管理级别占比 | 按 `manage_level` 分组求 DDDs 及占比 |
| 药品排名 TOP20 | 按药品通用名分组，输出 DDDs 合计、占比、使用患者数、DDD 值/单位，按 DDDs 降序取前 20 |
| 月度趋势 | 按自然月逐月重算 DDDs、使用强度、使用患者数 |

## 三、数据源映射

| 数据 | 表 | 关键字段 |
|---|---|---|
| 抗菌药医嘱 | `patient_advice` | `group_id, in_hospital_no, name, freq_name, drug_method_name, start_time, end_time, plan_end_time, drug_one_dosage, drug_one_dosage_unit` |
| 患者与在科时间 | `patient_info` | `in_hospital_no, depart_code, in_depart_time, out_depart_time, del_flag` |
| 患者列表（分母） | `patient_info` | `selectPatientsForStats(startTime, endTime, departCode)` |
| 科室下拉 | `sys_depart` | `selectAllDepartments()` |
| DDD 知识库 | `config_ddd` | `drug_name, atc_code, ddd_value, ddd_unit, route, manage_level, drug_class, keywords, status` |

## 四、DDD 知识库（`config_ddd`）

```sql
CREATE TABLE "zing_doctor_db_prod"."config_ddd" (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    drug_name    VARCHAR(128) NOT NULL,   -- 药品通用名
    atc_code     VARCHAR(32),             -- WHO ATC 编码
    ddd_value    DECIMAL(10,4) NOT NULL,  -- DDD 值（限定日剂量）
    ddd_unit     VARCHAR(16),             -- DDD 单位（g/mg/MU）
    route        VARCHAR(16),             -- 给药途径：注射/口服
    manage_level VARCHAR(16),             -- 管理级别：非限制/限制/特殊
    drug_class   VARCHAR(64),             -- 药物分类（青霉素类/头孢三代/碳青霉烯类…）
    keywords     VARCHAR(500),            -- 匹配关键词（逗号分隔）
    remark       VARCHAR(500),
    status       TINYINT DEFAULT 1 NOT NULL
);
-- 唯一索引 (drug_name, route)；普通索引 (drug_class)、(manage_level)
```

- 初始数据按 **WHO ATC/DDD 最新版本**录入（`sql/02_seed.sql`），覆盖青霉素类、头孢一~四代、碳青霉烯类、糖肽类、喹诺酮类、抗真菌类、硝基咪唑类、磺胺类等；
- 种子脚本为**幂等写法**：`INSERT ... SELECT ... WHERE NOT EXISTS (drug_name, route)`，重复部署不会重置后台配置（v23.1.13 起不再清表）；
- 关键词覆盖商品名，例如哌拉西林他唑巴坦 → `哌拉西林,他唑巴坦,特治星,邦达`。

## 五、后端接口

### 5.1 统计分析（`/api/antibiotic/ddd`）

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `/departments` | — | 科室下拉（`sys_depart` 启用科室） |
| GET | `/overview` | `startTime, endTime, departCode` | 总览：床日数、患者数、总/特殊级 DDDs、使用强度、使用率、分类与级别占比 |
| GET | `/drug-rank` | `startTime, endTime, departCode` | 药品 DDDs 排名 TOP20 |
| GET | `/monthly-trend` | `startTime, endTime, departCode` | 月度趋势（DDDs、使用强度、使用患者数） |
| GET | `/patients` | `startTime, endTime, departCode` | 患者维度明细列表 |
| GET | `/patient/{inHospitalNo}` | `startTime, endTime` | 单患者 DDD 明细（逐条医嘱 DDDs） |

### 5.2 配置管理（`/api/antibiotic/ddd/config`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/list` | 全部启用配置 |
| GET | `/by-class` | 按药物分类查询（`drugClass`） |
| GET | `/classes` | 全部分类枚举 |
| GET | `/count-by-class` | 各分类条目数（配置页分组展示用） |
| POST | `/add` | 新增药品 DDD 配置 |
| POST | `/update` | 修改配置 |
| POST | `/toggle` | 启用/停用（`id, status`） |

## 六、前端页面

| 页面 | 路径 | 内容 |
|---|---|---|
| DDD 总览（`DddOverview.vue`） | `/page/ddd-overview` | 筛选（科室 + 时间范围）、指标卡（床日数/患者数/总 DDDs/使用强度/使用率/特殊级占比）、药物分类占比图、管理级别占比、月度趋势折线、药品 TOP20 排名表 |
| 患者明细（`DddPatients.vue`） | `/page/ddd-patients` | 按患者的 DDD 明细列表（含在科天数、使用强度、抗菌药条数），可下钻到单患者逐条医嘱 DDDs |
| 配置页（`DddConfig.vue`） | `/page/ddd-config` | 药品 DDD 值知识库维护：分类分组、关键词、管理级别、启停开关 |

## 七、风险与已知限制

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | 频次未命中一律按 1 次/日 | **低估** DDDs（如「每日三次」） | 扩充 `FREQ_MAP`（中文频次、q8-12h、qod 等），或对未命中频次打标提示 |
| 2 | DDD 配置未命中即丢弃该医嘱 | **低估** DDDs 与使用率 | 增加「未匹配医嘱清单」报表，驱动知识库补录 |
| 3 | 分母用「小时/24」，展示用「日期差+1」 | 两处口径不一致，交叉核对会疑惑 | UI 上标注口径，或统一为一种 |
| 4 | 医嘱去重按 `group_id + name` 取最新一条 | 同药多次调整剂量的历史量被丢弃 | 若需严格按执行记录累加，应改用「医嘱执行/发药记录」表 |
| 5 | `isSolvent()` 恒为 false（空实现） | 依赖 DDD 匹配天然过滤，语义与注释不符 | 修正实现或删除该分支，避免误读 |
| 6 | 单次剂量取「医嘱名中最大数字」 | 药名里带规格（如「0.5g/瓶」）时可能取到非单次剂量 | 优先字段 + 人工抽检 |
| 7 | 特殊级判定用 `manage_level = '特殊'`（字符串精确匹配） | 后台录入「特殊使用级」等写法会漏判 | 归一化级别枚举 |
| 8 | 科室归属按「患者当前 depart_code」 | 转科患者的历史医嘱全归到最终科室 | 如需精确，按医嘱时间点归属科室 |

## 八、后续优化建议

1. 增加「未匹配 DDD 的医嘱 TOP 清单」与「频次未识别清单」两个数据质量看板；
2. 支持按「医嘱执行记录」精确累计剂量（替代「单次剂量 × 频次 × 天数」估算）；
3. 增加科室/医生维度的 DDD 排名与环比预警（阈值可配）；
4. 支持抗真菌药（DDD 单位 MU）与特殊级抗菌药的单独考核口径。
