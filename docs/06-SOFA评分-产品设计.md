# SOFA 评分（序贯器官衰竭评估）· 产品设计方案

> 版本：设计稿 v1 · 2026-09-11
> 变更：2026-09-15 —— ① 自动评分改为「入科满 24h 后**终身一条**」（与 APACHE II 对齐）；② 定时任务 cron 由「每 6 小时一次（01/07/13/19）」降为 **每天 01:00**；③ 「SOFA 评分历史趋势」功能下线（前端入口与弹窗删除，后端 `metricKey=total` 分支删除）
> 定位：在现有 APACHE II 评分基础上，新增 SOFA 器官功能评估，服务于 **Sepsis-3 脓毒症判定**与病情严重度评价。

## 一、背景与定位

### 1.1 为什么需要 SOFA（与 APACHE II 的分工）

| 维度 | APACHE II（已实现） | SOFA（本方案） |
|---|---|---|
| 用途 | 入科病情严重度分级、**院内死亡风险预测** | **器官功能评估**、脓毒症判定 |
| 时点 | 入科时一次（一人一条） | 入科满 24h 自动一次（**终身一条**）；医生可手动补评 |
| 取值口径 | 取数窗口内最差值 | 取数窗口内最差值（默认过去 24h） |
| 分值 | 0~71（A+B+C+D） | 0~24（6 器官各 0~4） |

### 1.2 与现有模块的联动

- **脓毒症集束化（`sepsis-bundle`）**：SOFA 是 Sepsis-3 判定核心——「疑似感染 + SOFA 较基线升高 ≥2 分」即脓毒症；集束化页可引用 SOFA 结果。⚠️ 自动评分改为终身一条后，ΔSOFA 不再由定时任务自动产生，需医生手动补评才能形成基线 + 复评序列。
- **第一维度（经验性抗感染决策）**：决策页可显示当前 SOFA 作为治疗强度参考。
- **ARDS 监测**：SOFA 呼吸项与 ARDS 分级共用「氧合」数据，可互相跳转。
- **APACHE II 评分页**：同患者可在两套评分间一键切换。

## 二、评分标准（权威阈值表）

6 个器官系统，每项 0~4 分，总分 **0~24 分**（数值越高器官衰竭越重）。

### 2.1 呼吸（PaO₂/FiO₂，mmHg）

| 分值 | 条件 |
|---|---|
| 0 | ≥ 400 |
| 1 | < 400 |
| 2 | < 300 |
| 3 | < 200 **且** 有呼吸支持 |
| 4 | < 100 **且** 有呼吸支持 |

> 呼吸支持：机械通气（含 CPAP）。取数窗口内**最低** PaO₂/FiO₂（最差）。

### 2.2 凝血（血小板，×10³/µL）

| 分值 | 条件 |
|---|---|
| 0 | ≥ 150 |
| 1 | < 150 |
| 2 | < 100 |
| 3 | < 50 |
| 4 | < 20 |

### 2.3 肝（总胆红素）

| 分值 | mg/dL | µmol/L |
|---|---|---|
| 0 | < 1.2 | < 20 |
| 1 | 1.2–1.9 | 20–32 |
| 2 | 2.0–5.9 | 33–101 |
| 3 | 6.0–11.9 | 102–204 |
| 4 | > 12.0 | > 204 |

> 换算：1 mg/dL ≈ 17.1 µmol/L。取窗口内**最高**值。

### 2.4 循环

| 分值 | 条件 |
|---|---|
| 0 | MAP ≥ 70 mmHg |
| 1 | MAP < 70 mmHg |
| 2 | 多巴胺 < 5 µg/kg/min **或** 多巴酚丁胺（任意剂量） |
| 3 | 多巴胺 5–15 **或** 肾上腺素 ≤ 0.1 **或** 去甲肾上腺素 ≤ 0.1 µg/kg/min |
| 4 | 多巴胺 > 15 **或** 肾上腺素 > 0.1 **或** 去甲肾上腺素 > 0.1 µg/kg/min |

> 血管活性药与 MAP **同时评估，取高分**；剂量单位统一为 **µg/kg/min**。

### 2.5 神经（GCS）

| 分值 | GCS |
|---|---|
| 0 | 15 |
| 1 | 13–14 |
| 2 | 10–12 |
| 3 | 6–9 |
| 4 | < 6 |

> 与 APACHE II 同源同规则：**E+V+M 三项评全**方可计分；`item4=ET`（插管/气切）时 V 不可评，跳过该条取更早的评全记录。

