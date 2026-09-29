# 第二维度：PK/PD 抗菌药物剂量优化 · 产品需求设计

## 一、维度概述

### 1.1 临床背景
ICU 患者常存在肾功能不全、肝功能异常、肥胖、低蛋白血症、体外循环（CRRT/ECMO）等特殊病理生理状态，常规剂量抗菌药物常导致：
- **剂量不足**：疗效失败、耐药菌产生、感染复发
- **剂量过量**：肾毒性、耳毒性、神经毒性、药物相互作用

PK/PD（药代动力学/药效动力学）剂量优化是 ICU 抗菌药物管理的核心环节，目标是让药物在感染部位达到并维持有效浓度。

### 1.2 临床价值
- 提高抗感染治疗成功率
- 减少耐药菌产生
- 降低药物不良反应（尤其是肾毒性）
- 缩短 ICU 住院日
- 为 TDM（治疗药物监测）提供目标值指引

### 1.3 与第一维度的关系
- 第一维度（经验性抗感染决策）回答"**用什么药**"
- 第二维度（PK/PD 剂量优化）回答"**用多少量、怎么给**"
- 两个维度共享患者数据，可从第一维度决策页一键跳转第二维度剂量页

---

## 二、核心功能模块

### 2.1 患者特殊状态评估
| 模块 | 计算内容 | 数据来源 |
|---|---|---|
| 肾功能评估 | 肌酐清除率（Cockcroft-Gault 公式）、eGFR（CKD-EPI）、肾功能分级 | patient_info（年龄/性别/体重）+ patient_info_lis_item（肌酐） |
| 肝功能评估 | Child-Pugh 分级（需手动补充胆红素/白蛋白/INR/腹水/肝性脑病） | patient_info_lis_item（胆红素/白蛋白） |
| 营养状态 | BMI、理想体重（IBW）、调整体重（AdjBW，肥胖患者用） | patient_info（身高/体重/性别） |
| 低蛋白血症 | 白蛋白 < 25g/L 标记（影响高蛋白结合率药物分布） | patient_info_lis_item（白蛋白） |
| 特殊治疗标记 | CRRT（持续肾脏替代治疗）、ECMO（体外膜肺氧合）标记 | 需从 ICU 系统获取或手动标记 |

### 2.2 当前抗菌药物 PK/PD 分析
| 分析项 | 内容 |
|---|---|
| 药物 PK/PD 分类 | 时间依赖性（β-内酰胺类）/ 浓度依赖性（氨基糖苷类/喹诺酮类）/ 时间依赖性+长PAE（万古霉素/利奈唑胺） |
| PK/PD 目标参数 | %T>MIC / Cmax/MIC / AUC/MIC |
| 当前给药方案 | 药物名称、单次剂量、给药间隔、输注方式（静推/快速静滴/延长输注/持续输注） |
| 蛋白结合率 | 药物蛋白结合率（高蛋白结合率药物在低蛋白血症时游离浓度升高） |
| 主要清除途径 | 肾脏清除 / 肝脏清除 / 双通道清除 |

### 2.3 剂量优化建议
| 建议类型 | 内容 |
|---|---|
| 肾功能剂量调整 | 根据肌酐清除率推荐调整剂量或延长给药间隔，附药品说明书肾功能剂量表 |
| 体重剂量调整 | 肥胖患者推荐按调整体重计算（AdjBW = IBW + 0.4×(实际体重-IBW)） |
| 低蛋白血症调整 | 高蛋白结合率药物（如头孢曲松、厄他培南、替考拉宁）在白蛋白<25g/L时游离浓度升高，需警惕毒性 |
| 负荷剂量建议 | 浓度依赖性药物和组织穿透力差的药物推荐首剂负荷剂量 |
| 延长输注建议 | β-内酰胺类药物推荐 3h 延长输注或持续输注，提高 %T>MIC |
| 透析患者调整 | CRRT 患者推荐剂量（需结合 CRRT 模式和剂量） |

