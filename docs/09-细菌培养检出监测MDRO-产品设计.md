# 细菌培养检出监测（MDRO 风险预警）· 产品设计方案

> 版本：设计稿 v1 · 2026-09-13
> 定位：第四维度。对 ICU 细菌培养送检与阳性检出做结构化统计（送检率、阳性率、菌谱排名、革兰分类、标本分布、高风险菌预警），为院感防控与经验性抗感染用药提供科室级、患者级视图。

## 一、背景与定位

### 1.1 解决什么问题

1. **送检率不达标**：抗菌药物使用前微生物送检是等级评审与抗菌药物管理的核心指标，需要科室级量化。
2. **菌谱不清**：科室经验性用药应基于本地菌谱（常见菌、革兰分布、标本来源），而不是凭经验。
3. **MDRO 需要早期预警**：CRE、CRKP、MRSA、鲍曼不动杆菌、铜绿假单胞菌等高风险菌一旦检出，应尽快被医生看到（接触隔离、调整用药）。

### 1.2 重要边界（必须明确）

> ICU 库中 `patient_info_lis_item.lis_item_result` 只存**细菌名称**，**不含耐药关键词、不含药敏结果**（药敏明细未接入）。

因此本模块当前是 **「细菌培养检出监测」**，不是严格意义的 **MDRO（多重耐药菌）判定**：

- **能做的**：细菌分类（革兰阳性/阴性/真菌）、菌株排名、标本分布、**依据配置表的高风险菌标记与预警**；
- **不能做的**：自动判定某菌株是否为 MRSA / CRE / CRPA / CRAB（需要药敏结果：如对苯唑西林耐药、对碳青霉烯类耐药）；
- **落地方式**：以「**高风险菌种清单**」（如碳青霉烯类耐药高发菌种、耐甲氧西林金黄色葡萄球菌等）作为 MDRO 风险的**代理指标**，明确标注为「风险提示」而非确诊。

## 二、功能清单

| 功能 | 说明 |
|---|---|
| 送检与检出总览 | 总患者数、送检患者数/送检份数、阳性患者数/阳性份数、培养阳性率、高风险菌患者数与占比 |
| 菌株排名 TOP20 | 菌名、检出次数、患者数、占比、革兰分类、是否高风险、最常见标本、首/末次检出时间 |
| 革兰分类分布 | 革兰阳性菌 / 革兰阴性菌 / 真菌 / 其他（剔除非细菌真菌结果） |
| 标本类型分布 | 阳性标本类型 TOP10 及占比 |
| 月度趋势 | 逐月送检患者数、阳性患者数、阳性率、高风险数、革兰分类计数 |
| 患者明细 | 每患者：在科天数、送检次数、阳性次数、阳性率、菌种数、是否含高风险菌、首/末次阳性时间 |
| 高风险菌预警 | 筛出「含高风险菌」的患者清单，供交班与感控使用 |
| 配置页 | 细菌分类词库（革兰分类关键词）、高风险菌清单、标本类型，均可维护 |

## 三、判定规则

### 3.1 送检记录纳入（SQL 层，`selectAllBacteriaCultureForStats`）

```sql
FROM patient_info_lis_item li
INNER JOIN patient_info_lis l      ON l.lis_code = li.lis_code AND l.del_flag = 0
INNER JOIN patient_info  pi        ON pi.in_hospital_no = li.in_hospital_no AND pi.del_flag = 0
WHERE li.del_flag = 0 AND li.status = 1
  AND li.lis_item_name LIKE '细菌培养+药敏%'     -- 含普通瓶与厌氧瓶
  AND li.check_time >= #{startTime} AND li.check_time < #{endTime}
  AND pi.in_depart_time < #{endTime}
  AND (pi.out_depart_time >= #{startTime} OR pi.out_depart_time IS NULL)
  AND pi.depart_code = #{departCode}
```

- **时间口径**：以**检验时间 `check_time`** 落在区间为准（不是出科时间），并要求患者在科时段与统计周期有交集（`in_depart_time < 周期结束` 且 `出科时间 >= 周期开始` 或仍在科）。
- 送检份数 = 「细菌培养+药敏」项目条数（每瓶为一条）。

### 3.2 阳性判定（`selectBacteriaCultureForStats`）

在上面的基础上追加：

```sql
AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> ''
AND li.lis_item_result <> '未检出'
AND li.lis_item_result <> '无细菌生长'
AND li.lis_item_result <> '阴性'
```

### 3.3 结果清洗（`isNonBacteriaFungi`，剔除「不是真细菌/真菌」的结果）

命中以下任一条件即剔除（不计入革兰分类、菌株排名、标本分布）：