### 2.6 肾（肌酐 **或** 24h 尿量，取高分）

| 分值 | 肌酐 mg/dL | 肌酐 µmol/L | 24h 尿量 |
|---|---|---|---|
| 0 | < 1.2 | < 106 | — |
| 1 | 1.2–1.9 | 106–168 | — |
| 2 | 2.0–3.4 | 169–300 | — |
| 3 | 3.5–4.9 | 301–440 | < 500 mL/d |
| 4 | > 5.0 | > 440 | < 200 mL/d |

## 三、数据源映射（**已按实测代码核对**）

| SOFA 项 | 所需数据 | ICU 表 / 字段 | 现有可复用方法 | 状态 |
|---|---|---|---|---|
| 呼吸 | PaO₂/FiO₂ | `patient_info_lis_item` 中 `lis_item_name='氧合指数'`（血气报告内，**ICU 已算好**） | `IcuPatientMapper.selectOxygenationHistory` | ✅ 直接可用 |
| 呼吸 | 呼吸支持 | `patient_info.ventilator_code`（非空=机械通气）；`oi_peep` | `selectLatestVentilatorParams` | ✅ |
| 凝血 | 血小板 | `patient_info_lis_item`，**`lis_item_code = 200050`**（已确认） | 需新增查询（可复用 `selectLabRecordsByCodes` 的写法） | ✅ code 已确认；单位通常 `10⁹/L`，与 SOFA 的 `×10³/µL` **数值等同**，无需换算 |
| 肝 | 总胆红素 | `patient_info_lis_item`，候选 code **`100010 / 100020 / 100030`**（三个，需区分总/直接/间接胆红素） | 新增查询一次取 3 个 code，Java 侧复用 `PkpdServiceImpl#isTotalBilirubin` 按 `lis_item_name` 取"总胆红素" | ⚠️ 需确认三者中哪个是总胆红素（见 §8 风险 2）；单位通常 µmol/L → ÷17.1 转 mg/dL |
| 循环 | MAP | `patient_observe_module_item_record`（`oi_ycpjy,oi_pjy`） | `selectObserveRecords` + APACHE II 的「最差值」算法 | ✅ |
| 循环 | 血管活性药 | `patient_advice_execute`（泵速+总液量）+ `patient_advice_execute_drug`（药物总量/规格）+ `patient_advice_execute_step`（**调速历史**） | 需**新增**查询；现有 `selectVasopressorAdvice` 走的是医嘱表 `patient_advice`，**不含泵速**，不能直接用 | ✅ 可精确还原 µg/kg/min（见 §3.1） |
| 神经 | GCS | `patient_doc_record`（`doc_code='Z_ICU_GCS'`），`item1=E/item2=V/item3=M/item4=ET` | `selectGcsDocRecordsByRange` | ✅ |
| 肾 | 肌酐 | `patient_info_lis_item`（`lis_item_code=100210`） | `selectLabRecordsByCodes` | ✅ |
| 肾 | 24h 尿量 | `patient_io_module_item_record`（固定 `item_code='ii_nl'`，或 `item_name` 含"尿量"） | `selectUrineByPatient` / `selectIoRecords` | ✅ |

### 3.1 血管活性药剂量还原（**已按真实数据验证 · 2026-09-11**）

微量泵（去甲肾上腺素）样例涉及 **3 张表**：

| 表 | 作用 | 关键字段 |
|---|---|---|
| `patient_advice_execute` | 执行主表：**泵速 + 总液量** | `drug_now_speed`（当前泵速）、`drug_start_speed`、`drug_speed_unit`（样例 `ml/h`）、`liquid_amount`（**总液量**，样例 50）、`start_time`、`status`、`name`（复合名，含溶媒+药物） |
| `patient_advice_execute_drug` | 药品明细：**药物总量 + 规格** | `drug_name`、`drug_dose`（样例 10，mg）、`drug_dose_unit`、`spec`（样例 `1ml:2mg / 支`）、`liquid_amount`（⚠️ **药物自身体积**，样例 5ml） |
| `patient_advice_execute_step` | **调速历史**（关键，用于还原任意时刻泵速） | `step_time`、`step_type_name`（执行/加速…）、`speed`（样例 2 → 6 → 10，与 `drug_speed_unit` 同单位） |