### 2.4 TDM 目标值指引
| 药物 | TDM 目标 | 监测时机 |
|---|---|---|
| 万古霉素 | AUC/MIC ≥ 400；谷浓度 15-20 mg/L（严重感染）/ 10-15 mg/L（一般感染） | 第4剂前30min |
| 去甲万古霉素 | 同万古霉素 | 同万古霉素 |
| 替考拉宁 | 谷浓度 15-30 mg/L（骨/关节感染）/ 10-15 mg/L（一般感染） | 第3剂前 |
| 氨基糖苷类（阿米卡星） | Cmax/MIC ≥ 8；谷浓度 < 4 mg/L | 给药后30min测峰，下次给药前测谷 |
| 氨基糖苷类（庆大霉素/妥布霉素） | Cmax/MIC ≥ 8；谷浓度 < 1 mg/L | 同上 |
| 氟康唑 | 谷浓度 ≥ 目标 MIC 的2倍（隐球菌 ≥ 10mg/L） | 第4剂前 |
| 伏立康唑 | 谷浓度 1-5 mg/L（>5 毒性增加） | 第4剂前 |
| 泊沙康唑 | 谷浓度 ≥ 0.7 mg/L（预防）/ ≥ 1.25 mg/L（治疗） | 第7天 |
| 氟胞嘧啶 | 峰浓度 50-100 mg/L；谷浓度 < 25 mg/L | 给药后2h测峰 |
| 利奈唑胺 | 谷浓度 2-7 mg/L（>7 血小板减少风险） | 第4剂前 |

### 2.5 药物相互作用与配伍禁忌提醒
- 肾毒性药物叠加（氨基糖苷类 + 万古霉素 + 利尿剂 + NSAIDs）
- 蛋白结合置换（高蛋白结合率药物之间）
- CYP450 酶相互作用（伏立康唑、利奈唑胺等）
- 配伍禁忌（同一管路输注的药物相容性）

---

## 三、数据来源（ICU 系统 zing_icu_db_prod）

### 3.1 患者基本信息
- **表**：`patient_info`
- **字段**：patient_id, name, age, gender, weight, height, in_hospital_no, bed_no, department

### 3.2 检验结果
- **表**：`patient_info_lis_item`（明细）+ `patient_info_lis`（主表）
- **关键字段**：
  - 肌酐（*肌酐 / 肌酐 / Cr）→ 肾功能评估
  - 白蛋白（*白蛋白 / 白蛋白 / ALB）→ 低蛋白血症评估
  - 总胆红素（*总胆红素 / TBIL）→ 肝功能评估
  - 直接胆红素（*直接胆红素 / DBIL）
  - 谷丙转氨酶（丙氨酸氨基转移酶 / ALT）
  - 谷草转氨酶（天冬氨酸氨基转氨酶 / AST）
  - INR / 凝血酶原时间 → Child-Pugh 评估
  - 尿素氮（*尿素 / BUN）
  - 钾钠氯电解质 → 药物电解质影响

### 3.3 当前抗菌药物医嘱
- **表**：`patient_advice`（医嘱主表）+ `patient_advice_pda`（PDA执行表）
- **关联字段**：group_id_mark + in_hospital_serial_no
- **关键字段**：
  - name（药物名称，溶媒溶质分开，只取溶质）
  - freq_name（给药频次，如 q8h/q12h/qd）
  - drug_method_name（给药方式，如静脉推注/静脉输液）
  - plan_start_time（计划开始时间）
  - pda_advice_start_time / pda_advice_end_time（实际执行时间）
  - status（医嘱状态）

### 3.4 生命体征与出入量（可选，用于肾功能动态评估）
- **表**：`patient_observe_module_item_record`
- **关键字段**：尿量、血压、心率等

### 3.5 诊断信息
- **表**：`patient_info_diagnosis`
- **用途**：识别肾功能不全、肝功能不全、肥胖、低蛋白血症等诊断，辅助特殊状态标记

---

## 四、页面设计

### 4.1 页面布局（横屏铺开，同第一维度风格）