1. 同时包含**数字 + "天"**：正则 `.*\d+.*天.*`，如「厌氧培养5天未检出细菌」「需氧培养5天未检出细菌」；
2. 包含 **"生长"**：如「无细菌生长」「杂菌生长」。

另有 `NEGATIVE_KEYWORDS`（未检出、无细菌生长、阴性、无致病菌、正常菌群、未生长、无菌生长）与 `NON_BACTERIA_FUNGI_KEYWORDS`（杂菌、无沙门菌、无志贺菌、菌群失调、需氧、厌氧…）。

> ⚠️ 注意：结果值里凡带「生长」二字一律剔除。若某医院微生物室把阳性结果写成「肺炎克雷伯菌生长」，会被误剔。上线前必须核对本院报告文本格式。

### 3.4 革兰分类（`MdroConfigService#matchBacteriaClass`）

- 数据来源：`zing_mdro_config` 中 `config_type='bacteria_class'` 且 `status=1` 的记录；
- 匹配顺序：**先精确等于 `bacteria_name`，再按 `keywords`（逗号分隔）逐个 `contains` 模糊匹配**；
- 返回值：`gram_positive` / `gram_negative` / `fungi` / `other`（未命中即 `other`，**仍计入统计**）。

### 3.5 高风险菌（`isHighRiskBacteria`）

- 数据来源：`zing_mdro_config` 中 `config_type='high_risk'` 且 `status=1` 的记录；
- 匹配规则同 3.4（精确 → 关键词 contains）；
- 种子数据覆盖：耐甲氧西林金黄色葡萄球菌、碳青霉烯类耐药肠杆菌（CRE/KPC/NDM）、耐碳青霉烯鲍曼不动杆菌、耐碳青霉烯铜绿假单胞菌、产 ESBL 大肠埃希菌/肺炎克雷伯菌、嗜麦芽窄食单胞菌、白色念珠菌以外的耐药念珠菌、粪肠球菌（VRE 相关）等。

### 3.6 指标定义

| 指标 | 公式 |
|---|---|
| 培养阳性率（总览） | 阳性**患者数** ÷ 送检**患者数** × 100% |
| 高风险菌占比 | 高风险菌**患者数** ÷ 阳性**患者数** × 100% |
| 革兰分类占比 | 该分类检出**条数** ÷ 全部真细菌真菌**条数** × 100% |
| 标本类型占比 | 该标本阳性条数 ÷ 阳性总条数 × 100% |
| 菌株排名占比 | 该菌检出条数 ÷ 全部真细菌真菌条数 × 100% |
| 患者阳性率 | 该患者阳性次数 ÷ 该患者送检次数 × 100% |
| 在科天数 | max(入科, 周期开始) → min(出科, 周期结束, 当前时间)，**按日期差计算（含首日）**，与 DDD 模块的展示口径一致 |

> ⚠️ 「培养阳性率」总览用**患者数**作分子分母，患者明细用**次数**，两个口径不同，UI 需标注清楚。

### 3.7 患者筛选与去重

- 按 `in_hospital_no` 分组聚合；患者的在科时段必须与统计周期有交集（`isInDepartOverlap`），否则跳过；
- 菌种去重按**细菌名称**分组（同一患者同一菌名多条记录 = 1 个菌种，检出次数累加）；
- 明细排序：按最近阳性时间降序。

## 四、数据源映射

| 数据 | 表 | 关键字段 |
|---|---|---|
| 检验明细（菌名） | `patient_info_lis_item` | `lis_code, lis_item_name, lis_item_result（=菌名）, check_time, in_hospital_no, del_flag, status` |
| 检验主表（标本） | `patient_info_lis` | `lis_short_name（标本类型）, lis_name, lis_order_ward_name, lis_order_doctor_name` |
| 患者信息 | `patient_info` | `name, gender, age, ward_name, bed_code, in_depart_time, out_depart_time, is_in_depart, depart_code` |
| 科室下拉 | `sys_depart` | `selectAllDepartments()` |
| 分类/高风险配置 | `zing_mdro_config` | `config_type, bacteria_name, bacteria_class, is_high_risk, keywords` |

## 五、配置表（`zing_mdro_config`）

```sql
CREATE TABLE "zing_doctor_db_prod"."zing_mdro_config" (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    config_type    VARCHAR(32),   -- bacteria_class 细菌分类 / high_risk 高风险菌 / specimen 标本类型
    bacteria_name  VARCHAR(128),  -- 细菌名称（精确匹配用）
    bacteria_class VARCHAR(32),   -- gram_positive / gram_negative / fungi / other
    is_high_risk   TINYINT,       -- 1 是 0 否
    keywords       VARCHAR(500),  -- 关键词（逗号分隔，模糊匹配）
    remark         VARCHAR(500),
    status         TINYINT DEFAULT 1 NOT NULL
);
```

