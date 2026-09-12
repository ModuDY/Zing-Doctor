# ARDS 监测 · 产品设计方案

> 版本：设计稿 v1 · 2026-09-13
> 定位：面向 ICU 的**急性呼吸窘迫综合征（ARDS）**筛查、分级与肺保护性通气依从性监测，帮助医师快速发现 ARDS 患者并量化通气策略是否达标。

## 一、背景与定位

### 1.1 解决什么问题

1. **漏诊**：ARDS 的柏林定义依赖「氧合指数 + PEEP + 起病时间 + 双肺浸润 + 排除心源性肺水肿」，临床逐条核对成本高，容易漏诊或延迟诊断。
2. **通气策略不量化**：肺保护性通气（小潮气量、合适 PEEP、限制平台压/驱动压）是唯一被证实可降低 ARDS 病死率的措施，但**是否达标**缺乏科室级、患者级的量化视图。
3. **看不到趋势**：ARDS 是动态过程，需要观察氧合指数与呼吸机参数随时间的变化。

### 1.2 与其它模块的关系

| 模块 | 关系 |
|---|---|
| SOFA 评分 | SOFA 呼吸项（PaO₂/FiO₂）与本模块共用「氧合指数」数据源，可互相印证 |
| 第一维度 · 抗感染决策 | 重症肺炎/ARDS 常伴随感染，决策详情页可跳转 |
| 交班览表 | 危重患者（含 ARDS）进入交班重点关注列表 |

## 二、功能清单

| 功能 | 说明 |
|---|---|
| ARDS 总览 | 按科室 + 出科时间范围统计：ARDS 患者数、轻/中/重/未知分级分布、平均氧合指数、肺保护性通气 4 项依从率 |
| 患者列表 | 逐患者展示床号、姓名、住院号、入/出科时间、ARDS 分级、氧合指数、FiO₂、PEEP、潮气量、潮气量/kg、通气天数 |
| 呼吸机参数趋势 | 单患者按时间轴展示潮气量、PEEP、FiO₂、呼吸频率、分钟通气量的变化曲线 |
| 氧合指数历史 | 单患者所有血气时间点的氧合指数列表 |
| 分级筛选 | 按「轻度 / 中度 / 重度 / 未知」过滤患者列表 |

## 三、判定规则

### 3.1 ARDS 患者纳入（SQL 层）

`IcuPatientMapper#selectArdsPatients`：

```sql
FROM patient_info pi
WHERE pi.del_flag = 0
  AND pi.depart_code = #{departCode}
  AND pi.out_depart_time >= #{startTime} AND pi.out_depart_time < #{endTime}
  AND EXISTS (SELECT 1 FROM patient_info_diagnosis pid
              WHERE pid.in_hospital_no = pi.in_hospital_no AND pid.del_flag = 0
                AND (pid.diag_name LIKE '%急性呼吸窘迫综合征%' OR pid.diag_name LIKE '%ARDS%'))
ORDER BY pi.out_depart_time DESC
```

- 以**出科时间**落在查询区间为口径（与 DDD/MDRO/出科统计一致，便于同口径对比）。
- 疾病认定：诊断名称包含「急性呼吸窘迫综合征」或「ARDS」（`patient_info.is_ards` 字段保留但当前未作为筛选条件，仅随结果返回）。

### 3.2 氧合指数分级（柏林定义简化版）

取患者**最新一条**血气记录的氧合指数（`selectOxygenationHistory` 的最后一条），按下表分级：

| 分级 | 氧合指数 PaO₂/FiO₂ (mmHg) |
|---|---|
| 轻度 | > 200 且 ≤ 300 |
| 中度 | > 100 且 ≤ 200 |
| 重度 | > 0 且 ≤ 100 |
| 未知 | 无血气数据，或氧合指数 ≤ 0、> 300（未达 ARDS 阈值） |

> 说明：当前实现**只用氧合指数一项**做分级，未强制校验 PEEP ≥ 5 cmH₂O、起病时间、双肺浸润与心源性肺水肿排除；PEEP 值单独展示在列表中，由医师人工复核柏林定义。这是「筛查提示」而非「确诊」，产品定位上不做自动确诊。

### 3.3 肺保护性通气依从性（4 项）

| 指标 | 达标条件 | 说明 |
|---|---|---|
| 潮气量 Vt | 潮气量/kg **≤ 6.0 ml/kg** | 分母为**理想体重**，非实际体重 |
| PEEP | **≥ 5.0 cmH₂O** | 柏林定义最低 PEEP 要求 |
| FiO₂ | **≤ 60%** | 兼容两种录入口径：百分比 `60` 与小数 `0.6`（`fio2 <= 60 || (fio2 <= 1 && fio2 > 0)`） |
| 呼吸频率 RR | **≤ 35 次/分** | 防止为维持分钟通气量而过高频率 |

- **依从率** = 达标患者数 ÷ **有呼吸机数据的患者数**（`vent_data_count`，4 项参数至少命中 1 项的患者），不是除以全部 ARDS 患者，避免把「未上机/无数据」算成不达标。
- 4 项全达标（`all_ok`）用于患者级标识。

### 3.4 理想体重（Vt/kg 的分母）

`ArdsServiceImpl#calcIdealWeight`，按 ARDSnet 标准公式：

```
男：理想体重(kg) = 50 + 0.91 × (身高cm − 152.4)
女：理想体重(kg) = 45.5 + 0.91 × (身高cm − 152.4)
```