**浓度推算（样例已校验通过）**
```
药物总量 = drug_dose = 10 mg                    （交叉验证：spec 2mg/ml × 5ml = 10mg ✓）
总液量   = patient_advice_execute.liquid_amount = 50 ml   （45ml 溶媒 + 5ml 药液 ✓）
浓度     = 10 mg ÷ 50 ml = 0.2 mg/ml = 200 µg/ml
```

**剂量换算**
```
剂量(µg/kg/min) = 泵速(ml/h) × 浓度(µg/ml) ÷ 60 ÷ 体重(kg)

样例（体重按 70kg 估算）：
  10 ml/h × 200 ÷ 60 ÷ 70 ≈ 0.476 µg/kg/min  → SOFA 循环 = 4 分
   2 ml/h × 200 ÷ 60 ÷ 70 ≈ 0.095 µg/kg/min  → SOFA 循环 = 3 分
```

**取数口径（重要）**
1. **取窗口内最差**：SOFA 用 24h 最差值 → 由 `patient_advice_execute_step` 还原速度时间线，取窗口内**最大泵速**（比 `drug_now_speed` 更贴合"最差"语义；`drug_now_speed` 仅作无 step 记录时的兜底）。
2. **多泵/多药**：同药多泵取最大剂量；不同药各自算分后取**最高**。
3. **药名匹配陷阱**：`去甲肾上腺素` **包含子串** `肾上腺素` → 必须**先匹配去甲肾上腺素**，匹配肾上腺素时需**排除含"去甲"**的串；样例 `drug_name` 有**前导空格**、`spec` 有**尾随空格**，一律 `TRIM` 后再匹配。
4. **单位：全库仅 `ml/h`** —— 已实测 `patient_advice_execute.drug_speed_unit` 去重后**只有 `ml/h` 一种**（401,723 条），故换算只需 `ml/h` 一条分支。仍保留「非 `ml/h` 则降级并标注」的防御性判断，避免将来新增单位时静默算错。
5. **重酒石酸盐折算（需临床确认）**：样例为「重酒石酸去甲肾上腺素」且 `spec=1ml:2mg`。若院内惯例按**碱基**计（重酒石酸盐 : 碱基 ≈ 2 : 1），浓度需减半。**方案中把折算系数做成配置项**（`sofa_config` 新增 `norepinephrine_conversion` / `epinephrine_conversion`，**默认 1.0 = 按标示量**），由临床确认后调整，避免写死。
6. **降级策略**：无泵速、无浓度或单位不可识别时，降级为**按药名判定**（用去甲肾上腺素/肾上腺素/多巴胺 → ≥3 分；仅多巴酚丁胺 → 2 分），并在界面标注「剂量未归一，请人工确认」。
7. **体重来源**：见下方 §3.2（多级兜底 + 界面标注来源）。

### 3.1.1 药物总量单位归一（`drug_dose_unit` 实测为混杂值）

实测 `patient_advice_execute_drug.drug_dose_unit` 去重 **36 种**，混装了 5 类语义，**必须区分，否则会算出数量级错误**：

| 语义 | 实测取值（含条数） | 处理 |
|---|---|---|
| **质量** | `mg`(191,849)、`g`(177,946)、`μg`(1,862)、`克`(725)、`毫克`(21)、`ug`(1) | ✅ 归一为 µg（g ×10⁶、mg ×10³，`ug`=`μg`、`克`=`g`、`毫克`=`mg`） |
| **体积** | `ml`(328,348) | ⚠️ **不是药量**！须由 `spec`（如 `1ml:2mg / 支`）× 体积推导 |
| **包装数量** | `支`(21,667)、`片`(29,693)、`粒`、`袋`、`瓶`、`包`、`丸`、`罐`、`贴`、`揿`/`掀`、`吸`、`个` | ⚠️ **不是药量**！须 `spec` × 数量 |
| **生物效价单位** | `U`(9,260)、`iu`(3,567)、`单位`、`万iu`、`万单位`、`万u`、`ku`、`Bu`、`10万IU` | ❌ 无法换算为 mg（依药物而定）；此类药物不参与 SOFA 循环评分 |
| **速率/浓度** | `ml/h`(4)、`mg/h`(1)、**`μg/kg/h`(3)**、`mg/ml`(14) | 本身已是速率/浓度：`μg/kg/h` 可直接使用（÷60 转 /min） |
| 数据异常 | `cmH2O`(3)、`NULL`(9,296) | 质量异常；血管活性药按药名过滤不会命中 |