```
┌─────────────────────────────────────────────────────────────────┐
│ 患者信息横条（姓名/住院号/年龄性别/床位/病区/肾功能分级/体重/BMI）│
├──────────────┬──────────────────┬────────────────────────────────┤
│ 肾功能评估卡   │ 当前抗菌药物       │ 剂量优化建议卡                  │
│              │ PK/PD 分析卡       │                                │
│ - 肌酐清除率  │ - 药物分类         │ - 推荐剂量                     │
│ - eGFR       │ - PK/PD目标参数    │ - 调整依据                     │
│ - 肾功能分级  │ - 当前给药方案     │ - 肾功能调整表                 │
│ - 趋势图      │ - 蛋白结合率       │ - 体重调整                     │
│              │ - 清除途径          │ - 延长输注建议                 │
├──────────────┴──────────────────┼────────────────────────────────┤
│ 特殊状态标记卡                    │ TDM 目标值指引卡                │
│ - 低蛋白血症                      │ - 当前药物 TDM 目标             │
│ - 肝功能异常                      │ - 监测时机                      │
│ - 肥胖                            │ - 采样时间提醒                  │
│ - CRRT/ECMO 标记                  │                                │
├──────────────────────────────────┴────────────────────────────────┤
│ 药物相互作用与配伍禁忌提醒（底部横幅，有风险时显示）               │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 核心页面元素说明

#### 肾功能评估卡
- 大数字显示肌酐清除率（Cockcroft-Gault）和 eGFR（CKD-EPI）
- 肾功能分级标签：正常（≥90）/ 轻度下降（60-89）/ 中度下降（30-59）/ 重度下降（15-29）/ 肾衰竭（<15）
- 肌酐趋势图（最近7天，带参考范围虚线）
- 计算公式展示（鼠标悬停显示）

#### 当前抗菌药物 PK/PD 分析卡
- 药物列表（同第一维度当前抗菌药模块，结构化展示）
- 每个药物显示：PK/PD 分类标签、目标参数、当前剂量/间隔/输注方式
- 时间依赖性药物标记"建议延长输注"
- 浓度依赖性药物标记"建议负荷剂量"

#### 剂量优化建议卡
- 推荐方案：药物 + 单次剂量 + 给药间隔 + 输注方式
- 调整依据：基于肌酐清除率/体重/低蛋白血症的具体计算过程
- 肾功能剂量调整表（当前药物的肾功能分级对应剂量表）
- 证据来源：药品说明书 / 指南推荐

#### TDM 目标值指引卡
- 当前药物的 TDM 目标值（峰浓度/谷浓度/AUC/MIC 比值）
- 推荐监测时机（第N剂前30min）
- 下次采样时间倒计时（基于当前给药时间计算）
- 结果解读标准（达标/不足/过量）

---

## 五、后端接口设计

### 5.1 核心接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/antibiotic/patients/{patientId}/pkpd` | GET | 获取患者 PK/PD 剂量优化完整数据 |
| `/api/antibiotic/patients/by-no/pkpd` | GET | 按住院号获取（外链用） |
| `/api/antibiotic/drugs/{drugName}/renal-dose` | GET | 获取指定药物的肾功能剂量调整表 |
| `/api/antibiotic/drugs/{drugName}/tdm-target` | GET | 获取指定药物的 TDM 目标值 |

### 5.2 核心 DTO

```java
// PK/PD 评估视图
public class PkpdAssessmentView {
    private IcuPatientBrief patient;           // 患者基本信息
    private RenalAssessment renal;             // 肾功能评估
    private LiverAssessment liver;             // 肝功能评估
    private NutritionAssessment nutrition;     // 营养状态（BMI/IBW/AdjBW）
    private List<AbxCurrentItem> currentAbx;   // 当前抗菌药物列表
    private List<PkpdDrugAnalysis> drugAnalysis; // 各药物 PK/PD 分析
    private List<DoseRecommendation> recommendations; // 剂量优化建议
    private List<TdmTarget> tdmTargets;        // TDM 目标值
    private List<DrugInteraction> interactions; // 药物相互作用
    private List<String> specialFlags;          // 特殊状态标记
}

// 肾功能评估
public class RenalAssessment {
    private BigDecimal creatinine;       // 最新肌酐值
    private String creatinineUnit;       // 单位
    private BigDecimal crcl;             // 肌酐清除率（Cockcroft-Gault）
    private BigDecimal egfr;             // eGFR（CKD-EPI）
    private String renalStage;           // 肾功能分级
    private List<LabTrendPoint> creatinineTrend; // 肌酐趋势
}

// 药物 PK/PD 分析
public class PkpdDrugAnalysis {
    private String drugName;             // 药物名称
    private String pkpdType;             // PK/PD 分类：time-dependent / concentration-dependent / time-dependent-long-pae
    private String targetParam;          // 目标参数：%T>MIC / Cmax/MIC / AUC/MIC
    private String targetValue;          // 目标值
    private BigDecimal proteinBinding;   // 蛋白结合率（%）
    private String clearanceRoute;       // 清除途径：renal / hepatic / dual
    private String currentDose;          // 当前剂量
    private String currentInterval;      // 当前给药间隔
    private String infusionMethod;       // 输注方式
}

// 剂量优化建议
public class DoseRecommendation {
    private String drugName;
    private String recommendedDose;      // 推荐剂量
    private String recommendedInterval;  // 推荐间隔
    private String recommendedInfusion;  // 推荐输注方式
    private String adjustmentReason;     // 调整依据
    private String evidenceSource;       // 证据来源
    private List<RenalDoseRow> renalDoseTable; // 肾功能剂量调整表
}
```