降级链（逐级兜底，并在返回值中通过 `ideal_weight_estimated=true` 标记「非公式计算」）：

1. 有身高 → 按公式计算；
2. 身高为空但有实际体重 → 用**实际体重**代替；
3. 都为空 → 默认 **男 70kg / 女 60kg**。

### 3.5 通气天数

```
通气天数 = ceil( (出科时间 ?? 当前时间 − 入科时间) / 24小时 )
```

无入科时间返回 0。注意：**当前实现是「入科时长」的近似**，并未与呼吸机开关机记录关联，上机时间晚于入科时会被高估。

## 四、数据源映射

| 数据 | ICU 表 | 关键字段 / 方法 |
|---|---|---|
| ARDS 患者基本信息 | `patient_info` | `id, in_hospital_no, name, bed_code, gender, height, weight, in_depart_time, out_depart_time, is_ards` |
| ARDS 诊断认定 | `patient_info_diagnosis` | `diag_name LIKE '%急性呼吸窘迫综合征%' OR '%ARDS%'` |
| 呼吸机参数（最新值） | `patient_observe_module_item_record` | `item_code` ∈ `oi_呼末潮气量`、`oi_peep`、`oi_FiO2(设置值)`、`oi_呼吸频率(设置值)`、`oi_呼末分钟通气量`；按 `patient_id + item_code` 用 `ROW_NUMBER() OVER (PARTITION BY ... ORDER BY item_time DESC)` 取最新一条 |
| 氧合指数 | `patient_info_lis_item` | `selectOxygenationHistory(patientId)`（血气报告内 ICU 已算好的「氧合指数」） |
| 呼吸机趋势 | 同上 | `selectVentilatorTrend(patientId, startTime, endTime)` |

## 五、后端接口

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| GET | `/api/ards/overview` | `departCode`、`startTime`、`endTime`、`grade`（可空，"全部"/空=不筛选） | `{ summary: {total, mild, moderate, severe, unknown_grade, avg_oxygenation_index, vent_data_count, vt_compliance_rate, peep_compliance_rate, fio2_compliance_rate, rr_compliance_rate}, patients: [...] }` |
| GET | `/api/ards/patient/{patientId}/ventilator-trend` | `startTime`、`endTime` | 呼吸机参数时间序列 |
| GET | `/api/ards/patient/{patientId}/oxygenation-history` | — | 氧合指数历史列表 |

- `avg_oxygenation_index` 为**有氧合指数患者**的平均值（1 位小数）；无数据返回 `null`。
- 依从率均为百分比数值（1 位小数），分母为 0 时返回 0。
- 所有接口返回 `Result<T>` 统一包装，异常时 `code=fail` 并带错误信息（内部异常不抛出到前端堆栈）。

## 六、前端页面（`/page/ards-monitor`）

```
┌─ 筛选区：科室 + 时间范围 + ARDS 分级 ───────────────────────────────┐
├─ 统计卡：ARDS患者数 | 分级分布(轻/中/重/未知) | 肺保护通气依从率 | 平均氧合指数 ─┤
├─ 患者表格（行点击展开/弹窗） ────────────────────────────────────┤
│  床号 | 姓名 | 住院号 | 入科时间 | 出科时间 | 分级 | 氧合指数 | FiO2 | PEEP |
│  潮气量(ml) | 潮气量/kg | 通气天数                                       │
├─ 患者详情弹窗 ────────────────────────────────────────────────┤
│  ① 呼吸机参数趋势（可切换 潮气量/PEEP/FiO2/呼吸频率/分钟通气量 折线图）  │
│  ② 肺保护性通气达标表：指标 | 当前值 | 目标值 | 状态                  │
└──────────────────────────────────────────────────────────┘
```

- 分级用颜色区分（轻度/中度/重度），潮气量/kg 超标与依从率低值用警示色提示。
- 页面通过外链参数（`departCode / inHospitalNo / username / realname`）接收外层重症系统的上下文，支持独立外链访问。

## 七、配置项

本模块无独立配置表与配置项，全部口径由代码常量决定（阈值 `6.0 / 5.0 / 60 / 35`、理想体重公式系数）。若需科室自定义阈值，需新增配置表。

## 八、风险与已知限制

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | 只用氧合指数分级，未校验 PEEP、起病时间、双肺浸润、心源性肺水肿排除 | 可能把非 ARDS（如术后低氧）标为 ARDS | 定位为筛查；后续可加 PEEP ≥5 与「起病 1 周内」校验 |
| 2 | 氧合指数取「最新一条」而非「最差值」 | 与 APACHE II/SOFA 的「窗口内最差值」口径不一致，可能低估严重度 | 建议统一为窗口内最差值（与 SOFA 一致） |
| 3 | 通气天数按入科时长估算 | 高估未上机患者 | 接入呼吸机开关机记录后精确计算 |
| 4 | `fiO2 <= 60 || fiO2 <= 1` 判定 | 若录入 `6`（表示 6%）会误判达标 | 录入侧约束 + 展示原始值人工复核 |
| 5 | 呼吸机参数只取「每个 item 最新一条」 | 若最新一条是错误值/未设置值，会影响依从率 | 可考虑取窗口内中位数或众数 |
| 6 | 无 PEEP/FiO₂ 阶梯表联动评估 | 无法评估 ARDSnet 表格化策略 | 后续增强 |