**药量取值优先级（仅针对血管活性药）**
```
1) drug_one_dosage + drug_one_dosage_unit ∈ 质量单位 → 归一为 µg     ← 样例命中此路径（10 mg）
2) drug_dose       + drug_dose_unit       ∈ 质量单位 → 归一为 µg
3) 解析 spec（形如 '1ml:2mg / 支'）× 体积/支数 → 得到 µg
4) 以上都不可用 → 走安全底线（见下）
```

**⚠️ 安全底线（关键）**：当单位落在 `ml / 支 / 片 / U / iu / 单位…` 等**非质量单位**、且 `spec` 无法解析时，**一律不推测药量**，改为「按药名判定」并在界面标注 `剂量未归一，请人工确认`。
> 宁可降级，也不能算出数量级错误的 µg/kg/min——本项直接决定升压药剂量的临床判读，错一个数量级比缺失更危险。

**范围收敛**：36 种单位中只需覆盖**血管活性药实际出现**的那几种即可，无需全表适配（见 §八 风险 9）。

### 3.2 体重兜底策略（含年龄/性别默认值）

血管活性药按 µg/kg/min 计，**体重是必需输入**。按下列顺序取值，并在界面显式标注来源：

| 优先级 | 来源 | 说明 |
|---|---|---|
| 1 | `patient_info.weight` | 实际体重（首选；血管活性药惯例按实际体重计） |
| 2 | 身高 + 性别 → **IBW**（Devine 公式） | `patient_info.height` 可得时使用；复用 PKPD 模块已有的 IBW 实现 |
| 3 | **年龄 + 性别 → 默认体重表** | 身高与体重均缺失时使用（下表；`sofa_config` 可覆盖） |
| 4 | 固定 70 kg | 最终兜底 |

**默认体重表（初版，需临床确认后写入 `sofa_config`）**

| 年龄段 | 男 (kg) | 女 (kg) |
|---|---|---|
| 18–39 | 70 | 58 |
| 40–59 | 70 | 60 |
| 60–69 | 67 | 58 |
| 70–79 | 65 | 55 |
| ≥ 80 | 62 | 52 |
| < 18 | 本期不支持儿科，标注需人工填写 | — |

- **配置化**：`sofa_config` 新增 `config_type='default_weight'`，`config_key` 形如 `M_18_39`，`config_value='70'`，便于院内调整而无需改代码。
- **界面标注**：循环项卡片与「来源」弹窗显示体重及其来源，例如
  `体重 70kg（实际）` / `体重 62kg（IBW 估算，身高 168cm）` / `体重 65kg（年龄性别默认值，待确认）`；
  使用默认值的评分记录会在 `remark` 中自动注明，便于质控追溯。

## 四、评分口径设计

1. **取数窗口**：默认 **过去 24 小时**（SOFA 标准）；同时提供自定义区间（沿用 APACHE II 的取数范围控件与预设：24h/48h/72h/入科后 24h）。
2. **最差值原则**：每项取窗口内最差（偏离正常最远、对应分值最高）的值——与 APACHE II 一致。
3. **二选一取高分**：循环（MAP vs 血管活性药）、肾（肌酐 vs 24h 尿量）。
4. **数据缺失**：该项记 **0 分并标注「未取到」**，不猜测、不虚高；页面明确提示需医生复核。
5. **单位归一（必须在服务层统一处理）**：
   - FiO₂：库内可能是百分数（60）或小数（0.6）→ 统一为百分数（ARDS 模块已踩过此坑）
   - 胆红素：µmol/L → mg/dL（÷17.1）
   - 肌酐：µmol/L → mg/dL（÷88.4，APACHE II 已验证）
   - 尿量：mL/24h（窗口内求和）
6. **自动初评（终身一条）**：配合定时任务，为在科超 24h 患者自动生成 SOFA 初评；**同一患者只要已存在任意一条评分记录即跳过**（幂等，逻辑同 `Apache2AutoScoreTask`）。需要动态跟踪时由医生手动补评，补评记录与自动记录共存于左侧历史列表。

## 五、数据库设计（`zing_doctor_db_prod`）