---

## 六、计算逻辑与规则引擎

### 6.1 肌酐清除率（Cockcroft-Gault 公式）
```
男性：CrCl = [(140 - 年龄) × 体重(kg)] / [72 × 血肌酐(mg/dL)]
女性：CrCl = 男性结果 × 0.85
```
- 注意：肌酐单位换算（μmol/L → mg/dL，除以 88.4）
- 肥胖患者用理想体重（IBW）或调整体重（AdjBW）

### 6.2 理想体重（IBW）
```
男性：IBW = 50 + 2.3 × (身高英寸 - 60)
女性：IBW = 45.5 + 2.3 × (身高英寸 - 60)
```

### 6.3 调整体重（AdjBW，肥胖患者用）
```
AdjBW = IBW + 0.4 × (实际体重 - IBW)
```
- 适用：实际体重 > 1.3 × IBW 的肥胖患者

### 6.4 eGFR（CKD-EPI 公式）
- 根据性别、种族、肌酐、年龄计算
- 用于肾功能分级（KDIGO 标准）

### 6.5 PK/PD 分类与目标
| 分类 | 代表药物 | PK/PD 参数 | 目标值 |
|---|---|---|---|
| 时间依赖性 | 青霉素类、头孢菌素类、碳青霉烯类、单环β-内酰胺类 | %T>MIC | ≥50%（一般）/ ≥70-100%（严重感染/脓毒症） |
| 浓度依赖性 | 氨基糖苷类、氟喹诺酮类、达托霉素 | Cmax/MIC、AUC/MIC | Cmax/MIC ≥ 8-10；AUC/MIC ≥ 100-125 |
| 时间依赖性+长PAE | 万古霉素、替考拉宁、利奈唑胺、四环素类 | AUC/MIC | 万古霉素 AUC/MIC ≥ 400 |

### 6.6 肾功能剂量调整规则
1. 肾脏清除为主的药物（氨基糖苷类、万古霉素、β-内酰胺类大部分）需根据 CrCl 调整
2. 肝脏清除为主的药物（利奈唑胺、替加环素、莫西沙星）肾功能不全无需调整
3. 双通道清除的药物（头孢曲松、哌拉西林他唑巴坦）轻中度肾功能不全无需调整，重度需调整

### 6.7 延长输注规则
- β-内酰胺类药物（时间依赖性）推荐 3h 延长输注
- 脓毒症/感染性休克患者推荐持续输注
- 头孢他啶、头孢吡肟、哌拉西林他唑巴坦、美罗培南、亚胺培南均支持延长输注

---

## 七、药物知识库（内置，后续可维护到数据库）