- 种子数据按 ICU 常见菌谱录入（革兰阳性：金黄色葡萄球菌、表皮葡萄球菌、肠球菌、链球菌等；革兰阴性：肺炎克雷伯菌、大肠埃希菌、铜绿假单胞菌、鲍曼不动杆菌、阴沟肠杆菌、嗜麦芽窄食单胞菌等；真菌：白色念珠菌、光滑念珠菌、热带念珠菌、曲霉等）；
- 维护入口：`/page/mdro-config` 页面，配置**即时生效**（每次统计实时查配置）。

## 六、后端接口（`/api/antibiotic/mdro`）

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `/departments` | — | 科室下拉 |
| GET | `/overview` | `startTime, endTime, departCode` | 总览：送检/阳性患者数、阳性率、分类分布、标本分布、高风险患者数与占比 |
| GET | `/bacteria-rank` | `startTime, endTime, departCode` | 菌株排名 TOP20 |
| GET | `/monthly-trend` | `startTime, endTime, departCode` | 月度趋势 |
| GET | `/patients` | `startTime, endTime, departCode` | 患者明细列表 |
| GET | `/patient/{inHospitalNo}` | `startTime, endTime` | 单患者明细（菌种明细、标本、检出次数） |
| GET | `/specimen-distribution` | `startTime, endTime, departCode` | 标本类型分布 |
| GET | `/high-risk-alerts` | `startTime, endTime, departCode` | 高风险菌患者预警列表 |
| GET | `/config/list` `/config/by-type` `/config/by-class` `/config/high-risk` `/config/classes` | — | 配置查询 |
| POST | `/config/add` `/config/update` `/config/toggle` | Body / `id,status` | 配置维护 |

## 七、前端页面

| 页面 | 路径 | 内容 |
|---|---|---|
| 检出总览（`MdroOverview.vue`） | `/page/mdro-overview` | 筛选、指标卡（送检患者/阳性患者/阳性率/高风险占比）、革兰分类饼图、标本类型分布、月度趋势、菌株 TOP20 表、高风险预警条 |
| 患者明细（`MdroPatients.vue`） | `/page/mdro-patients` | 患者列表（在科天数、送检/阳性次数、阳性率、菌种数、高风险标识），展开查看菌种明细 |
| 配置页（`MdroConfig.vue`） | `/page/mdro-config` | 细菌分类词库、高风险菌清单、标本类型维护 |

## 八、风险与已知限制

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | **无药敏结果，无法真正判定 MDRO** | 「高风险菌」≠「多重耐药菌」，可能与感控口径不一致 | 接入药敏明细后实现真正 MDRO 判定（MRSA/CRE/CRPA/CRAB/VRE…）；当前对外文案统一用「高风险菌」 |
| 2 | 含「生长」二字的结果被剔除 | 若本院报告阳性写作「XX菌生长」，阳性结果全部丢失 | 上线前核对报告文本；建议改为只在包含阴性前缀（无/未/杂）时才剔除 |
| 3 | 总览阳性率按患者数、明细按次数 | 两个数字对不上，易被质疑 | UI 明确标注口径，或统一为次数口径 |
| 4 | 阳性判定依赖 `lis_item_result` 文本 | 报告里若一条记录含多种菌（合并报告），会整体当成一个「菌名」 | 需按分隔符拆分多种菌 |
| 5 | 分类匹配依赖配置关键词顺序 | 关键词冲突（如「球菌」）可能误分类 | 配置页约束 + 精确名优先（已实现） |
| 6 | 无「同一患者同一菌种 N 天内算一次」的去重 | 同一感染反复培养会重复计数，虚高检出量 | 增加「按菌种+时间窗去重」的患者级指标 |
| 7 | 模块名为 MDRO 但语义为检出监测 | 汇报/评审时表述风险 | 文档与页面文案区分「检出监测」与「MDRO 判定」 |
| 8 | 统计口径按「检验时间」而 DDD 按「出科时间」 | 两维度数字不可直接相加对比 | 说明口径差异 |

## 九、后续优化建议

1. **接入药敏结果**（`patient_info_lis_item` 药敏子项或独立药敏表），实现真正的 MDRO 判定：MRSA、VRE、CRE、CRPA、CRAB、产 ESBL 等；
2. 增加「**首次检出即预警**」与「**隔离医嘱未开**」联动提示；
3. 增加医院感染（HAI）判定：入院 48h 后检出 vs 院外带入；
4. 增加「抗菌药物使用前送检率」考核指标（与 DDD/抗感染决策模块联动）。