### 5.1 `sofa_score_record`（评分记录，仿 `apache2_score_record`）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT IDENTITY | 主键 |
| patient_id / in_hospital_no / patient_name / depart_code | VARCHAR | 患者定位 |
| score_time | TIMESTAMP | 评分时间 |
| score_type | VARCHAR(32) | `auto` 自动 / `daily` 每日 / `custom` 自定义 |
| resp_score / coag_score / liver_score / cardio_score / neuro_score / renal_score | INT | 6 项分项分 |
| total_score | INT | 总分 0~24 |
| resp_data / coag_data / liver_data / cardio_data / neuro_data / renal_data | TEXT(JSON) | 各项原始值与取数时间，供复核与"来源"展示 |
| vasopressor_json | TEXT | 血管活性药明细（药名/剂量/单位/起止时间） |
| urine_24h | DECIMAL | 24h 尿量 |
| gcs_total / gcs_detail | INT / VARCHAR | GCS 与 E/V/M 明细 |
| respiratory_support | TINYINT | 是否有呼吸支持 |
| data_start_time / data_end_time | TIMESTAMP | 取数范围 |
| delta_sofa | INT | 较上一次评分的变化（可为负）。⚠️ 自动评分改为终身一条后不再自动生成多日序列，仅医生手动补评多条时才有值；「评分历史趋势」已下线，科室总览的 ΔSOFA 预警列仅供参考 |
| remark / status / create_by / create_time / update_by / update_time | — | 同现有表规范 |

索引：`in_hospital_no`、`depart_code`、`score_time`。

### 5.2 `sofa_config`（配置表，仿 `apache2_config`）

| config_type | config_key | 示例 config_value | 说明 |
|---|---|---|---|
| `lis_item` | `platelet` | `200020` 或 `PLT` | 血小板 item code / 名称关键词 |
| `lis_item` | `bilirubin` | `100310,总胆红素` | 总胆红素 |
| `lis_item` | `creatinine` | `100210` | 肌酐 |
| `observe_item` | `map` | `oi_ycpjy,oi_pjy` | 平均动脉压 |
| `observe_item` | `fio2` | `oi_FiO2(设置值)` | FiO₂ |
| `observe_item` | `peep` | `oi_peep` | PEEP（辅助判断呼吸支持） |
| `io_item` | `urine` | `ii_nl` | 尿量 item_code |
| `vasopressor` | `norepinephrine` | `去甲肾上腺素,0.1` | 药名关键词,3/4 分界阈值(µg/kg/min) |
| `vasopressor` | `dopamine` | `多巴胺,5,15` | 药名,2/3 界,3/4 界 |
| `vasopressor` | `epinephrine` | `肾上腺素,0.1` | 药名,3/4 界 |
| `vasopressor` | `dobutamine` | `多巴酚丁胺` | 任意剂量即 2 分 |

> 表空时回退代码内置默认值（与 `zing_abx_word_config` / `apache2_config` 一致）。

### 5.3 页面注册

`02_seed.sql` 的 `zing_page_config` 增补（或增量脚本）：

| page_code | 页面名 | frontend_path |
|---|---|---|
| `sofa-score` | SOFA 评分 | `/page/sofa-score` |
| `sofa-overview` | SOFA 评分总览 | `/page/sofa-overview` |