### 7.1 常用 ICU 抗菌药物 PK/PD 参数表
| 药物 | 分类 | 蛋白结合率 | 清除途径 | 常规剂量 | 肾功能调整 | TDM |
|---|---|---|---|---|---|---|
| 美罗培南 | 时间依赖 | 2% | 肾 | 1g q8h | CrCl<50 调整 | 不常规 |
| 亚胺培南 | 时间依赖 | 20% | 肾 | 0.5g q6h | CrCl<70 调整 | 不常规 |
| 哌拉西林他唑巴坦 | 时间依赖 | 30% | 肾 | 4.5g q6h | CrCl<40 调整 | 不常规 |
| 头孢他啶 | 时间依赖 | 17% | 肾 | 2g q8h | CrCl<50 调整 | 不常规 |
| 头孢吡肟 | 时间依赖 | 20% | 肾 | 2g q8h | CrCl<60 调整 | 不常规 |
| 头孢曲松 | 时间依赖 | 95% | 肝肾双通道 | 2g qd | 肾功能不全无需调整 | 不常规 |
| 万古霉素 | 时间依赖+长PAE | 55% | 肾 | 15-20mg/kg q12h | 按 CrCl 调整间隔 | 谷浓度/AUC |
| 去甲万古霉素 | 同上 | 同上 | 肾 | 同上 | 同上 | 同上 |
| 替考拉宁 | 同上 | 90% | 肾 | 负荷 400mg q12h×3，维持 400mg qd | CrCl<50 调整 | 谷浓度 |
| 利奈唑胺 | 时间依赖+长PAE | 31% | 肝 | 600mg q12h | 肾功能不全无需调整 | 谷浓度 |
| 阿米卡星 | 浓度依赖 | 4% | 肾 | 15mg/kg qd | 按 CrCl 调整 | 峰/谷浓度 |
| 庆大霉素 | 浓度依赖 | 30% | 肾 | 5-7mg/kg qd | 按 CrCl 调整 | 峰/谷浓度 |
| 左氧氟沙星 | 浓度依赖 | 38% | 肾 | 750mg qd | CrCl<50 调整 | 不常规 |
| 莫西沙星 | 浓度依赖 | 45% | 肝 | 400mg qd | 肾功能不全无需调整 | 不常规 |
| 氟康唑 | 浓度依赖 | 12% | 肾 | 400mg qd（负荷800mg） | CrCl<50 减半 | 谷浓度 |
| 伏立康唑 | 浓度依赖 | 58% | 肝 | 负荷 400mg q12h×2，维持 200mg q12h | 肾功能不全无需调整（口服） | 谷浓度 |
| 卡泊芬净 | 时间依赖 | 97% | 肝 | 负荷 70mg，维持 50mg qd | 肾功能不全无需调整 | 不常规 |
| 米卡芬净 | 时间依赖 | 99% | 肝 | 100-150mg qd | 肾功能不全无需调整 | 不常规 |
| 替加环素 | 时间依赖+长PAE | 80% | 肝/胆汁 | 负荷 100mg，维持 50mg q12h | 肾功能不全无需调整 | 不常规 |
| 多粘菌素B | 浓度依赖 | 高 | 肾 | 负荷 2.5mg/kg，维持 1.25-1.5mg/kg q12h | 按 CrCl 调整 | 不常规 |
| 复方磺胺甲噁唑 | 时间依赖 | 70% | 肾 | 15-20mg/kg q6h（按TMP） | CrCl<30 调整 | 不常规 |

---

## 八、P0 实施范围（第二维度 MVP）

### 8.1 必做（MVP）
1. 肾功能评估（肌酐清除率 Cockcroft-Gault + eGFR + 分级 + 肌酐趋势图）
2. 当前抗菌药物 PK/PD 分类展示（基于药物知识库匹配）
3. 剂量优化建议（肾功能调整 + 延长输注建议 + 负荷剂量建议）
4. TDM 目标值展示（基于药物知识库）
5. 患者信息横条（含体重/BMI/肾功能分级）
6. 外链支持（同第一维度，ICU 系统外链打开）

### 8.2 后续迭代（P1+）
1. 肝功能评估（Child-Pugh，需手动补充部分字段）
2. 肥胖患者调整体重计算
3. 低蛋白血症对高蛋白结合率药物的影响分析
4. CRRT/ECMO 患者剂量调整
5. 药物相互作用自动检测
6. 实际 TDM 结果录入与达标判断
7. 剂量计算器（交互式，用户可输入 MIC 值计算目标剂量）
8. 药物知识库维护界面（可后台维护药物 PK/PD 参数）

---

## 九、与第一维度的页面跳转

- 第一维度决策页 → 点击"剂量优化"按钮 → 跳转第二维度 PK/PD 页（携带 patientId/inHospitalNo）
- 第二维度 PK/PD 页 → 点击"返回决策"按钮 → 跳转第一维度决策页
- 两个页面共享外链 token 和患者上下文
