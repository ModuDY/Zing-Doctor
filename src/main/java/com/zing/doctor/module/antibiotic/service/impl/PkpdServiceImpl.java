package com.zing.doctor.module.antibiotic.service.impl;

import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.AbxCurrentItem;
import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.dto.*;
import com.zing.doctor.module.antibiotic.knowledge.AbxDrugKnowledge;
import com.zing.doctor.module.antibiotic.service.PkpdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 第二维度：PK/PD 抗菌药物剂量优化 Service 实现。
 *
 * <p>核心计算：
 * <ul>
 *   <li>肾功能：Cockcroft-Gault 肌酐清除率 + CKD-EPI eGFR + KDIGO 分级</li>
 *   <li>营养状态：BMI + 理想体重 IBW + 调整体重 AdjBW</li>
 *   <li>肝功能：胆红素/白蛋白/INR（INR 从 lis_item_short_name 匹配）</li>
 *   <li>特殊状态：低蛋白血症 / CRRT / ECMO</li>
 *   <li>药物 PK/PD 分析：基于内置 22 种药物知识库匹配</li>
 *   <li>剂量优化建议：肾功能调整 / 体重调整 / 低蛋白血症 / 负荷剂量 / 延长输注</li>
 *   <li>TDM 目标值：当前药物中需 TDM 的展示目标值和监测时机</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PkpdServiceImpl implements PkpdService {

    private final IcuPatientService icuPatientService;
    private final IcuPatientMapper icuPatientMapper;

    @Override
    public PkpdAssessmentView getPkpdAssessment(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) {
            return null;
        }
        IcuPatientBrief patient = icuPatientService.getPatientBrief(patientId);
        if (patient == null) {
            return null;
        }
        return buildPkpdView(patient);
    }

    @Override
    public PkpdAssessmentView getPkpdAssessmentByInHospitalNo(String inHospitalNo) {
        if (inHospitalNo == null || inHospitalNo.trim().isEmpty()) {
            throw new BizException(400, "缺少住院号参数 inHospitalNo");
        }
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            // 与第一维度（/patients/by-no/assessment）口径一致：明确报错。
            // 原实现返回 null → 200 + data:null，前端只渲染出一张空白页，既无提示也无从排查。
            throw new BizException(404, "未找到住院号对应的在科患者：" + inHospitalNo);
        }
        return getPkpdAssessment(patientId);
    }

    // ==================================================================
    // 核心组装
    // ==================================================================

    private PkpdAssessmentView buildPkpdView(IcuPatientBrief patient) {
        PkpdAssessmentView view = new PkpdAssessmentView();
        view.setPatient(patient);

        String patientId = patient.getPatientId();
        String inHospitalNo = patient.getPatientNo();

        // 获取 ICU 评估数据（含当前抗菌药、检验、过敏等）
        IcuPatientAssessment assessment = icuPatientService.getAssessment(patientId);
        if (assessment != null) {
            view.setCurrentAbx(assessment.getCurrentAntibiotics());
        } else {
            view.setCurrentAbx(Collections.emptyList());
        }

        // 1. 肾功能评估
        view.setRenal(buildRenalAssessment(patient, inHospitalNo));

        // 2. 营养状态
        view.setNutrition(buildNutritionAssessment(patient));

        // 3. 肝功能评估
        view.setLiver(buildLiverAssessment(inHospitalNo));

        // 4. 特殊状态
        view.setSpecialStatus(buildSpecialStatus(patient, assessment, inHospitalNo));

        // 5. 药物 PK/PD 分析
        view.setDrugAnalysis(buildDrugAnalysis(view.getCurrentAbx()));

        // 6. 剂量优化建议
        view.setRecommendations(buildDoseRecommendations(view.getCurrentAbx(), view.getRenal(),
                view.getNutrition(), view.getSpecialStatus()));

        // 7. TDM 目标值
        view.setTdmTargets(buildTdmTargets(view.getCurrentAbx()));

        // 8. 药物相互作用提醒（简化版：肾毒性叠加）
        view.setInteractionAlerts(buildInteractionAlerts(view.getCurrentAbx(), view.getRenal()));

        return view;
    }

    // ==================================================================
    // 1. 肾功能评估
    // ==================================================================

    private RenalAssessment buildRenalAssessment(IcuPatientBrief patient, String inHospitalNo) {
        RenalAssessment renal = new RenalAssessment();

        // 从检验数据中取最新肌酐
        BigDecimal creatinine = null;
        String unit = "μmol/L";
        List<RenalAssessment.LabTrendPoint> trend = new ArrayList<>();

        try {
            List<Map<String, Object>> labs = icuPatientMapper.selectRecentLabs(inHospitalNo);
            for (Map<String, Object> lab : labs) {
                String name = str(lab.get("item_name"));
                String shortName = str(lab.get("short_name"));
                String result = str(lab.get("result"));
                String u = str(lab.get("unit"));
                String time = str(lab.get("check_time"));
                if (isCreatinine(name, shortName) && isNumeric(result)) {
                    BigDecimal val = new BigDecimal(result);
                    if (creatinine == null) {
                        creatinine = val;
                        if (u != null && !u.isEmpty()) unit = u;
                    }
                    if (time != null && !time.isEmpty()) {
                        RenalAssessment.LabTrendPoint p = new RenalAssessment.LabTrendPoint();
                        p.setTime(formatTimeShort(time));
                        p.setValue(val);
                        p.setUnit(u);
                        trend.add(p);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询肌酐趋势失败: {}", e.getMessage());
        }

        // 反转趋势（按时间正序）
        Collections.reverse(trend);
        renal.setCreatinineTrend(trend);

        if (creatinine == null) {
            renal.setCreatinine(null);
            renal.setCrcl(null);
            renal.setEgfr(null);
            renal.setRenalStage("unknown");
            renal.setRenalStageText("无肌酐数据");
            renal.setFormulaNote("暂无肌酐检验数据，无法计算肾功能");
            return renal;
        }

        renal.setCreatinine(creatinine);
        renal.setCreatinineUnit(unit);

        // 肌酐单位换算：μmol/L → mg/dL（除以 88.4）
        BigDecimal crMgDl;
        if (unit != null && unit.toLowerCase().contains("mg")) {
            crMgDl = creatinine;
        } else {
            crMgDl = creatinine.divide(new BigDecimal("88.4"), 4, RoundingMode.HALF_UP);
        }
        renal.setCreatinineMgDl(crMgDl);

        // Cockcroft-Gault 肌酐清除率
        Integer age = patient.getAge();
        BigDecimal weight = patient.getWeight();
        String gender = patient.getGender();
        if (age != null && weight != null && gender != null) {
            BigDecimal crcl = calcCrclCockcroftGault(age, weight, crMgDl, gender);
            renal.setCrcl(crcl);

            // CKD-EPI eGFR
            BigDecimal egfr = calcEgfrCkdEpi(age, crMgDl, gender);
            renal.setEgfr(egfr);

            // 肾功能分级（基于 eGFR，KDIGO）
            String[] stage = classifyRenalStage(egfr);
            renal.setRenalStage(stage[0]);
            renal.setRenalStageText(stage[1]);

            renal.setFormulaNote("Cockcroft-Gault: CrCl=[(140-年龄)×体重]/[72×Scr(mg/dL)]（女性×0.85）；CKD-EPI eGFR 公式");
        } else {
            renal.setCrcl(null);
            renal.setEgfr(null);
            renal.setRenalStage("unknown");
            renal.setRenalStageText("年龄/体重/性别不全");
            renal.setFormulaNote("患者年龄、体重、性别信息不全，无法计算肌酐清除率");
        }

        return renal;
    }

    /** Cockcroft-Gault 肌酐清除率（mL/min） */
    private BigDecimal calcCrclCockcroftGault(int age, BigDecimal weightKg, BigDecimal crMgDl, String gender) {
        if (crMgDl == null || crMgDl.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal numerator = new BigDecimal(140 - age).multiply(weightKg);
        BigDecimal denominator = new BigDecimal("72").multiply(crMgDl);
        BigDecimal crcl = numerator.divide(denominator, 1, RoundingMode.HALF_UP);
        if (gender != null && gender.contains("女")) {
            crcl = crcl.multiply(new BigDecimal("0.85")).setScale(1, RoundingMode.HALF_UP);
        }
        return crcl;
    }

    /** CKD-EPI eGFR（简化版，不考虑种族，mL/min/1.73m²） */
    private BigDecimal calcEgfrCkdEpi(int age, BigDecimal crMgDl, String gender) {
        if (crMgDl == null || crMgDl.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        boolean female = gender != null && gender.contains("女");
        double k = female ? 0.7 : 0.9;
        double a = female ? (crMgDl.doubleValue() <= k ? -0.329 : -1.209)
                : (crMgDl.doubleValue() <= k ? -0.411 : -1.209);
        double base = female ? 144 : 141;
        double egfr = base * Math.pow(crMgDl.doubleValue() / k, a) * Math.pow(0.993, age);
        return new BigDecimal(egfr).setScale(1, RoundingMode.HALF_UP);
    }

    /** KDIGO 肾功能分级 */
    private String[] classifyRenalStage(BigDecimal egfr) {
        if (egfr == null) return new String[]{"unknown", "未知"};
        double v = egfr.doubleValue();
        if (v >= 90) return new String[]{"G1", "正常或增高"};
        if (v >= 60) return new String[]{"G2", "轻度下降"};
        if (v >= 45) return new String[]{"G3a", "轻中度下降"};
        if (v >= 30) return new String[]{"G3b", "中重度下降"};
        if (v >= 15) return new String[]{"G4", "重度下降"};
        return new String[]{"G5", "肾衰竭"};
    }

    // ==================================================================
    // 2. 营养状态
    // ==================================================================

    private PkpdAssessmentView.NutritionAssessment buildNutritionAssessment(IcuPatientBrief patient) {
        PkpdAssessmentView.NutritionAssessment nutrition = new PkpdAssessmentView.NutritionAssessment();
        BigDecimal weight = patient.getWeight();
        BigDecimal height = patient.getHeight();
        String gender = patient.getGender();

        nutrition.setWeight(weight);
        nutrition.setHeight(height);

        // 身高为脏数据（如 0.001）时跳过 BMI/IBW 计算：
        // 原实现只判 height > 0，而 height.divide(100, 4, HALF_UP) 会把 (0, 0.005) 的身高
        // 直接舍入成 0.0000，heightM² 为 0 时 BMI 除法抛 ArithmeticException: / by zero，
        // 导致整个 PK/PD 页面 500（只有身高脏数据的患者会触发）。
        // 这类身高算出的 BMI/IBW 也不可信，跳过比给出错误剂量建议更安全。
        if (weight != null && height != null && isPlausibleHeightCm(height)) {
            // BMI = 体重(kg) / 身高(m)²
            BigDecimal heightM = height.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal bmi = weight.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
            nutrition.setBmi(bmi);

            // BMI 分级（中国标准）
            double b = bmi.doubleValue();
            if (b < 18.5) nutrition.setBmiCategory("偏瘦");
            else if (b < 24) nutrition.setBmiCategory("正常");
            else if (b < 28) nutrition.setBmiCategory("超重");
            else if (b < 32) nutrition.setBmiCategory("肥胖");
            else nutrition.setBmiCategory("重度肥胖");

            // 理想体重 IBW（Devine 公式）
            if (gender != null) {
                double heightInch = height.doubleValue() / 2.54;
                double ibwBase = gender.contains("女") ? 45.5 : 50;
                double ibw = ibwBase + 2.3 * Math.max(0, heightInch - 60);
                BigDecimal ibwBd = new BigDecimal(ibw).setScale(1, RoundingMode.HALF_UP);
                nutrition.setIbw(ibwBd);

                // 调整体重 AdjBW（肥胖患者用）
                boolean obese = weight.doubleValue() > 1.3 * ibw;
                nutrition.setObese(obese);
                if (obese) {
                    double adjBw = ibw + 0.4 * (weight.doubleValue() - ibw);
                    nutrition.setAdjBw(new BigDecimal(adjBw).setScale(1, RoundingMode.HALF_UP));
                } else {
                    nutrition.setAdjBw(weight);
                }
            }
        } else if (weight != null && height != null) {
            log.warn("身高不在可信范围(30~250cm)，跳过 BMI/IBW 计算: height={}", height);
        }

        return nutrition;
    }

    /** 身高是否为可信的厘米值（30~250cm）。脏数据会让 BMI 除法抛 / by zero。 */
    private boolean isPlausibleHeightCm(BigDecimal height) {
        return height.compareTo(new BigDecimal("30")) >= 0
                && height.compareTo(new BigDecimal("250")) <= 0;
    }

    // ==================================================================
    // 3. 肝功能评估
    // ==================================================================

    private PkpdAssessmentView.LiverAssessment buildLiverAssessment(String inHospitalNo) {
        PkpdAssessmentView.LiverAssessment liver = new PkpdAssessmentView.LiverAssessment();
        try {
            List<Map<String, Object>> labs = icuPatientMapper.selectRecentLabs(inHospitalNo);
            for (Map<String, Object> lab : labs) {
                String name = str(lab.get("item_name"));
                String shortName = str(lab.get("short_name"));
                String result = str(lab.get("result"));
                if (!isNumeric(result)) continue;
                BigDecimal val = new BigDecimal(result);

                if (isTotalBilirubin(name, shortName) && liver.getTotalBilirubin() == null) {
                    liver.setTotalBilirubin(val);
                } else if (isDirectBilirubin(name, shortName) && liver.getDirectBilirubin() == null) {
                    liver.setDirectBilirubin(val);
                } else if (isAlbumin(name, shortName) && liver.getAlbumin() == null) {
                    liver.setAlbumin(val);
                } else if (isInr(name, shortName) && liver.getInr() == null) {
                    liver.setInr(val);
                }
            }
        } catch (Exception e) {
            log.warn("查询肝功能指标失败: {}", e.getMessage());
        }

        // 肝功能异常判断（简化：总胆红素>34.2μmol/L 或 白蛋白<30g/L）
        boolean abnormal = false;
        List<String> reasons = new ArrayList<>();
        if (liver.getTotalBilirubin() != null && liver.getTotalBilirubin().doubleValue() > 34.2) {
            abnormal = true;
            reasons.add("总胆红素升高");
        }
        if (liver.getAlbumin() != null && liver.getAlbumin().doubleValue() < 30) {
            abnormal = true;
            reasons.add("低白蛋白血症");
        }
        liver.setAbnormal(abnormal);
        liver.setAbnormalText(abnormal ? String.join("、", reasons) : "肝功能基本正常");

        return liver;
    }

    // ==================================================================
    // 4. 特殊状态
    // ==================================================================

    private PkpdAssessmentView.SpecialStatus buildSpecialStatus(IcuPatientBrief patient,
                                                                   IcuPatientAssessment assessment,
                                                                   String inHospitalNo) {
        PkpdAssessmentView.SpecialStatus special = new PkpdAssessmentView.SpecialStatus();
        List<String> flags = new ArrayList<>();

        // 低蛋白血症（白蛋白<25g/L）
        BigDecimal albumin = null;
        if (assessment != null && assessment.getCreatinine() != null) {
            // 从 assessment 拿不到白蛋白，从检验数据拿
        }
        try {
            List<Map<String, Object>> labs = icuPatientMapper.selectRecentLabs(inHospitalNo);
            for (Map<String, Object> lab : labs) {
                String name = str(lab.get("item_name"));
                String shortName = str(lab.get("short_name"));
                String result = str(lab.get("result"));
                if (isAlbumin(name, shortName) && isNumeric(result)) {
                    albumin = new BigDecimal(result);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("查询白蛋白失败: {}", e.getMessage());
        }
        special.setAlbumin(albumin);
        if (albumin != null && albumin.doubleValue() < 25) {
            special.setHypoalbuminemia(true);
            flags.add("低蛋白血症");
        }

        // CRRT
        try {
            Map<String, Object> crrt = icuPatientMapper.selectCrrtRecord(patient.getPatientId());
            if (crrt != null && !crrt.isEmpty()) {
                boolean isEnd = "1".equals(str(crrt.get("is_end")));
                if (!isEnd) {
                    special.setCrrt(true);
                    special.setCrrtStartTime(formatTimeShort(str(crrt.get("start_time"))));
                    special.setCrrtPlan(str(crrt.get("crrt_plan")));
                    special.setCrrtBloodFlow(str(crrt.get("blood_rate")));
                    special.setCrrtExchangeRate(str(crrt.get("exchange_rate")));
                    special.setCrrtDialysisRate(str(crrt.get("dialysis_rate")));
                    flags.add("CRRT");
                }
            }
        } catch (Exception e) {
            log.warn("查询 CRRT 记录失败: {}", e.getMessage());
        }

        // ECMO
        try {
            Map<String, Object> ecmo = icuPatientMapper.selectEcmoRecord(patient.getPatientId());
            if (ecmo != null && !ecmo.isEmpty()) {
                boolean isEnd = "1".equals(str(ecmo.get("is_end")));
                if (!isEnd) {
                    special.setEcmo(true);
                    special.setEcmoStartTime(formatTimeShort(str(ecmo.get("start_time"))));
                    special.setEcmoMode(str(ecmo.get("auxiliary_mode")));
                    flags.add("ECMO");
                }
            }
        } catch (Exception e) {
            log.warn("查询 ECMO 记录失败: {}", e.getMessage());
        }

        special.setFlags(flags);
        return special;
    }

    // ==================================================================
    // 5. 药物 PK/PD 分析
    // ==================================================================

    private List<PkpdDrugAnalysis> buildDrugAnalysis(List<AbxCurrentItem> currentAbx) {
        List<PkpdDrugAnalysis> list = new ArrayList<>();
        if (currentAbx == null || currentAbx.isEmpty()) return list;

        for (AbxCurrentItem item : currentAbx) {
            PkpdDrugAnalysis analysis = new PkpdDrugAnalysis();
            analysis.setDrugName(item.getName());
            analysis.setCurrentDose(item.getName()); // 药名中含剂量
            analysis.setCurrentInterval(item.getFreq());
            analysis.setInfusionMethod(item.getMethod());
            analysis.setStartTime(item.getStartTime());

            // 匹配知识库
            AbxDrugKnowledge knowledge = AbxDrugKnowledge.match(item.getName());
            if (knowledge != null) {
                analysis.setKnowledgeMatched(true);
                analysis.setPkpdType(knowledge.getPkpdType());
                analysis.setPkpdTypeText(knowledge.getPkpdTypeText());
                analysis.setTargetParam(knowledge.getTargetParam());
                analysis.setTargetValue(knowledge.getTargetValue());
                analysis.setProteinBinding(knowledge.getProteinBinding());
                analysis.setHighProteinBinding(knowledge.isHighProteinBinding());
                analysis.setClearanceRoute(knowledge.getClearanceRoute());
                analysis.setClearanceRouteText(knowledge.getClearanceRouteText());
                analysis.setTdmRequired(knowledge.isTdmRequired());
                analysis.setRemark(knowledge.getRemark());
            } else {
                analysis.setKnowledgeMatched(false);
                analysis.setPkpdType("unknown");
                analysis.setPkpdTypeText("未匹配知识库");
            }
            list.add(analysis);
        }
        return list;
    }

    // ==================================================================
    // 6. 剂量优化建议
    // ==================================================================

    private List<DoseRecommendation> buildDoseRecommendations(List<AbxCurrentItem> currentAbx,
                                                                 RenalAssessment renal,
                                                                 PkpdAssessmentView.NutritionAssessment nutrition,
                                                                 PkpdAssessmentView.SpecialStatus specialStatus) {
        List<DoseRecommendation> list = new ArrayList<>();
        if (currentAbx == null || currentAbx.isEmpty()) return list;

        for (AbxCurrentItem item : currentAbx) {
            AbxDrugKnowledge knowledge = AbxDrugKnowledge.match(item.getName());
            if (knowledge == null) continue; // 未匹配知识库的不生成建议

            DoseRecommendation rec = new DoseRecommendation();
            rec.setDrugName(knowledge.getDrugName());
            rec.setRecommendedDose(knowledge.getUsualDose());
            rec.setRecommendedInterval(extractFreq(item.getFreq()));
            rec.setRecommendedInfusion(item.getMethod());
            rec.setEvidenceSource("药品说明书 + PK/PD 专家共识");

            List<String> reasons = new ArrayList<>();
            boolean needAdjust = false;

            // 肾功能调整
            if ("renal".equals(knowledge.getClearanceRoute()) && renal.getCrcl() != null) {
                double crcl = renal.getCrcl().doubleValue();
                if (crcl < 50) {
                    reasons.add("肌酐清除率 " + crcl + " mL/min，" + knowledge.getRenalAdjustment());
                    needAdjust = true;
                    rec.setAdjustmentType("renal");
                }
            }

            // 肥胖患者体重调整
            if (nutrition != null && nutrition.isObese() && isWeightBasedDose(knowledge)) {
                reasons.add("肥胖患者（BMI " + nutrition.getBmi() + "），建议按调整体重 "
                        + nutrition.getAdjBw() + "kg 计算剂量");
                needAdjust = true;
                if (rec.getAdjustmentType() == null) rec.setAdjustmentType("weight");
            }

            // 低蛋白血症
            if (specialStatus != null && specialStatus.isHypoalbuminemia() && knowledge.isHighProteinBinding()) {
                reasons.add("低蛋白血症（白蛋白 " + specialStatus.getAlbumin() + "g/L），"
                        + knowledge.getDrugName() + " 蛋白结合率 " + knowledge.getProteinBinding()
                        + "%，游离药物浓度可能升高，需警惕毒性");
                needAdjust = true;
                if (rec.getAdjustmentType() == null) rec.setAdjustmentType("hypoalbuminemia");
            }

            // 负荷剂量建议（浓度依赖性药物）
            if ("CONCENTRATION_DEPENDENT".equals(knowledge.getPkpdType()) && !isLoadingDoseInOrder(item)) {
                reasons.add("浓度依赖性药物，建议首剂负荷剂量以快速达到有效浓度");
                needAdjust = true;
                if (rec.getAdjustmentType() == null) rec.setAdjustmentType("loading");
            }

            // 延长输注建议（β-内酰胺类，时间依赖性）
            if ("TIME_DEPENDENT".equals(knowledge.getPkpdType()) && !isExtendedInfusion(item)) {
                reasons.add("时间依赖性药物，建议 3h 延长输注或持续输注以提高 %T>MIC");
                needAdjust = true;
                if (rec.getAdjustmentType() == null) rec.setAdjustmentType("extended_infusion");
            }

            // CRRT 患者
            if (specialStatus != null && specialStatus.isCrrt() && "renal".equals(knowledge.getClearanceRoute())) {
                reasons.add("CRRT 治疗中，肾脏清除药物需根据 CRRT 剂量调整给药间隔");
                needAdjust = true;
                if (rec.getAdjustmentType() == null) rec.setAdjustmentType("crrt");
            }

            if (!needAdjust) {
                reasons.add("当前患者肝肾功能及特殊状态下，常规剂量适用，无需特殊调整");
                rec.setAdjustmentType("none");
            }

            rec.setAdjustmentReason(String.join("；", reasons));
            rec.setNeedAdjustment(needAdjust);
            rec.setRenalDoseTable(buildRenalDoseTable(knowledge));
            list.add(rec);
        }
        return list;
    }

    /** 构建肾功能剂量调整表 */
    private List<DoseRecommendation.RenalDoseRow> buildRenalDoseTable(AbxDrugKnowledge knowledge) {
        List<DoseRecommendation.RenalDoseRow> table = new ArrayList<>();
        if (!"renal".equals(knowledge.getClearanceRoute())) {
            DoseRecommendation.RenalDoseRow row = new DoseRecommendation.RenalDoseRow();
            row.setStage("all");
            row.setStageText("全阶段");
            row.setCrclRange("—");
            row.setDose(knowledge.getUsualDose() + "（肾功能不全无需调整）");
            table.add(row);
            return table;
        }
        String usual = knowledge.getUsualDose();
        String[][] stages = {
                {"G1-G2", "正常/轻度下降", "CrCl≥60", usual},
                {"G3", "中度下降", "CrCl 30-59", adjustByCrcl(knowledge, 45)},
                {"G4", "重度下降", "CrCl 15-29", adjustByCrcl(knowledge, 22)},
                {"G5", "肾衰竭", "CrCl<15", adjustByCrcl(knowledge, 10)},
        };
        for (String[] s : stages) {
            DoseRecommendation.RenalDoseRow row = new DoseRecommendation.RenalDoseRow();
            row.setStage(s[0]);
            row.setStageText(s[1]);
            row.setCrclRange(s[2]);
            row.setDose(s[3]);
            table.add(row);
        }
        return table;
    }

    /** 简化的肾功能剂量调整（按比例减量，实际应以说明书为准） */
    private String adjustByCrcl(AbxDrugKnowledge knowledge, double crclRatio) {
        // 简化：CrCl 越低，间隔越长或剂量越小
        if (crclRatio >= 45) return knowledge.getUsualDose() + "（可能需延长间隔）";
        if (crclRatio >= 22) return "减量 25-50% 或延长间隔（详见说明书）";
        return "减量 50% 或 q48-72h（详见说明书）";
    }

    private boolean isWeightBasedDose(AbxDrugKnowledge k) {
        String name = k.getDrugName();
        return name.contains("万古霉素") || name.contains("氨基糖苷") || name.contains("阿米卡星")
                || name.contains("庆大霉素") || name.contains("多粘菌素") || name.contains("达托霉素");
    }

    private boolean isLoadingDoseInOrder(AbxCurrentItem item) {
        // 简化判断：医嘱名中含"负荷"或首剂标记
        return item.getName() != null && item.getName().contains("负荷");
    }

    private boolean isExtendedInfusion(AbxCurrentItem item) {
        String method = item.getMethod();
        return method != null && (method.contains("延长") || method.contains("持续") || method.contains("3h"));
    }

    private String extractFreq(String freq) {
        return freq != null ? freq : "q12h";
    }

    // ==================================================================
    // 7. TDM 目标值
    // ==================================================================

    private List<TdmTarget> buildTdmTargets(List<AbxCurrentItem> currentAbx) {
        List<TdmTarget> list = new ArrayList<>();
        if (currentAbx == null || currentAbx.isEmpty()) return list;

        Set<String> added = new HashSet<>();
        for (AbxCurrentItem item : currentAbx) {
            AbxDrugKnowledge k = AbxDrugKnowledge.match(item.getName());
            if (k == null || !k.isTdmRequired()) continue;
            if (added.contains(k.getDrugName())) continue;
            added.add(k.getDrugName());

            TdmTarget target = new TdmTarget();
            target.setDrugName(k.getDrugName());
            target.setMonitorParam(k.getTargetParam());
            target.setTargetRange(k.getTargetValue());
            target.setMonitorTiming("第4剂前30min");
            target.setRemark(k.getRemark());

            // 具体药物的详细目标值
            String name = k.getDrugName();
            if (name.contains("万古霉素") || name.contains("去甲万古霉素")) {
                target.setSevereTarget("谷浓度 15-20 mg/L，AUC/MIC ≥ 400");
                target.setGeneralTarget("谷浓度 10-15 mg/L");
                target.setToxicityThreshold("谷浓度 >20 mg/L 肾毒性增加");
                target.setSamplingNote("第4剂给药前30min采样");
            } else if (name.contains("替考拉宁")) {
                target.setSevereTarget("谷浓度 15-30 mg/L（骨/关节感染）");
                target.setGeneralTarget("谷浓度 10-15 mg/L");
                target.setSamplingNote("第3剂给药前采样");
            } else if (name.contains("阿米卡星")) {
                target.setMonitorParam("峰浓度/谷浓度");
                target.setSevereTarget("峰浓度 20-30 mg/L，谷浓度 <4 mg/L");
                target.setGeneralTarget("峰浓度 ≥8×MIC，谷浓度 <4 mg/L");
                target.setToxicityThreshold("谷浓度 >4 mg/L 耳毒性/肾毒性增加");
                target.setSamplingNote("给药后30min测峰，下次给药前测谷");
            } else if (name.contains("庆大霉素") || name.contains("妥布霉素")) {
                target.setMonitorParam("峰浓度/谷浓度");
                target.setSevereTarget("峰浓度 8-12 mg/L，谷浓度 <1 mg/L");
                target.setToxicityThreshold("谷浓度 >1 mg/L 毒性增加");
                target.setSamplingNote("给药后30min测峰，下次给药前测谷");
            } else if (name.contains("伏立康唑")) {
                target.setMonitorParam("谷浓度");
                target.setTargetRange("1-5 mg/L");
                target.setToxicityThreshold(">5 mg/L 肝毒性/神经毒性增加");
                target.setSamplingNote("第4剂给药前30min采样");
            } else if (name.contains("利奈唑胺")) {
                target.setMonitorParam("谷浓度");
                target.setTargetRange("2-7 mg/L");
                target.setToxicityThreshold(">7 mg/L 血小板减少风险增加");
                target.setSamplingNote("第4剂给药前30min采样");
            } else if (name.contains("氟康唑")) {
                target.setMonitorParam("谷浓度");
                target.setTargetRange("≥目标MIC的2倍");
                target.setSevereTarget("隐球菌感染 ≥10 mg/L");
                target.setSamplingNote("第4剂给药前采样");
            }
            list.add(target);
        }
        return list;
    }

    // ==================================================================
    // 8. 药物相互作用提醒（简化版）
    // ==================================================================

    private List<String> buildInteractionAlerts(List<AbxCurrentItem> currentAbx, RenalAssessment renal) {
        List<String> alerts = new ArrayList<>();
        if (currentAbx == null || currentAbx.isEmpty()) return alerts;

        boolean hasAminoglycoside = false;
        boolean hasVancomycin = false;
        boolean hasNephrotoxic = false;

        for (AbxCurrentItem item : currentAbx) {
            String name = item.getName();
            if (name == null) continue;
            if (name.contains("阿米卡星") || name.contains("庆大霉素") || name.contains("妥布霉素")) {
                hasAminoglycoside = true;
                hasNephrotoxic = true;
            }
            if (name.contains("万古霉素") || name.contains("去甲万古霉素")) {
                hasVancomycin = true;
                hasNephrotoxic = true;
            }
            if (name.contains("多粘菌素") || name.contains("两性霉素")) {
                hasNephrotoxic = true;
            }
        }

        if (hasAminoglycoside && hasVancomycin) {
            alerts.add("⚠️ 氨基糖苷类 + 万古霉素联用：肾毒性叠加风险，建议密切监测肾功能和血药浓度");
        }
        if (hasNephrotoxic && renal.getCrcl() != null && renal.getCrcl().doubleValue() < 60) {
            alerts.add("⚠️ 患者肾功能不全（CrCl " + renal.getCrcl() + " mL/min），使用肾毒性药物需调整剂量并监测肾功能");
        }

        return alerts;
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    private boolean isCreatinine(String name, String shortName) {
        if (name == null && shortName == null) return false;
        String n = (name != null ? name : "") + (shortName != null ? shortName : "");
        return (n.contains("肌酐") || n.contains("Cr") || n.contains("CRE"))
                && !n.contains("尿素") && !n.contains("尿酸");
    }

    private boolean isTotalBilirubin(String name, String shortName) {
        if (name == null && shortName == null) return false;
        String n = (name != null ? name : "") + (shortName != null ? shortName : "");
        return (n.contains("总胆红素") || n.contains("TBIL") || n.contains("T-BIL"))
                && !n.contains("直接") && !n.contains("间接");
    }

    private boolean isDirectBilirubin(String name, String shortName) {
        if (name == null && shortName == null) return false;
        String n = (name != null ? name : "") + (shortName != null ? shortName : "");
        return n.contains("直接胆红素") || n.contains("DBIL") || n.contains("D-BIL");
    }

    private boolean isAlbumin(String name, String shortName) {
        if (name == null && shortName == null) return false;
        String n = (name != null ? name : "") + (shortName != null ? shortName : "");
        return (n.contains("白蛋白") || n.contains("ALB")) && n.startsWith("*") || n.contains("*白蛋白") || n.contains("白蛋白");
    }

    private boolean isInr(String name, String shortName) {
        if (name == null && shortName == null) return false;
        String n = (name != null ? name : "") + (shortName != null ? shortName : "");
        return n.contains("INR") || n.contains("国际标准化比值") || n.contains("国际标准化比率");
    }

    private boolean isNumeric(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            new BigDecimal(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String str(Object o) {
        if (o == null) return "";
        // 达梦 CLOB/NClob 类型必须主动读取内容，否则 toString() 只返回对象地址
        // （如 dm.jdbc.driver.DmdbNClob@6300cfa5），NClob 继承自 Clob，统一处理
        if (o instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) o;
                long len = clob.length();
                if (len <= 0) return "";
                return clob.getSubString(1, (int) Math.min(len, 65535)).trim();
            } catch (Exception e) {
                return "";
            }
        }
        return String.valueOf(o).trim();
    }

    private String formatTimeShort(String time) {
        if (time == null || time.isEmpty()) return "";
        // "2026-09-04 22:17:08" → "09-04 22:17"
        if (time.length() >= 16) {
            return time.substring(5, 16);
        }
        return time;
    }
}