## 六、后端接口设计

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/sofa/assessment/{patientId}` | GET | 单患者 SOFA 评估（6 项 + 总分 + 体重来源；`startTime/endTime` 指定取数范围） |
| `/api/sofa/assessment/by-no` | GET | 按住院号查询（ICU 外链入口） |
| `/api/sofa/record` | POST | 保存/更新评分（`score_type`：`daily`/`auto` 自动初评、`reviewed` 已复核、`custom` 手工） |
| `/api/sofa/records` | GET | 历史评分列表（`inHospitalNo`，按评分时间倒序） |
| `/api/sofa/record/delete` | POST | 逻辑删除（`id`） |
| `/api/sofa/auto-generate` | POST | 手动触发自动初评（与定时任务同逻辑，幂等 + 互斥锁） |
| `/api/sofa/overview` | GET | 科室总览：评分分布、ΔSOFA 恶化预警 |
| `/api/sofa/metric-trend/{patientId}` | GET | 单指标趋势（`metricKey` = resp/coag/liver/cardio/neuro/renal），供「来源」弹窗核对取数依据；⚠️ 原 `total`（总分趋势）已下线 |
| `/api/sofa/record/{id}/pdf` | GET | 取文书 PDF（Base64 + 文件名） |
| `/api/sofa/patient/{patientId}/gcs-records` | GET | 重症系统已评估 GCS 记录（GCS 弹窗同步/选择用） |
| `/api/sofa/record/{id}/pdf` | POST | 补传文书 PDF（与主体保存解耦） |
| `/api/sofa/config`、`/config/{configType}` | GET | 配置列表（含停用项，带 status） |
| `/api/sofa/config/save` | POST | 配置新增/更新 |
| `/api/sofa/config/delete` | POST | 配置逻辑删除 |
| `/api/sofa/config/toggle` | POST | 配置启用/停用 |

代码结构（与 `module/apache2` 对称）：

```
module/sofa/
├── controller/SofaController.java
├── service/SofaService.java
├── service/impl/SofaServiceImpl.java      ← 6 项评分规则 + 取数
├── entity/SofaScoreRecord.java · SofaConfig.java
├── mapper/SofaScoreRecordMapper.java · SofaConfigMapper.java
├── task/SofaAutoScoreTask.java                ← 定时初评（每天 01:00，终身一条）
└── dto/SofaAssessmentView.java（6 项子对象 + totalScore + deltaSofa）
```

> 上表接口按 `SofaController` 实测代码核对（2026-09-15）；设计稿早期写过的 `/patients/{patientId}/assessment`、`/by-no/assessment`、`/auto-fetch/{patientId}`、`/patient/{inHospitalNo}/records`、`/record/{id}` DELETE 等路径，实际实现已分别合并/改名为上表形式。

新增 ICU 查询（`IcuPatientMapper`，`@DS("icu")` 只读）：
- `selectPlateletRecordsByRange(inHospitalNo, startTime, endTime)`
- `selectBilirubinRecordsByRange(...)`
- `selectUrineSumByRange(patientId, startTime, endTime)`（`ii_nl` 求和，可复用 `selectUrineByPatient` 后在 Java 求和）
- `selectVasopressorExecuteByRange(inHospitalNo, startTime, endTime)`：`patient_advice_execute` 关联 `patient_advice_execute_drug`，取 `drug_name` / `drug_dose`(+unit) / `spec` / 执行主表 `liquid_amount`(总液量) / `drug_speed_unit` / `drug_now_speed` / `start_time`
- `selectVasopressorSpeedSteps(executeIds, startTime, endTime)`：`patient_advice_execute_step` 按执行 ID 批量取调速历史（`step_time` + `speed`），用于还原窗口内**最大泵速**

## 七、前端页面设计

### 7.1 `sofa-score`（医生视角）

```
┌──────────────────────────────────────────────────────────────┐
│ 患者信息条（姓名/床号/住院号/年龄性别/入科时间）                 │
├──────────────┬──────────────┬──────────────┬─────────────────┤
│ 呼吸          │ 凝血          │ 肝            │ 循环             │
│ PaO2/FiO2    │ 血小板        │ 总胆红素       │ MAP / 血管活性药  │
│ [+呼吸支持]   │              │              │                 │
├──────────────┼──────────────┼──────────────┴─────────────────┤
│ 神经 GCS      │ 肾            │        总分 / ΔSOFA 对比       │
│ (E/V/M)      │ 肌酐 / 24h尿量 │  0~24 + 较上次 ↑↓ 变化         │
└──────────────┴──────────────┴────────────────────────────────┘
   ▸ 每项右侧「来源」按钮 → 弹窗（当前值 + 命中区间 + 得分 + 该指标数据趋势图）
   ▸ 支持自动取数 / 分值手工修正 / 保存 / 左侧历史列表（新建、切换、删除）
   ▸ ⚠️ 「SOFA 评分历史趋势」入口已于 2026-09-15 下线：自动评分改为终身一条后无自动多日序列。
      「来源」弹窗内的单指标趋势保留，用于核对本次取数依据
