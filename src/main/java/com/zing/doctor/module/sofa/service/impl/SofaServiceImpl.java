package com.zing.doctor.module.sofa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.sofa.dto.SofaAssessmentView;
import com.zing.doctor.module.sofa.entity.SofaConfig;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaConfigMapper;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.module.sofa.service.SofaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SOFA（序贯器官衰竭评估）实现。
 *
 * <p>评分标准（Vincent 1996 / Sepsis-3），6 项各 0~4 分，总分 0~24：
 * <ul>
 *   <li>呼吸 PaO2/FiO2：≥400=0 / &lt;400=1 / &lt;300=2 / &lt;200 且有呼吸支持=3 / &lt;100 且有呼吸支持=4</li>
 *   <li>凝血 血小板(×10³/µL)：≥150=0 / &lt;150=1 / &lt;100=2 / &lt;50=3 / &lt;20=4</li>
 *   <li>肝 总胆红素(mg/dL)：&lt;1.2=0 / 1.2-1.9=1 / 2.0-5.9=2 / 6.0-11.9=3 / &gt;12=4</li>
 *   <li>循环：MAP≥70=0 / &lt;70=1 / 多巴胺&lt;5 或多巴酚丁胺=2 / 多巴胺5-15 或 肾上腺素≤0.1 或 去甲肾上腺素≤0.1=3 / 更高=4</li>
 *   <li>神经 GCS：15=0 / 13-14=1 / 10-12=2 / 6-9=3 / &lt;6=4</li>
 *   <li>肾 肌酐(mg/dL) 或 24h 尿量：&lt;1.2=0 / 1.2-1.9=1 / 2.0-3.4=2 / 3.5-4.9 或尿量&lt;500=3 / &gt;5.0 或尿量&lt;200=4</li>
 * </ul>
 *
 * <p>取数口径：窗口内最差值；循环与肾「二选一取高分」；缺数据项记 0 分并标注。
 * 血管活性药剂量 = （药物总量 ÷ 总液量）× 泵速，换算为 µg/kg/min；
 * 单位非质量单位且无法解析时**不推测药量**，降级为按药名判定并标注。
 */
@Slf4j
@Service
public class SofaServiceImpl implements SofaService {

    @Autowired
    private IcuPatientMapper icuPatientMapper;

    @Autowired
    private SofaScoreRecordMapper scoreRecordMapper;

    @Autowired
    private SofaConfigMapper configMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 质量单位白名单（其余如 ml / 支 / 片 / U / iu 一律视为非药量） */
    private static final Set<String> MASS_UNITS = new HashSet<>(Arrays.asList(
            "mg", "毫克", "g", "克", "μg", "ug", "µg", "mcg"));