```

**直接复用 APACHE II 的交互范式**（本项目已验证有效）：
- 左侧历史评分列表（标注 `自动初评` / `手动评分`，按 `score_type` 判定）
- 每项「来源」弹窗：当前值 / 命中区间 / 得分 / **数据趋势**（复用双 Y 轴、最差点高亮等做法）
- 得分使用「最差值」并在弹窗展示取数时间点

### 7.2 `sofa-overview`（主任视角）

- 科室 SOFA 分布（0-1 / 2-5 / 6-9 / 10-14 / ≥15）
- 平均分、ΔSOFA 升高的患者预警列表
- 患者列表：姓名/床号/总分/较上次变化/评分时间/呼吸支持

## 八、风险与必须现场核对的项

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| 1 | 循环项剂量还原 | ✅ 已验证可行（§3.1），可精确到 µg/kg/min | 剩余：① ~~单位分支~~ **已实测全库仅 `ml/h`**（401,723 条），保留防御性降级；② **重酒石酸盐 ↔ 碱基折算系数需临床确认**（做成配置项，默认 1.0 = 按标示量）；③ 无泵速/无浓度 → 降级为按药名判定并标注；④ 药名匹配须**先匹配"去甲肾上腺素"**、匹配"肾上腺素"时**排除"去甲"**；⑤ 体重缺失按 §3.2 多级兜底并标注来源 |
| 2 | 胆红素给了 **3 个 code**（`100010/100020/100030`），需确定哪个是**总胆红素** | 若取到直接/间接胆红素 → 肝项分值错位 | ① 优先按 `lis_item_name` 名称筛选（复用 `PkpdServiceImpl#isTotalBilirubin`，排除"直接/间接"）；② 名称缺失时按 `sofa_config` 指定的**主 code**（默认 `100010`）取值；③ 「来源」弹窗展示**实际命中的 code + 名称**，便于医生核对 |
| 3 | 胆红素库内单位（µmol/L vs mg/dL） | 肝项分值错位 | 按量级判断并归一（>20 视为 µmol/L，÷17.1 转 mg/dL） |
| 4 | 血小板单位 | — | ✅ `10⁹/L` 与 SOFA 的 `×10³/µL` **数值等同**，直接用，无需换算 |
| 5 | FiO₂ 存百分数还是小数 | 呼吸项分支错误 | 统一归一为百分数（ARDS 模块已遇到，同法处理） |
| 6 | `lis_item_name='氧合指数'` 是否恒等于 PaO₂/FiO₂ | 呼吸项口径 | 抽查 5~10 例人工核算；必要时改为自行用 PaO₂ 与当时 FiO₂ 计算 |
| 7 | "呼吸支持"定义（机械通气 vs 含 CPAP/高流量） | 呼吸项 3/4 分门槛 | 以 `ventilator_code` 非空为准，`oi_peep` 有值作为补充 |
| 8 | 尿量 `ii_nl` 是否为唯一尿量口径（与"尿量"名称项重复计？） | 肾项尿量翻倍 | 以 `ii_nl` 为准；名称匹配仅作兜底，二者不叠加 |
| 9 | `drug_dose_unit` 混杂 **36 种**单位，含 `ml`(328,348)、`支`、`片`、`U`、`iu` 等**非质量单位** | 若误按 mg 计 → 剂量**数量级错误** | ① 只接受**质量单位白名单**（`mg/g/μg/克/毫克/ug`）并归一为 µg；② 非质量单位且 `spec` 无法解析时**不推测药量**，降级为按药名判定并标注；③ 范围收敛：只覆盖血管活性药实际出现的单位（见 §3.1.1） |

## 九、分期实施

### P0（MVP）
1. 6 项自动取数（含单位归一）+ 每项"来源"可追溯
2. 手工修正 + 总分计算 + 保存/历史/逻辑删除
3. 外链支持（`sofa-score` 页面注册 + `/entry` 免登录）
4. `sofa_config` 初始化种子数据
5. 取数范围控件（默认 24h）

### P1（✅ 已实现）
1. ✅ `sofa-overview` 总览 + ΔSOFA 恶化预警
2. ✅ 自动评分定时任务（`SofaAutoScoreTask`，cron 默认 `0 0 1 * * ?` 即**每天 01:00**；幂等粒度为「已有任何记录则跳过」= **终身一条**；互斥锁；cron/开关/入科小时数均可配置）
   - 取数范围 = **评估时点前 24h**（标准 SOFA 口径；入科不足 24h 从入科时间起算）。不可用「当日 0 点→现在」，否则 01:00 触发时窗口仅 1h，多数指标无数据记 0 分，且尿量判据（要求窗口 ≥20h）会被直接跳过
3. ✅ 评分文书（PDF）：离屏 DOM + html2canvas/jsPDF，复用 APACHE II 的「主体先落库、PDF 后补传」解耦方案；历史列表可查看已归档文书
4. ✅ 单指标趋势图：`/api/sofa/metric-trend`，来源弹窗内按 **6 个器官指标**展示，并高亮评分实际取用点。⚠️ 原「总分历史趋势」（`metricKey=total` + 页面「评分历史趋势」按钮/弹窗）已于 2026-09-15 下线
5. ✅ 配置管理后台页面（`sofa-config`）：支持新增/编辑/启停/逻辑删除
6. ✅ **六项分值手工修正**：每张器官卡片的分值可选 0~4 直接覆盖自动值，适用于镇静患者 GCS 取镇静前、
   升压药剂量无法归一、`ventilator_code` 误判呼吸支持等场景；总分按修正后实时重算，
   修正明细自动写入备注（如「手工修正：呼吸 3→2、神经 2→3」）便于事后追溯