    /** spec 形如 "1ml:2mg / 支" 或 "2ml:10mg" */
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "([\\d.]+)\\s*ml\\s*[:：]\\s*([\\d.]+)\\s*(mg|g|μg|ug|µg|mcg)",
            Pattern.CASE_INSENSITIVE);

    /** 认为窗口达到"日"口径的最小小时数（SOFA 尿量阈值为 24h） */
    private static final double MIN_HOURS_FOR_URINE = 20.0;

    /** 重酒石酸盐↔碱基等换算系数配置键 → 药物类型 */
    private static final double DEFAULT_FACTOR = 1.0;

    // ==================================================================
    // 评估
    // ==================================================================

    @Override
    public SofaAssessmentView getAssessment(String patientId, String startTime, String endTime) {
        Map<String, Object> p = icuPatientMapper.selectSofaPatientBase(patientId);
        if (p == null) {
            throw new BizException(404, "未找到患者：" + patientId);
        }
        String inHospitalNo = toStr(p.get("in_hospital_no"));
        if (inHospitalNo.isEmpty()) {
            throw new BizException(400, "患者缺少住院号，无法取数：" + patientId);
        }

        List<String> notes = new ArrayList<>();
        SofaAssessmentView v = new SofaAssessmentView();
        v.setPatient(buildPatient(p));
        v.setDataStartTime(parseDateTime(startTime));
        v.setDataEndTime(parseDateTime(endTime));

        // 呼吸支持：ventilator_code 非空视为有
        boolean support = !toStr(p.get("ventilator_code")).isEmpty();
        v.setRespiratorySupport(support ? 1 : 0);

        // 体重（剂量换算必需）
        Weight w = resolveWeight(p);
        v.setWeightUsed(BigDecimal.valueOf(w.kg).setScale(2, RoundingMode.HALF_UP));
        v.setWeightSource(w.source);
        v.setWeightNote(w.note);
        if (!"actual".equals(w.source)) {
            notes.add("体重来源：" + w.note);
        }

        Map<String, String> lisCfg = loadConfigMap("lis_item");
        Map<String, String> obsCfg = loadConfigMap("observe_item");

        // ---------------- 取数（窗口内最差值） ----------------
        // 呼吸：氧合指数（PaO2/FiO2）取最低
        List<Map<String, Object>> oxyRows = safe(icuPatientMapper.selectOxygenationHistoryByRange(
                patientId, startTime, endTime));
        Double pf = minOf(oxyRows, "oxygenation_index");
        String pfTime = timeOfMin(oxyRows, "oxygenation_index", "check_time");

        // 凝血：血小板取最低
        List<Map<String, Object>> pltRows = safe(icuPatientMapper.selectLabRecordsByCodes(
                inHospitalNo, codes(lisCfg.get("platelet")), startTime, endTime));
        Double plt = minOf(pltRows, "item_value");
        String pltTime = timeOfMin(pltRows, "item_value", "item_time");

        // 肝：总胆红素取最高（优先按名称认定为“总胆红素”，排除直接/间接）
        List<Map<String, Object>> biliRows = safe(icuPatientMapper.selectLabRecordsByCodes(
                inHospitalNo, codes(lisCfg.get("bilirubin")), startTime, endTime));
        BilirubinPick bili = pickTotalBilirubin(biliRows);
        Double biliMgDl = (bili.umol == null) ? null : round(bili.umol / 17.1, 2);

        // 循环：MAP 取最低
        List<Map<String, Object>> obsRows = safe(icuPatientMapper.selectObserveRecords(patientId, startTime, endTime));
        MapObs mapObs = pickObserveMin(obsRows, codes(obsCfg.get("map")));
        // 循环：血管活性药剂量还原
        VasoResult vaso = resolveVasopressors(inHospitalNo, startTime, endTime, w.kg);

        // 神经：GCS（范围内最新一条评全记录）
        Map<String, Object> gcs = latestGcs(patientId, startTime, endTime);

        // 肾：肌酐取最高
        List<Map<String, Object>> crRows = safe(icuPatientMapper.selectLabRecordsByCodes(
                inHospitalNo, codes(lisCfg.get("creatinine")), startTime, endTime));
        Double crUmol = maxOf(crRows, "item_value");
        String crTime = timeOfMax(crRows, "item_value", "item_time");
        Double crMgDl = (crUmol == null) ? null : round(crUmol / 88.4, 2);
        // 肾：窗口内尿量合计（仅窗口达 24h 口径时才作为判据）
        List<Map<String, Object>> urineRows = safe(icuPatientMapper.selectUrineByPatient(patientId, startTime, endTime));
        Double urineSum = sumOf(urineRows, "item_value");
        boolean urineUsable = windowHours(startTime, endTime) >= MIN_HOURS_FOR_URINE;
        if (urineSum != null && !urineUsable) {
            notes.add("尿量判据已跳过：取数窗口不足 24h（仅按肌酐评分）");
        }
        Double urineForScore = urineUsable ? urineSum : null;
        v.setUrineMl(urineForScore == null ? null : BigDecimal.valueOf(urineForScore).setScale(1, RoundingMode.HALF_UP));

        // ---------------- 计分 ----------------
        SofaAssessmentView.SofaItem resp = item("resp", "呼吸");
        resp.setValue(pf);
        resp.setUnit("PaO2/FiO2");
        resp.setDataTime(pfTime);
        resp.setMissing(pf == null);
        resp.setScore(scoreResp(pf, support));
        resp.setRangeText(rangeResp(pf, support));
        resp.setValueText(pf == null ? null : round(pf, 1) + (support ? "（有呼吸支持）" : "（无呼吸支持）"));
        if (pf == null) {
            notes.add("呼吸项未取到氧合指数");
        }
        resp.setRawJson(json(mapOf("oxygenationIndex", pf, "checkTime", pfTime, "respiratorySupport", support)));

        SofaAssessmentView.SofaItem coag = item("coag", "凝血");
        coag.setValue(plt);
        coag.setUnit("×10³/µL");
        coag.setDataTime(pltTime);
        coag.setMissing(plt == null);
        coag.setScore(scoreCoag(plt));
        coag.setRangeText(rangeCoag(plt));
        coag.setValueText(plt == null ? null : String.valueOf((long) round(plt, 0)));
        if (plt == null) {
            notes.add("凝血项未取到血小板");
        }
        coag.setRawJson(json(mapOf("platelet", plt, "itemTime", pltTime)));

        SofaAssessmentView.SofaItem liver = item("liver", "肝");
        liver.setValue(biliMgDl);
        liver.setUnit("mg/dL");
        liver.setDataTime(bili.time);
        liver.setMissing(biliMgDl == null);
        liver.setScore(scoreLiver(biliMgDl));
        liver.setRangeText(rangeLiver(biliMgDl));
        liver.setValueText(biliMgDl == null ? null
                : round(biliMgDl, 2) + " mg/dL（" + round(bili.umol, 1) + " µmol/L）");
        liver.setNote(bili.note);
        if (biliMgDl == null) {
            notes.add("肝项未取到总胆红素");
        }
        liver.setRawJson(json(mapOf("totalBilirubinUmol", bili.umol, "totalBilirubinMgDl", biliMgDl,
                "itemTime", bili.time, "pickedItemName", bili.pickedName)));

        // 循环：MAP 与药物「取高分」
        int mapScore = scoreCardioMap(mapObs.value);
        int vasoScore = vaso.score;
        int cardioScore = Math.max(mapScore, vasoScore);
        SofaAssessmentView.SofaItem cardio = item("cardio", "循环");
        card_assign(cardio, mapObs, vaso, cardioScore);
        if (vaso.hasDegraded) {
            cardio.setNote("部分血管活性药剂量未归一（按药名判定），请人工确认");
            notes.add("循环项存在剂量未归一的降级判定，请人工确认");
        }
        cardio.setRawJson(json(mapOf("map", mapObs.value, "mapTime", mapObs.time,
                "mapScore", mapScore, "vasoScore", vasoScore, "vasopressors", vaso.details)));
        v.setVasopressors(vaso.details);

        SofaAssessmentView.SofaItem neuro = item("neuro", "神经");
        Integer gcsTotal = gcs == null ? null : toInt(gcs.get("gcsTotal"));
        neuro.setValue(gcsTotal == null ? null : gcsTotal.doubleValue());
        neuro.setUnit("GCS");
        neuro.setDataTime(gcs == null ? null : toStr(gcs.get("dataTime")));
        neuro.setMissing(gcsTotal == null);
        neuro.setScore(scoreNeuro(gcsTotal));
        neuro.setRangeText(rangeNeuro(gcsTotal));
        neuro.setValueText(gcsTotal == null ? null : gcsTotal + " 分（GCS）");
        if (gcsTotal == null) {
            notes.add("神经项未取到可评全的 GCS（插管或未评全）");
        }
        neuro.setRawJson(json(mapOf("gcsTotal", gcsTotal, "gcsDetail", gcs == null ? null : gcs.get("gcsDetail"))));
        v.setGcsTotal(gcsTotal);
        v.setGcsDetail(gcs == null ? null : toStr(gcs.get("gcsDetail")));

        SofaAssessmentView.SofaItem renal = item("renal", "肾");
        int crScore = scoreRenalCreatinine(crMgDl);
        int urineScore = scoreRenalUrine(urineForScore);
        renal.setValue(crMgDl);
        renal.setUnit("mg/dL");
        renal.setDataTime(crTime);
        renal.setMissing(crMgDl == null && urineForScore == null);
        renal.setScore(Math.max(crScore, urineScore));
        renal.setRangeText(rangeRenal(crMgDl, urineForScore, crScore, urineScore));
        List<String> renalParts = new ArrayList<>();
        renalParts.add("肌酐 " + (crMgDl == null ? "—" : round(crMgDl, 2) + " mg/dL"));
        if (urineUsable) {
            renalParts.add("尿量 " + (urineForScore == null ? "—" : round(urineForScore, 0) + " mL"));
        } else {
            renalParts.add("尿量判据已跳过（窗口<24h）");
        }
        renal.setValueText(String.join(" / ", renalParts));
        if (crMgDl == null && urineForScore == null) {
            notes.add("肾项未取到肌酐与尿量");
        }
        renal.setRawJson(json(mapOf("creatinineMgDl", crMgDl, "creatinineUmol", crUmol,
                "urineMl", urineForScore, "creatinineScore", crScore, "urineScore", urineScore)));

        List<SofaAssessmentView.SofaItem> items = Arrays.asList(resp, coag, liver, cardio, neuro, renal);
        v.setItems(items);
        int total = items.stream().mapToInt(i -> i.getScore() == null ? 0 : i.getScore()).sum();
        v.setTotalScore(total);

        // 与上次评分对比
        SofaScoreRecord last = latestRecord(inHospitalNo);
        if (last != null) {
            v.setLastScore(last.getTotalScore());
            v.setLastScoreTime(last.getScoreTime());
            if (last.getTotalScore() != null) {
                v.setDeltaSofa(total - last.getTotalScore());
            }
        }

        v.setRemark(String.join("；", notes));
        return v;
    }

    /** 组装循环项展示（MAP + 血管活性药，注明明细） */
    private void card_assign(SofaAssessmentView.SofaItem cardio, MapObs mapObs, VasoResult vaso, int cardioScore) {
        boolean hasDrug = !vaso.details.isEmpty();
        cardio.setValue(vaso.bestDose != null ? vaso.bestDose.doseUgKgMin : mapObs.value);
        cardio.setUnit(hasDrug ? "µg/kg/min" : "mmHg");
        cardio.setDataTime(vaso.bestDose != null ? vaso.bestDose.time : mapObs.time);
        cardio.setMissing(mapObs.value == null && !hasDrug);
        cardio.setScore(cardioScore);
        cardio.setRangeText(rangeCardio(cardioScore));
        List<String> parts = new ArrayList<>();
        parts.add("MAP " + (mapObs.value == null ? "—" : round(mapObs.value, 0) + " mmHg"));
        for (VasoDose d : vaso.doses) {
            parts.add(d.drugName + " "
                    + (d.doseUgKgMin == null ? "剂量未归一" : round(d.doseUgKgMin, 3) + " µg/kg/min"));
        }
        cardio.setValueText(String.join(" / ", parts));
    }

    @Override
    public SofaAssessmentView getAssessmentByInHospitalNo(String inHospitalNo, String startTime, String endTime) {
        if (inHospitalNo == null || inHospitalNo.trim().isEmpty()) {
            throw new BizException(400, "缺少住院号 inHospitalNo");
        }
        String patientId = resolvePatientId(inHospitalNo);
        if (patientId == null) {
            throw new BizException(404, "未找到住院号对应的在科患者：" + inHospitalNo);
        }
        return getAssessment(patientId, startTime, endTime);
    }

    /** 住院号 → patientId（复用 ICU 只读查询，取最新在科记录） */
    private String resolvePatientId(String inHospitalNo) {
        Map<String, Object> p = icuPatientMapper.selectPatientByInHospitalNo(inHospitalNo);
        if (p == null) return null;
        String id = toStr(p.get("patient_id"));
        return id.isEmpty() ? null : id;
    }

    // ==================================================================
    // 6 项评分规则（纯函数，便于单测）
    // ==================================================================

    /** 呼吸：PaO2/FiO2；3/4 分要求有呼吸支持 */
    int scoreResp(Double pf, boolean support) {
        if (pf == null) return 0;
        if (pf >= 400) return 0;
        if (pf >= 300) return 1;
        if (pf >= 200) return 2;
        if (pf >= 100) return support ? 3 : 2;
        return support ? 4 : 2;
    }

    String rangeResp(Double pf, boolean support) {
        if (pf == null) return "—";
        if (pf >= 400) return "≥400";
        if (pf >= 300) return "<400";
        if (pf >= 200) return "<300";
        if (pf >= 100) return support ? "<200" : "<300（无呼吸支持，按2分）";
        return support ? "<100" : "<300（无呼吸支持，按2分）";
    }

    /** 凝血：血小板 ×10³/µL */
    int scoreCoag(Double plt) {
        if (plt == null) return 0;
        if (plt >= 150) return 0;
        if (plt >= 100) return 1;
        if (plt >= 50) return 2;
        if (plt >= 20) return 3;
        return 4;
    }

    String rangeCoag(Double plt) {
        if (plt == null) return "—";
        if (plt >= 150) return "≥150";
        if (plt >= 100) return "<150";
        if (plt >= 50) return "<100";
        if (plt >= 20) return "<50";
        return "<20";
    }

    /** 肝：总胆红素 mg/dL */
    int scoreLiver(Double biliMgDl) {
        if (biliMgDl == null) return 0;
        if (biliMgDl < 1.2) return 0;
        if (biliMgDl < 2.0) return 1;
        if (biliMgDl < 6.0) return 2;
        if (biliMgDl < 12.0) return 3;
        return 4;
    }

    String rangeLiver(Double biliMgDl) {
        if (biliMgDl == null) return "—";
        if (biliMgDl < 1.2) return "<1.2";
        if (biliMgDl < 2.0) return "1.2-1.9";
        if (biliMgDl < 6.0) return "2.0-5.9";
        if (biliMgDl < 12.0) return "6.0-11.9";
        return ">12";
    }

    /** 循环：MAP 部分 */
    int scoreCardioMap(Double map) {
        if (map == null) return 0;
        return map >= 70 ? 0 : 1;
    }

    String rangeCardio(int score) {
        switch (score) {
            case 0: return "MAP≥70";
            case 1: return "MAP<70";
            case 2: return "多巴胺<5 或 多巴酚丁胺";
            case 3: return "多巴胺5-15 或 肾上腺素≤0.1 或 去甲肾上腺素≤0.1";
            default: return "多巴胺>15 或 肾上腺素>0.1 或 去甲肾上腺素>0.1";
        }
    }

    /** 神经：GCS */
    int scoreNeuro(Integer gcs) {
        if (gcs == null) return 0;
        if (gcs >= 15) return 0;
        if (gcs >= 13) return 1;
        if (gcs >= 10) return 2;
        if (gcs >= 6) return 3;
        return 4;
    }

    String rangeNeuro(Integer gcs) {
        if (gcs == null) return "—";
        if (gcs >= 15) return "15";
        if (gcs >= 13) return "13-14";
        if (gcs >= 10) return "10-12";
        if (gcs >= 6) return "6-9";
        return "<6";
    }

    /** 肾：肌酐 mg/dL */
    int scoreRenalCreatinine(Double crMgDl) {
        if (crMgDl == null) return 0;
        if (crMgDl < 1.2) return 0;
        if (crMgDl < 2.0) return 1;
        if (crMgDl < 3.5) return 2;
        if (crMgDl < 5.0) return 3;
        return 4;
    }

    /** 肾：24h 尿量（mL） */
    int scoreRenalUrine(Double urineMl) {
        if (urineMl == null) return 0;
        if (urineMl < 200) return 4;
        if (urineMl < 500) return 3;
        return 0;
    }

    String rangeRenal(Double crMgDl, Double urineMl, int crScore, int urineScore) {
        if (crScore >= urineScore && crMgDl != null) {
            if (crMgDl < 1.2) return "<1.2";
            if (crMgDl < 2.0) return "1.2-1.9";
            if (crMgDl < 3.5) return "2.0-3.4";
            if (crMgDl < 5.0) return "3.5-4.9";
            return ">5.0";
        }
        if (urineMl != null) {
            if (urineMl < 200) return "尿量<200 mL/d";
            if (urineMl < 500) return "尿量<500 mL/d";
            return "尿量≥500 mL/d";
        }
        return "—";
    }

    // ==================================================================
    // 血管活性药剂量还原
    // ==================================================================

    /**
     * 还原窗口内血管活性药剂量（µg/kg/min），取分最高的药作为循环项药物分。
     * 药量取值优先级：drug_one_dosage → drug_dose → spec 解析；必须是质量单位方可换算。
     */
    private VasoResult resolveVasopressors(String inHospitalNo, String startTime, String endTime, double weightKg) {
        VasoResult r = new VasoResult();
        // 作废/停止但未删除的执行单会被“时间重叠”条件误判为在用，抬高循环分。
        // 是否按 status 过滤交由配置决定，默认**不过滤**（保持原有口径）：各院 status 字典不一致，
        // 且本项目已知 status 表达的是“执行状态”（HandoverServiceImpl 用 status='1' 表示执行中，
        // selectRunningAdvice 用 status IN (0,1) 表示执行中/未执行），并非“是否作废”。
        // 盲目加 status=1 会把“已完成但未删除”的历史医嘱剔出 24h 回顾窗口，反而漏计。
        // 现场确认字典后，在 SOFA 配置管理页新增 config_type=vasopressor、config_key=status_filter
        // （值如 "0,1" 或 "1"）即可启用；此处仅接受数字与逗号，防注入。
        String statusFilter = "";
        String rawFilter = loadConfigMap("vasopressor").get("status_filter");
        if (rawFilter != null && !rawFilter.trim().isEmpty()) {
            String f = rawFilter.trim().replace(" ", "");
            if (f.matches("[0-9]+(,[0-9]+)*")) {
                statusFilter = "AND e.status IN (" + f + ")";
            } else {
                log.warn("[SOFA] vasopressor.status_filter 配置非法（仅允许数字与逗号），已忽略: {}", rawFilter);
            }
        }
        List<Map<String, Object>> rows = safe(icuPatientMapper.selectVasopressorExecuteByRange(
                inHospitalNo, startTime, endTime, statusFilter));
        if (rows.isEmpty()) return r;

        // 批量取调速历史：拆成「窗口内调速」与「窗口开始前最后一次调速」两组。
        // 后者用于还原窗口起点时的在效泵速——若某泵在窗口开始前已调高、窗口内未再调速，
        // 只取窗口内记录会漏掉该速度（旧实现直接退回 drug_now_speed，补评历史窗口时低估循环分）。
        List<String> execIds = rows.stream()
                .map(x -> toStr(x.get("execute_id"))).filter(s -> !s.isEmpty())
                .distinct().collect(Collectors.toList());
        Map<String, Double> maxSpeedByExec = new HashMap<>();       // 窗口内最高泵速
        Map<String, Double> speedAtStartByExec = new HashMap<>();   // 窗口开始时在效泵速
        Map<String, String> lastStepBeforeStart = new HashMap<>();  // 该泵速对应的调速时刻
        if (!execIds.isEmpty()) {
            // 查询刻意不设时间下界（仅上界 endTime），故此处按 startTime 自行分流
            for (Map<String, Object> s : safe(icuPatientMapper.selectVasopressorSpeedSteps(execIds, endTime))) {
                String eid = toStr(s.get("execute_id"));
                Double sp = toDouble(s.get("speed"));
                String st = toStr(s.get("step_time"));
                if (eid.isEmpty() || sp == null) continue;
                if (!st.isEmpty() && st.compareTo(startTime) < 0) {
                    // 结果按 step_time 升序，后到者更晚，覆盖即为“窗口前最后一次调速”
                    String prev = lastStepBeforeStart.get(eid);
                    if (prev == null || st.compareTo(prev) >= 0) {
                        lastStepBeforeStart.put(eid, st);
                        speedAtStartByExec.put(eid, sp);
                    }
                } else {
                    maxSpeedByExec.merge(eid, sp, Math::max);
                }
            }
        }

        Map<String, String> vasoCfg = loadConfigMap("vasopressor");
        Map<String, String> convCfg = loadConfigMap("conversion");

        for (Map<String, Object> row : rows) {
            String drugName = toStr(row.get("drug_name"));
            String type = classifyVaso(drugName);
            if (type == null) continue;

            VasoDose d = new VasoDose();
            d.drugName = drugName.trim();
            d.drugType = type;
            d.time = toStr(row.get("start_time"));

            // 泵速：取「窗口内在效的最高泵速」，而非最新值（避免低估循环分）。
            // 候选 = 窗口内最高调速 ∪ 窗口开始时在效泵速（窗口前最后一次调速；无则退回起始泵速），
            // 并保留原有 current / start 兜底，保证结果不会低于旧口径。
            String eid = toStr(row.get("execute_id"));
            Double speed = maxSpeedByExec.get(eid);
            Double atStart = speedAtStartByExec.get(eid);
            if (atStart == null) atStart = toDouble(row.get("drug_start_speed"));
            if (atStart != null && (speed == null || atStart > speed)) speed = atStart;
            if (speed == null) speed = toDouble(row.get("drug_now_speed"));
            if (speed == null) speed = toDouble(row.get("drug_start_speed"));
            d.speedMlH = speed;

            String speedUnit = toStr(row.get("drug_speed_unit"));
            Double liquidTotal = toDouble(row.get("liquid_amount"));   // 执行主表：总液量 ml

            // 药物总量（µg）
            Double totalUg = massToUg(toDouble(row.get("drug_one_dosage")), toStr(row.get("drug_one_dosage_unit")));
            String massFrom = "drug_one_dosage";
            if (totalUg == null) {
                totalUg = massToUg(toDouble(row.get("drug_dose")), toStr(row.get("drug_dose_unit")));
                massFrom = "drug_dose";
            }
            if (totalUg == null) {
                totalUg = parseSpecMassUg(toStr(row.get("spec")), toDouble(row.get("drug_volume")));
                massFrom = "spec";
            }

            // 换算系数（重酒石酸盐↔碱基等，默认 1.0 = 按标示量）
            if (totalUg != null) {
                String f = convCfg.get(type);
                double factor = DEFAULT_FACTOR;
                if (f != null) {
                    try { factor = Double.parseDouble(f.trim()); } catch (NumberFormatException ignored) {}
                }
                if (factor != 1.0) totalUg = totalUg * factor;
            }

            if (totalUg == null) {
                d.degraded = true;
                d.note = "未识别到质量单位药量（单位：" + toStr(row.get("drug_dose_unit")) + "），按药名判定";
            } else if (liquidTotal == null || liquidTotal <= 0) {
                d.degraded = true;
                d.note = "未取到总液量，无法算浓度，按药名判定";
            } else if (speed == null || speed <= 0) {
                d.degraded = true;
                d.note = "未取到泵速，按药名判定";
            } else if (!speedUnit.isEmpty() && !"ml/h".equalsIgnoreCase(speedUnit)) {
                d.degraded = true;
                d.note = "泵速单位非 ml/h（" + speedUnit + "），暂不支持换算，按药名判定";
            } else {
                d.concUgMl = totalUg / liquidTotal;
                d.doseUgKgMin = d.concUgMl * speed / 60.0 / weightKg;
                d.massFrom = massFrom;
            }

            d.score = scoreVaso(d, vasoCfg);
            d.degraded = d.degraded || d.doseUgKgMin == null;
            r.doses.add(d);
            if (r.bestDose == null || d.score > r.bestDose.score
                    || (d.score == r.bestDose.score && nz(d.doseUgKgMin) > nz(r.bestDose.doseUgKgMin))) {
                r.bestDose = d;
            }
            if (d.degraded) r.hasDegraded = true;
            r.details.add(d.toMap());
        }
        r.score = r.bestDose == null ? 0 : r.bestDose.score;
        return r;
    }

    /**
     * 药名归类。
     * <p>注意「去甲肾上腺素」包含子串「肾上腺素」，必须先判去甲；
     * 药名常带前导/全角空格，先清理再匹配。
     */
    String classifyVaso(String drugName) {
        if (drugName == null) return null;
        String n = drugName.replace(" ", "").replace("　", "");
        if (n.contains("去甲肾上腺素")) return "norepinephrine";
        if (n.contains("多巴酚丁胺")) return "dobutamine";
        if (n.contains("多巴胺")) return "dopamine";
        if (n.contains("肾上腺素")) return "epinephrine";
        return null;
    }

    /**
     * 按剂量算血管活性药分。
     * <p>剂量未归一（降级）时按各药的**最低档**给分（多巴酚丁胺/多巴胺=2，肾上腺素/去甲肾上腺素=3），
     * 不做过度估计，并在界面标注需人工确认。
     */
    int scoreVaso(VasoDose d, Map<String, String> vasoCfg) {
        Double v = d.doseUgKgMin;
        switch (d.drugType) {
            case "dobutamine":
                return 2;   // 任意剂量即 2
            case "dopamine": {
                if (v == null) return 2;             // 降级：最低档
                double b1 = cfgThreshold(vasoCfg, "dopamine", 0, 5.0);
                double b2 = cfgThreshold(vasoCfg, "dopamine", 1, 15.0);
                // SOFA 标准：多巴胺 ≤5 = 2 分，>5~15 = 3 分，>15 = 4 分（边界含等号）
                if (v <= b1) return 2;
                if (v <= b2) return 3;
                return 4;
            }
            case "epinephrine":
            case "norepinephrine": {
                if (v == null) return 3;             // 降级：最低档
                double th = cfgThreshold(vasoCfg, d.drugType, 0, 0.1);
                return v <= th ? 3 : 4;
            }
            default:
                return 0;
        }
    }

    /** 从 vasopressor 配置的 "药名,阈值1[,阈值2]" 取阈值（parts[0] 是药名） */
    private double cfgThreshold(Map<String, String> vasoCfg, String key, int idx, double def) {
        String cfg = vasoCfg.get(key);
        if (cfg == null) return def;
        String[] parts = cfg.split(",");
        int p = idx + 1;
        if (parts.length > p) {
            try { return Double.parseDouble(parts[p].trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    /** 质量单位 → µg；非质量单位返回 null（不推测） */
    private Double massToUg(Double value, String unit) {
        if (value == null || unit == null) return null;
        String u = unit.trim().toLowerCase();
        if (!MASS_UNITS.contains(u)) return null;
        if (u.equals("g") || u.equals("克")) return value * 1_000_000.0;
        if (u.equals("mg") || u.equals("毫克")) return value * 1_000.0;
        return value;   // μg / ug / µg / mcg
    }

    /** 解析 spec（如 "1ml:2mg / 支"）× 药物体积（ml）→ 药物总量 µg */
    private Double parseSpecMassUg(String spec, Double volumeMl) {
        if (spec == null || spec.trim().isEmpty() || volumeMl == null || volumeMl <= 0) return null;
        Matcher m = SPEC_PATTERN.matcher(spec);
        if (!m.find()) return null;
        try {
            double specMl = Double.parseDouble(m.group(1));
            double specMass = Double.parseDouble(m.group(2));
            if (specMl <= 0) return null;
            Double massUg = massToUg(specMass, m.group(3));
            if (massUg == null) return null;
            return massUg / specMl * volumeMl;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================================================================
    // 体重
    // ==================================================================

    /** 体重解析：实际 → IBW(身高) → 年龄性别默认 → 70kg 兜底 */
    private Weight resolveWeight(Map<String, Object> p) {
        Double w = toDouble(p.get("weight"));
        if (w != null && w > 0) return new Weight(w, "actual", "实际体重");

        Double h = toDouble(p.get("height"));
        Integer age = toInt(p.get("age"));
        String gender = toStr(p.get("gender"));

        if (h != null && h > 0) {
            Double ibw = ibw(h, gender);
            if (ibw != null && ibw > 0) {
                return new Weight(ibw, "ibw", "IBW 估算，身高 " + round(h, 0) + "cm");
            }
        }
        Double dw = defaultWeight(age, gender);
        if (dw != null) {
            return new Weight(dw, "default_age_sex", "年龄性别默认值，待确认");
        }
        return new Weight(70.0, "fallback70", "兜底 70kg，待确认");
    }

    /** Devine 理想体重：男 50 + 2.3×(英寸-60)；女 45.5 + 2.3×(英寸-60) */
    private Double ibw(double heightCm, String gender) {
        double inches = heightCm / 2.54;
        boolean male = isMale(gender);
        double v = (male ? 50.0 : 45.5) + 2.3 * (inches - 60);
        return v > 0 ? round(v, 1) : null;
    }

    /** 年龄+性别默认体重（sofa_config: default_weight，键形如 M_18_39；<18 岁不适用） */
    private Double defaultWeight(Integer age, String gender) {
        if (age == null || age < 18) return null;
        String band;
        if (age <= 39) band = "18_39";
        else if (age <= 59) band = "40_59";
        else if (age <= 69) band = "60_69";
        else if (age <= 79) band = "70_79";
        else band = "80";
        Map<String, String> cfg = loadConfigMap("default_weight");
        String v = cfg.get((isMale(gender) ? "M_" : "F_") + band);
        if (v == null) return null;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private boolean isMale(String gender) {
        if (gender == null) return false;
        String g = gender.trim();
        return g.contains("男") || g.equalsIgnoreCase("M") || g.equalsIgnoreCase("male");
    }

    // ==================================================================
    // 取数辅助
    // ==================================================================

    /**
     * 总胆红素拾取（窗口内最高）。
     * <p>先按名称认定“总胆红素”（排除直接/间接）；名称不可用时退化为「候选 code 取最高」并提示核对。
     */
    private BilirubinPick pickTotalBilirubin(List<Map<String, Object>> rows) {
        BilirubinPick pick = new BilirubinPick();
        for (Map<String, Object> r : rows) {
            if (!isTotalBilirubin(toStr(r.get("item_name")))) continue;
            Double v = toDouble(r.get("item_value"));
            if (v == null) continue;
            if (pick.umol == null || v > pick.umol) {
                pick.umol = v;
                pick.time = toStr(r.get("item_time"));
                pick.pickedName = toStr(r.get("item_name"));
            }
        }
        if (pick.umol != null) return pick;

        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get("item_value"));
            if (v == null) continue;
            if (pick.umol == null || v > pick.umol) {
                pick.umol = v;
                pick.time = toStr(r.get("item_time"));
                pick.pickedName = toStr(r.get("item_name"));
            }
        }
        if (pick.umol != null) {
            pick.note = "未识别到“总胆红素”名称，按候选 code 取最高值，请核对";
        }
        return pick;
    }

    /** 与 PKPD 模块一致的总胆红素名称判定 */
    private boolean isTotalBilirubin(String itemName) {
        if (itemName == null) return false;
        String n = itemName.replace(" ", "");
        if (n.contains("直接") || n.contains("间接")) return false;
        String up = n.toUpperCase();
        return n.contains("总胆红素") || up.contains("TBIL") || up.contains("T-BIL");
    }

    /** 监护项按 item_code 取窗口内最低值 */
    private MapObs pickObserveMin(List<Map<String, Object>> rows, List<String> itemCodes) {
        MapObs out = new MapObs();
        if (itemCodes.isEmpty()) return out;
        Set<String> codeSet = new HashSet<>(itemCodes);
        double min = Double.MAX_VALUE;
        for (Map<String, Object> r : rows) {
            if (!codeSet.contains(toStr(r.get("item_code")))) continue;
            Double v = toDouble(r.get("item_value"));
            if (v == null) continue;
            if (v < min) {
                min = v;
                out.value = v;
                out.time = toStr(r.get("item_time"));
            }
        }
        return out;
    }

    /** 范围内最新一条“评全且非插管”的 GCS（与 APACHE II 同口径） */
    private Map<String, Object> latestGcs(String patientId, String startTime, String endTime) {
        try {
            // mapper 已按 record_time DESC，第一条三项齐全的即“范围内最新一条评全记录”
            for (Map<String, Object> g : parseGcsRows(safe(
                    icuPatientMapper.selectGcsDocRecordsByRange(patientId, startTime, endTime)))) {
                Integer e = toInt(g.get("eye"));
                Integer v = toInt(g.get("verbal"));
                Integer m = toInt(g.get("motor"));
                if (e == null || v == null || m == null) continue;
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("gcsTotal", e + v + m);
                out.put("gcsDetail", "E" + e + "V" + v + "M" + m);
                out.put("dataTime", toStr(g.get("recordTime")));
                out.put("eye", e);
                out.put("verbal", v);
                out.put("motor", m);
                return out;
            }
        } catch (Exception e) {
            log.warn("SOFA 同步 GCS 失败: patientId={}", patientId, e);
        }
        return null;
    }

    /**
     * 患者在重症系统（Z_ICU_GCS）已评估的 GCS 记录（时间倒序，含插管/未评全记录）。
     * <p>供评分页 GCS 弹窗的「选择已有记录 / 自动同步最新」使用；与 APACHE II 同源同口径。
     */
    @Override
    public List<Map<String, Object>> listSystemGcs(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) return new ArrayList<>();
        try {
            return parseGcsRows(safe(icuPatientMapper.selectGcsDocRecords(patientId)));
        } catch (Exception e) {
            log.warn("SOFA 查询重症系统 GCS 记录失败: patientId={}", patientId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析系统 GCS 文书原始行 → 结构化的 E/V/M 记录（保持 SQL 的 record_time 倒序）。
     * <p>item1=睁眼 / item2=言语 / item3=运动；item4=ET（气管插管或气切）时言语反应无法评估，
     * 即使 item2 带数值也不取 V，该条记录不可用于自动同步。
     */
    private List<Map<String, Object>> parseGcsRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        ObjectMapper om = new ObjectMapper();
        for (Map<String, Object> row : rows) {
            try {
                Object sj = row.get("score_json");
                if (sj == null || sj.toString().trim().isEmpty()) continue;
                JsonNode node = om.readTree(sj.toString());
                Integer eye = gcsDim(node, "item1", 1, 4);
                Integer motor = gcsDim(node, "item3", 1, 6);
                String item4 = node.hasNonNull("item4") ? node.get("item4").asText("").trim() : "";
                boolean intubated = "ET".equalsIgnoreCase(item4);
                Integer verbal = intubated ? null : gcsDim(node, "item2", 1, 5);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("recordTime", row.get("record_time") == null ? null : row.get("record_time").toString());
                item.put("recordStaffName", row.get("record_staff_name") == null ? null : row.get("record_staff_name").toString());
                item.put("eye", eye);
                item.put("verbal", verbal);
                item.put("motor", motor);
                item.put("intubated", intubated);
                item.put("totalText", (eye != null && verbal != null && motor != null)
                        ? String.valueOf(eye + verbal + motor) : "—");
                item.put("auditable", eye != null && verbal != null && motor != null);
                out.add(item);
            } catch (Exception ex) {
                log.warn("解析重症系统GCS记录失败，已跳过: docRecordId={}", row.get("doc_record_id"), ex);
            }
        }
        return out;
    }

    private Integer gcsDim(JsonNode node, String key, int lo, int hi) {
        if (node == null || !node.hasNonNull(key)) return null;
        String s = node.get(key).asText("").trim();
        if (!s.matches("-?\\d+")) return null;
        try {
            int v = Integer.parseInt(s);
            return (v >= lo && v <= hi) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================================================================
    // 持久化 / 总览 / 配置
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SofaScoreRecord saveRecord(SofaScoreRecord record, String startTime, String endTime) {
        if (record.getInHospitalNo() == null || record.getInHospitalNo().trim().isEmpty()) {
            throw new BizException(400, "缺少住院号");
        }
        if (record.getTotalScore() == null) {
            // 未传分值：按当前取数范围重新计算后落库
            SofaAssessmentView view = getAssessmentByInHospitalNo(record.getInHospitalNo(), startTime, endTime);
            fillFromView(record, view);
        }
        if (record.getScoreTime() == null) record.setScoreTime(LocalDateTime.now());
        if (record.getStatus() == null) record.setStatus(1);

        SofaScoreRecord last = latestRecord(record.getInHospitalNo());
        if (last != null && last.getTotalScore() != null && record.getTotalScore() != null
                && (record.getId() == null || !last.getId().equals(record.getId()))) {
            record.setDeltaSofa(record.getTotalScore() - last.getTotalScore());
        }

        // 操作人一律由服务端解析并覆盖：请求体里即使带着 createBy 也不采信，
        // 否则改一下请求就能把评分记录署成别人的名字
        String operator = OperatorContext.current();
        if (record.getId() == null) {
            record.setCreateTime(LocalDateTime.now());
            record.setCreateBy(operator);
            scoreRecordMapper.insert(record);
        } else {
            record.setUpdateTime(LocalDateTime.now());
            record.setUpdateBy(operator);
            scoreRecordMapper.updateById(record);
        }
        log.info("SOFA 评分保存完成: id={}, inHospitalNo={}, total={}, delta={}, operator={}",
                record.getId(), record.getInHospitalNo(), record.getTotalScore(),
                record.getDeltaSofa(), operator);
        return record;
    }

    private void fillFromView(SofaScoreRecord r, SofaAssessmentView v) {
        Map<String, Object> p = v.getPatient() == null ? new HashMap<>() : v.getPatient();
        r.setPatientId(toStr(p.get("patientId")));
        r.setPatientName(toStr(p.get("name")));
        r.setDepartCode(toStr(p.get("departCode")));
        if (v.getItems() != null) {
            Map<String, SofaAssessmentView.SofaItem> m = v.getItems().stream()
                    .collect(Collectors.toMap(SofaAssessmentView.SofaItem::getKey, i -> i, (a, b) -> a));
            r.setRespScore(scoreOf(m, "resp"));
            r.setCoagScore(scoreOf(m, "coag"));
            r.setLiverScore(scoreOf(m, "liver"));
            r.setCardioScore(scoreOf(m, "cardio"));
            r.setNeuroScore(scoreOf(m, "neuro"));
            r.setRenalScore(scoreOf(m, "renal"));
            r.setRespData(rawOf(m, "resp"));
            r.setCoagData(rawOf(m, "coag"));
            r.setLiverData(rawOf(m, "liver"));
            r.setCardioData(rawOf(m, "cardio"));
            r.setNeuroData(rawOf(m, "neuro"));
            r.setRenalData(rawOf(m, "renal"));
        }
        r.setTotalScore(v.getTotalScore());
        r.setUrineMl(v.getUrineMl());
        r.setGcsTotal(v.getGcsTotal());
        r.setGcsDetail(v.getGcsDetail());
        r.setRespiratorySupport(v.getRespiratorySupport());
        r.setWeightUsed(v.getWeightUsed());
        r.setWeightSource(v.getWeightSource());
        r.setDataStartTime(v.getDataStartTime());
        r.setDataEndTime(v.getDataEndTime());
        String remark = v.getRemark();
        if (v.getWeightNote() != null && !"actual".equals(v.getWeightSource())) {
            remark = (remark == null || remark.isEmpty() ? "" : remark + "；") + "体重来源：" + v.getWeightNote();
        }
        r.setRemark(remark);
        try {
            r.setVasopressorJson(new ObjectMapper().writeValueAsString(v.getVasopressors()));
        } catch (Exception ignored) {}
    }

    private Integer scoreOf(Map<String, SofaAssessmentView.SofaItem> m, String key) {
        SofaAssessmentView.SofaItem i = m.get(key);
        return i == null ? 0 : i.getScore();
    }

    private String rawOf(Map<String, SofaAssessmentView.SofaItem> m, String key) {
        SofaAssessmentView.SofaItem i = m.get(key);
        return i == null ? null : i.getRawJson();
    }

    @Override
    public List<SofaScoreRecord> listByPatient(String inHospitalNo) {
        if (inHospitalNo == null || inHospitalNo.trim().isEmpty()) return new ArrayList<>();
        // 定制 SQL：不返回 pdf_data 大字段，附带 has_pdf 供前端显示“文书”标记
        return scoreRecordMapper.selectRecordList(inHospitalNo);
    }

    /** 最近一条评分（达梦不支持 LIMIT，故取列表首条） */
    private SofaScoreRecord latestRecord(String inHospitalNo) {
        List<SofaScoreRecord> list = scoreRecordMapper.selectList(new LambdaQueryWrapper<SofaScoreRecord>()
                .eq(SofaScoreRecord::getInHospitalNo, inHospitalNo)
                .eq(SofaScoreRecord::getStatus, 1)
                .orderByDesc(SofaScoreRecord::getScoreTime));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRecord(Long id) {
        SofaScoreRecord r = scoreRecordMapper.selectById(id);
        if (r == null) return false;
        r.setStatus(0);
        r.setUpdateBy(OperatorContext.current());
        r.setUpdateTime(LocalDateTime.now());
        scoreRecordMapper.updateById(r);
        return true;
    }

    @Override
    public Map<String, Object> getOverview(String departCode, String startTime, String endTime) {
        LambdaQueryWrapper<SofaScoreRecord> w = new LambdaQueryWrapper<SofaScoreRecord>()
                .eq(SofaScoreRecord::getStatus, 1)
                .ge(SofaScoreRecord::getScoreTime, startTime)
                .le(SofaScoreRecord::getScoreTime, endTime)
                .orderByDesc(SofaScoreRecord::getScoreTime);
        if (departCode != null && !departCode.trim().isEmpty()) {
            w.eq(SofaScoreRecord::getDepartCode, departCode);
        }
        List<SofaScoreRecord> records = scoreRecordMapper.selectList(w);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", records.size());
        double avg = records.stream().mapToInt(r -> r.getTotalScore() == null ? 0 : r.getTotalScore())
                .average().orElse(0);
        result.put("avgScore", round(avg, 1));
        result.put("highRiskCount", records.stream()
                .filter(r -> r.getTotalScore() != null && r.getTotalScore() >= 10).count());
        // ΔSOFA 恶化预警（较上次升高 ≥2，对应 Sepsis-3 器官功能障碍阈值）
        List<SofaScoreRecord> worsened = records.stream()
                .filter(r -> r.getDeltaSofa() != null && r.getDeltaSofa() >= 2)
                .collect(Collectors.toList());
        result.put("worsenedCount", worsened.size());
        result.put("worsenedList", worsened);

        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("0-1", 0); dist.put("2-5", 0); dist.put("6-9", 0); dist.put("10-14", 0); dist.put("15-24", 0);
        for (SofaScoreRecord r : records) {
            int s = r.getTotalScore() == null ? 0 : r.getTotalScore();
            if (s <= 1) dist.merge("0-1", 1, Integer::sum);
            else if (s <= 5) dist.merge("2-5", 1, Integer::sum);
            else if (s <= 9) dist.merge("6-9", 1, Integer::sum);
            else if (s <= 14) dist.merge("10-14", 1, Integer::sum);
            else dist.merge("15-24", 1, Integer::sum);
        }
        result.put("scoreDistribution", dist);
        result.put("records", records);
        return result;
    }

    @Override
    public List<Map<String, Object>> listConfig(String configType) {
        // 返回全部状态（含停用），并带 status 字段，供配置管理后台启用/停用；
        // 运行时取值由 loadConfigMap 过滤 status=1。
        LambdaQueryWrapper<SofaConfig> w = new LambdaQueryWrapper<SofaConfig>()
                .orderByAsc(SofaConfig::getConfigType)
                .orderByAsc(SofaConfig::getSortNo);
        if (configType != null && !configType.trim().isEmpty()) {
            w.eq(SofaConfig::getConfigType, configType);
        }
        return configMapper.selectList(w).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("configType", c.getConfigType());
            m.put("configKey", c.getConfigKey());
            m.put("configValue", c.getConfigValue());
            m.put("itemName", c.getItemName());
            m.put("remark", c.getRemark());
            m.put("sortNo", c.getSortNo());
            m.put("status", c.getStatus());
            m.put("updateTime", c.getUpdateTime());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================================================================
    // 文书 PDF（与主体保存解耦）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean attachPdf(Long id, String pdfData, String pdfName) {
        if (id == null || pdfData == null || pdfData.trim().isEmpty()) return false;
        SofaScoreRecord r = scoreRecordMapper.selectById(id);
        if (r == null) return false;
        r.setPdfData(pdfData);
        // 仅在传入文件名时覆盖，避免把已有名字冲掉
        if (pdfName != null && !pdfName.trim().isEmpty()) {
            r.setPdfName(pdfName.trim());
        }
        r.setUpdateBy(OperatorContext.current());
        r.setUpdateTime(LocalDateTime.now());
        scoreRecordMapper.updateById(r);
        log.info("SOFA 文书已归档: id={}, inHospitalNo={}, 大小≈{}KB",
                id, r.getInHospitalNo(),
                pdfData.length() * 3 / 4 / 1024);
        return true;
    }

    @Override
    public SofaScoreRecord getRecordPdf(Long id) {
        if (id == null) return null;
        return scoreRecordMapper.selectPdfById(id);
    }

    // ==================================================================
    // 每日自动初评
    // ==================================================================

    /** 自动初评互斥锁（单实例）：定时任务与手动触发可能并发，避免“先查后插”竞态重复建档 */
    private final java.util.concurrent.locks.ReentrantLock autoGenerateLock =
            new java.util.concurrent.locks.ReentrantLock();

    @Override
    public Map<String, Object> autoGenerateScores(String departCode, int overHours) {
        autoGenerateLock.lock();
        try {
            return doAutoGenerateScores(departCode, overHours);
        } finally {
            autoGenerateLock.unlock();
        }
    }

    private Map<String, Object> doAutoGenerateScores(String departCode, int overHours) {
        Map<String, Object> result = new LinkedHashMap<>();
        int scanned = 0, created = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        String dep = (departCode == null || departCode.trim().isEmpty()) ? null : departCode.trim();
        List<Map<String, Object>> patients;
        try {
            patients = safe(icuPatientMapper.selectInDepartPatientsOverHours(overHours, dep));
        } catch (Exception e) {
            log.error("[SOFA] 查询在科患者失败", e);
            result.put("scanned", 0);
            result.put("created", 0);
            result.put("skipped", 0);
            result.put("failed", 0);
            result.put("errors", Collections.singletonList("查询在科患者失败: " + e.getMessage()));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Map<String, Object> p : patients) {
            scanned++;
            String pid = toStr(p.get("patient_id"));
            String no = toStr(p.get("in_hospital_no"));
            if (pid.isEmpty() || no.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                // 幂等：已存在任何评分记录则跳过（定时任务重复执行/手动补跑都安全）。
                // 产品口径（2026-09-15）：SOFA 改为「入科满 24h 后终身一条」，与 APACHE II 一致。
                // 评分历史趋势功能已下线，历史记录列表仅用于回看/补评。
                Long existCount = scoreRecordMapper.selectCount(new LambdaQueryWrapper<SofaScoreRecord>()
                        .eq(SofaScoreRecord::getInHospitalNo, no)
                        .eq(SofaScoreRecord::getStatus, 1));
                if (existCount != null && existCount > 0) {
                    skipped++;
                    continue;
                }

                // 取数范围：标准 SOFA 口径「评估时点前 24h 内最差值」。
                // 不可用「当日 0 点 → 现在」：定时任务 02:30 触发时窗口仅约 2.5h，
                // 会导致大量指标无数据而记 0 分（总分被严重低估），且尿量判据要求窗口 ≥20h 会被直接跳过，
                // 也与评分页默认「过去 24h」口径不一致（自动分与手评分不可比）。
                // 入科不足 24h 时从入科时间起算，不跨越入科前。
                LocalDateTime start = now.minusHours(24);
                LocalDateTime inDepart = parseDateTime(toStr(p.get("in_depart_time")));
                if (inDepart != null && inDepart.isAfter(start)) start = inDepart;

                SofaAssessmentView view = getAssessment(pid, start.format(DT_FMT), now.format(DT_FMT));

                SofaScoreRecord rec = new SofaScoreRecord();
                rec.setInHospitalNo(no);
                rec.setScoreTime(now);
                rec.setScoreType("daily");
                rec.setCreateBy("系统自动");
                rec.setStatus(1);
                rec.setCreateTime(now);
                fillFromView(rec, view);
                SofaScoreRecord last = latestRecord(no);
                if (last != null && last.getTotalScore() != null && rec.getTotalScore() != null) {
                    rec.setDeltaSofa(rec.getTotalScore() - last.getTotalScore());
                }
                scoreRecordMapper.insert(rec);
                created++;
            } catch (Exception e) {
                failed++;
                if (errors.size() < 10) {
                    errors.add(no + ": " + e.getMessage());
                }
                log.warn("[SOFA] 自动初评失败: inHospitalNo={}", no, e);
            }
        }

        result.put("scanned", scanned);
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("errors", errors);
        log.info("[SOFA] 自动初评结束: 扫描={}, 建档={}, 跳过={}, 失败={}", scanned, created, skipped, failed);
        return result;
    }

    // ==================================================================
    // 指标趋势（来源弹窗用）
    // ==================================================================

    @Override
    public List<Map<String, Object>> getMetricTrend(String patientId, String metricKey,
                                                    String startTime, String endTime) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (metricKey == null || metricKey.trim().isEmpty()) return out;

        Map<String, Object> p = icuPatientMapper.selectSofaPatientBase(patientId);
        if (p == null) return out;
        String inHospitalNo = toStr(p.get("in_hospital_no"));
        if (inHospitalNo.isEmpty()) return out;

        Map<String, String> lisCfg = loadConfigMap("lis_item");
        Map<String, String> obsCfg = loadConfigMap("observe_item");

        switch (metricKey.trim()) {
            // 呼吸：氧合指数 PaO2/FiO2
            case "resp":
                for (Map<String, Object> r : safe(icuPatientMapper.selectOxygenationHistoryByRange(
                        patientId, startTime, endTime))) {
                    Double v = toDouble(r.get("oxygenation_index"));
                    if (v != null) out.add(trendPoint(toStr(r.get("check_time")), v));
                }
                break;
            // 凝血：血小板
            case "coag":
                for (Map<String, Object> r : safe(icuPatientMapper.selectLabRecordsByCodes(
                        inHospitalNo, codes(lisCfg.get("platelet")), startTime, endTime))) {
                    Double v = toDouble(r.get("item_value"));
                    if (v != null) out.add(trendPoint(toStr(r.get("item_time")), v));
                }
                break;
            // 肝：总胆红素（µmol/L → mg/dL）
            case "liver":
                for (Map<String, Object> r : safe(icuPatientMapper.selectLabRecordsByCodes(
                        inHospitalNo, codes(lisCfg.get("bilirubin")), startTime, endTime))) {
                    if (!isTotalBilirubin(toStr(r.get("item_name")))) continue;
                    Double v = toDouble(r.get("item_value"));
                    if (v != null) out.add(trendPoint(toStr(r.get("item_time")), round(v / 17.1, 2)));
                }
                break;
            // 循环：MAP（血管活性药剂量按泵速时间线离散，不并入单值趋势）
            case "cardio": {
                Set<String> mapCodes = new HashSet<>(codes(obsCfg.get("map")));
                for (Map<String, Object> r : safe(icuPatientMapper.selectObserveRecords(
                        patientId, startTime, endTime))) {
                    if (!mapCodes.contains(toStr(r.get("item_code")))) continue;
                    Double v = toDouble(r.get("item_value"));
                    if (v != null) out.add(trendPoint(toStr(r.get("item_time")), v));
                }
                break;
            }
            // 神经：GCS 总分（仅评全且非插管的记录）
            case "neuro":
                for (Map<String, Object> g : parseGcsRows(safe(
                        icuPatientMapper.selectGcsDocRecordsByRange(patientId, startTime, endTime)))) {
                    Integer e = toInt(g.get("eye"));
                    Integer v = toInt(g.get("verbal"));
                    Integer m = toInt(g.get("motor"));
                    if (e != null && v != null && m != null) {
                        out.add(trendPoint(toStr(g.get("recordTime")), (double) (e + v + m)));
                    }
                }
                break;
            // 肾：肌酐（µmol/L → mg/dL）
            case "renal":
                for (Map<String, Object> r : safe(icuPatientMapper.selectLabRecordsByCodes(
                        inHospitalNo, codes(lisCfg.get("creatinine")), startTime, endTime))) {
                    Double v = toDouble(r.get("item_value"));
                    if (v != null) out.add(trendPoint(toStr(r.get("item_time")), round(v / 88.4, 2)));
                }
                break;
            // 注：原「total（SOFA 总分历史趋势）」分支已随评分历史趋势功能一并删除，
            //     此处仅保留 6 个器官指标，供评分页「来源」弹窗核对取数依据。
            default:
                break;
        }

        // 统一按时间升序（各来源 SQL 排序不一致）
        out.sort(Comparator.comparing(m -> toStr(m.get("time"))));
        return out;
    }

    // ==================================================================
    // 配置管理（后台）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfig(Map<String, Object> body) {
        if (body == null) return false;
        String type = toStr(body.get("configType"));
        String key = toStr(body.get("configKey"));
        if (type.isEmpty() || key.isEmpty()) {
            throw new BizException(400, "配置类型与配置键不能为空");
        }
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        SofaConfig c = null;
        if (id != null) {
            c = configMapper.selectById(id);
            if (c == null) {
                throw new BizException(404, "配置不存在：" + id);
            }
        } else {
            // 同类型同键视为同一条，避免重复项导致取数歧义
            List<SofaConfig> exist = configMapper.selectList(new LambdaQueryWrapper<SofaConfig>()
                    .eq(SofaConfig::getConfigType, type)
                    .eq(SofaConfig::getConfigKey, key));
            if (!exist.isEmpty()) {
                c = exist.get(0);
            } else {
                c = new SofaConfig();
                c.setStatus(1);
                c.setCreateTime(LocalDateTime.now());
            }
        }
        c.setConfigType(type);
        c.setConfigKey(key);
        c.setConfigValue(toStr(body.get("configValue")));
        c.setItemName(toStr(body.get("itemName")));
        c.setRemark(toStr(body.get("remark")));
        Integer sortNo = toInt(body.get("sortNo"));
        c.setSortNo(sortNo == null ? 1 : sortNo);
        c.setUpdateTime(LocalDateTime.now());
        if (c.getId() == null) {
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfig(Long id) {
        if (id == null) return false;
        SofaConfig c = configMapper.selectById(id);
        if (c == null) return false;
        c.setStatus(0);
        c.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(c);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleConfig(Long id, Integer status) {
        if (id == null || status == null) return false;
        SofaConfig c = configMapper.selectById(id);
        if (c == null) return false;
        c.setStatus(status);
        c.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(c);
        return true;
    }

    // ==================================================================
    // 配置加载（表空时回退内置默认）
    // ==================================================================

    private Map<String, String> loadConfigMap(String configType) {
        Map<String, String> map = new HashMap<>();
        try {
            for (Map<String, Object> m : listConfig(configType)) {
                // listConfig 返回含停用项（供后台管理），运行时取值只认启用项
                Integer st = toInt(m.get("status"));
                if (st != null && st != 1) continue;
                map.put(toStr(m.get("configKey")), toStr(m.get("configValue")));
            }
        } catch (Exception e) {
            log.warn("加载 SOFA 配置失败，使用内置默认: configType={}", configType);
        }
        switch (configType) {
            case "lis_item":
                map.putIfAbsent("platelet", "200050");
                map.putIfAbsent("bilirubin", "100010,100020,100030");
                map.putIfAbsent("creatinine", "100210");
                break;
            case "observe_item":
                map.putIfAbsent("map", "oi_ycpjy,oi_pjy");
                map.putIfAbsent("fio2", "oi_FiO2(设置值)");
                map.putIfAbsent("peep", "oi_peep");
                break;
            case "io_item":
                map.putIfAbsent("urine", "ii_nl");
                break;
            case "vasopressor":
                map.putIfAbsent("norepinephrine", "去甲肾上腺素,0.1");
                map.putIfAbsent("epinephrine", "肾上腺素,0.1");
                map.putIfAbsent("dopamine", "多巴胺,5,15");
                map.putIfAbsent("dobutamine", "多巴酚丁胺");
                break;
            case "conversion":
                map.putIfAbsent("norepinephrine", "1.0");
                map.putIfAbsent("epinephrine", "1.0");
                break;
            case "default_weight":
                map.putIfAbsent("M_18_39", "70"); map.putIfAbsent("F_18_39", "58");
                map.putIfAbsent("M_40_59", "70"); map.putIfAbsent("F_40_59", "60");
                map.putIfAbsent("M_60_69", "67"); map.putIfAbsent("F_60_69", "58");
                map.putIfAbsent("M_70_79", "65"); map.putIfAbsent("F_70_79", "55");
                map.putIfAbsent("M_80", "62");    map.putIfAbsent("F_80", "52");
                break;
            default:
                break;
        }
        return map;
    }

    // ==================================================================
    // 通用小工具
    // ==================================================================

    private SofaAssessmentView.SofaItem item(String key, String label) {
        SofaAssessmentView.SofaItem i = new SofaAssessmentView.SofaItem();
        i.setKey(key);
        i.setLabel(label);
        i.setScore(0);
        i.setMissing(false);
        return i;
    }

    private Map<String, Object> buildPatient(Map<String, Object> p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patientId", p.get("patient_id"));
        m.put("inHospitalNo", p.get("in_hospital_no"));
        m.put("name", p.get("name"));
        m.put("bedCode", p.get("bed_code"));
        m.put("age", p.get("age"));
        m.put("ageUnit", p.get("age_unit"));
        m.put("gender", p.get("gender"));
        m.put("departCode", p.get("depart_code"));
        m.put("departName", p.get("depart_name"));
        m.put("inDepartTime", p.get("in_depart_time"));
        m.put("weight", p.get("weight"));
        m.put("height", p.get("height"));
        return m;
    }

    private static List<String> codes(String cfg) {
        if (cfg == null || cfg.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(cfg.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private Double minOf(List<Map<String, Object>> rows, String key) {
        Double out = null;
        double min = Double.MAX_VALUE;
        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get(key));
            if (v != null && v < min) { min = v; out = v; }
        }
        return out;
    }

    private Double maxOf(List<Map<String, Object>> rows, String key) {
        Double out = null;
        double max = -Double.MAX_VALUE;
        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get(key));
            if (v != null && v > max) { max = v; out = v; }
        }
        return out;
    }

    private Double sumOf(List<Map<String, Object>> rows, String key) {
        double sum = 0;
        boolean any = false;
        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get(key));
            if (v != null) { sum += v; any = true; }
        }
        return any ? sum : null;
    }

    /** 最小值所在记录的时间 */
    private String timeOfMin(List<Map<String, Object>> rows, String valueKey, String timeKey) {
        String out = null;
        double best = Double.MAX_VALUE;
        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get(valueKey));
            if (v == null) continue;
            if (v < best) { best = v; out = toStr(r.get(timeKey)); }
        }
        return out;
    }

    /** 最大值所在记录的时间 */
    private String timeOfMax(List<Map<String, Object>> rows, String valueKey, String timeKey) {
        String out = null;
        double best = -Double.MAX_VALUE;
        for (Map<String, Object> r : rows) {
            Double v = toDouble(r.get(valueKey));
            if (v == null) continue;
            if (v > best) { best = v; out = toStr(r.get(timeKey)); }
        }
        return out;
    }

    /** 构造趋势点 {time, value}（与 APACHE II 的 /metric-trend 返回结构一致，前端可复用渲染） */
    private Map<String, Object> trendPoint(String timeText, Double value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("time", timeText);
        p.put("value", value);
        return p;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String toStr(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        Double d = toDouble(o);
        return d == null ? null : (int) Math.round(d);
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            String t = s.trim();
            if (t.length() > 19) t = t.substring(0, 19);
            return LocalDateTime.parse(t.replace('T', ' '), DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static double windowHours(String startTime, String endTime) {
        LocalDateTime s = parseDateTime(startTime);
        LocalDateTime e = parseDateTime(endTime);
        if (s == null || e == null) return 0;
        return Duration.between(s, e).toMinutes() / 60.0;
    }

    private static String json(Map<String, Object> m) {
        try {
            return new ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ---------------- 内部结果对象 ----------------

    private static class Weight {
        final double kg;
        final String source;
        final String note;
        Weight(double kg, String source, String note) {
            this.kg = kg; this.source = source; this.note = note;
        }
    }

    private static class MapObs {
        Double value;
        String time;
    }

    private static class BilirubinPick {
        Double umol;
        String time;
        String pickedName;
        String note;
    }

    /** 单个血管活性药的剂量还原结果 */
    static class VasoDose {
        String drugName;
        String drugType;
        String time;
        Double speedMlH;
        Double concUgMl;        // 浓度 µg/ml
        Double doseUgKgMin;     // 归一剂量 µg/kg/min（null=降级）
        String massFrom;        // drug_one_dosage / drug_dose / spec
        boolean degraded;
        String note;
        int score;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("drugName", drugName);
            m.put("drugType", drugType);
            m.put("time", time);
            m.put("speedMlH", speedMlH == null ? null : round(speedMlH, 2));
            m.put("concUgMl", concUgMl == null ? null : round(concUgMl, 2));
            m.put("doseUgKgMin", doseUgKgMin == null ? null : round(doseUgKgMin, 4));
            m.put("massFrom", massFrom);
            m.put("degraded", degraded);
            m.put("note", note);
            m.put("score", score);
            return m;
        }
    }

    private static class VasoResult {
        final List<VasoDose> doses = new ArrayList<>();
        final List<Map<String, Object>> details = new ArrayList<>();
        VasoDose bestDose;
        int score = 0;
        boolean hasDegraded = false;
    }
}