7. ✅ **来源三态**：`daily`/`auto` → 自动初评、`reviewed` → 已复核、`custom` → 手工评分。
   自动记录可由医生点「复核」载入并覆盖保存（复用原记录 id），标记为已复核
8. ⬜ Sepsis-3 联动：疑似感染 + SOFA ≥2 → 提示脓毒症并跳转集束化页（留待 P2）

### 待现场确认项（代码已按保守口径实现）

| 项 | 说明 |
|---|---|
| `vasopressor.status_filter` | 升压药执行单是否按 `status` 过滤。**默认不过滤**：本项目已知 `patient_advice_execute.status` 表达的是「执行状态」（`HandoverServiceImpl` 用 `status='1'` 表示执行中，`selectRunningAdvice` 用 `status IN (0,1)` 表示执行中/未执行），并非「是否作废」；盲目加 `status=1` 会把「已完成但未删除」的历史医嘱剔出 24h 回顾窗口，**反而漏计**。现场确认字典后，在配置管理页新增 `config_type=vasopressor`、`config_key=status_filter`（值如 `0,1` 或 `1`）即可启用（仅接受数字与逗号） |
| 氧合指数口径 | 呼吸项直接取 `lis_item_name='氧合指数'`，假设其即 PaO₂/FiO₂；若不是需改用 PaO₂ 与 FiO₂ 计算 |
| `ventilator_code` 语义 | 非空即视为有呼吸支持（3/4 分的前提）；若混入高流量/无创/单纯氧疗会高估，现可由分项手工修正覆盖 |
| 取数项编码 | 血小板 `200050`、胆红素三编码、尿量 `ii_nl` 等来自 07 种子数据，建议抽 5 例核对 |
| 升压药剂量口径 | 现取「取数窗口内在效的最高泵速」还原剂量（含窗口起点的在效泵速），未实现「需持续 ≥1h 的最高档」约束；若按后者口径评估需另行确认 |

### P2
1. SOFA 序列趋势图（多日 6 项堆叠）—— ⚠️ 当前自动评分为「终身一条」，无自动多日序列；若要恢复按天留痕，需同步改 `SofaServiceImpl` 的幂等条件（已有记录即跳过 → 当日已有记录才跳过）与 `SofaAutoScoreTask` 的 cron
2. 与 APACHE II 双评分对照视图
3. 质控统计（评分完成率、ΔSOFA 分布）

## 十、验收要点

- [ ] 6 项阈值与 Vincent 1996 / Sepsis-3 标准逐条比对通过
- [ ] 循环、肾的"二选一取高分"逻辑正确
- [ ] 单位归一（FiO₂ / 胆红素 / 肌酐 / 尿量）经真实数据验证
- [ ] 缺数据不虚高：未取到项记 0 并在界面标注
- [ ] 与 APACHE II 共享 GCS 取数口径（三项评全、ET 插管跳过）
- [ ] 单元测试覆盖 6 项阈值边界（参照 `Apache2ScoringTest` 的规格测试写法）
- [ ] 外链免登录可打开；`score_type` 标签正确区分自动/手工

---

## 附：与现有模块的复用清单

| 复用对象 | 来源 | 用途 |
|---|---|---|
| `selectObserveRecords` / `findWorstValue` | APACHE II | MAP、FiO₂ |
| `selectGcsDocRecordsByRange` / `parseGcsRows` | APACHE II | GCS |
| `selectLabRecordsByCodes` | APACHE II | 肌酐 |
| `isTotalBilirubin` 名称匹配 | PKPD | 总胆红素 |
| `selectUrineByPatient` / `selectIoRecords` | 交班模块 | 24h 尿量 |
| `selectVasopressorAdvice` | 脓毒症集束化 | **仅药名/剂量展示**（医嘱表 `patient_advice` 无泵速；SOFA 剂量还原须另查执行三表，见 §3.1） |
| `selectOxygenationHistory` | ARDS | PaO₂/FiO₂ |
| `selectLatestVentilatorParams` | ARDS | 呼吸支持判断 |
| 「来源」弹窗 + 趋势图交互 | APACHE II | 前端交互范式 |
| 自动初评定时任务 + 幂等 + 互斥锁 | APACHE II | 入科满 24h 自动初评（与 APACHE II 同为「一人一条」） |
| 主体/PDF 解耦保存 | APACHE II | 文书归档 |
