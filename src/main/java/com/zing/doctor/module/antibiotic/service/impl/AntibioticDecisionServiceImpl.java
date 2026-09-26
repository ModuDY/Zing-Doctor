package com.zing.doctor.module.antibiotic.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.dto.AdviceItem;
import com.zing.doctor.module.antibiotic.dto.PatientAssessmentView;
import com.zing.doctor.module.antibiotic.entity.AdviceLog;
import com.zing.doctor.module.antibiotic.entity.DecisionRecord;
import com.zing.doctor.module.antibiotic.mapper.AdviceLogMapper;
import com.zing.doctor.module.antibiotic.mapper.DecisionRecordMapper;
import com.zing.doctor.module.antibiotic.service.AntibioticDecisionService;
import com.zing.doctor.module.antibiotic.service.AntibioticReassessmentService;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 第一维度：经验性抗感染治疗决策实现。
 *
 * <p>方案推荐为可解释规则引擎（P0），覆盖「脓毒性休克 / 无休克」分层与 MRSA、真菌风险加量，
 * 依据 SSC 2021 与国内指南/共识。规则后续迁移到数据库规则表，支持医院本地化配置。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AntibioticDecisionServiceImpl implements AntibioticDecisionService {

    private final IcuPatientService icuPatientService;
    private final DecisionRecordMapper decisionRecordMapper;
    private final AdviceLogMapper adviceLogMapper;
    private final SysParamService sysParamService;
    private final AntibioticReassessmentService reassessmentService;

    @Override
    public List<IcuPatientBrief> listSuspectPatients(String departCode) {
        List<IcuPatientBrief> patients = icuPatientService.listSuspectInfections(departCode);
        enrichDecisionStatus(patients);
        markPending(patients);
        return patients;
    }

    /**
     * 标记"待决策"患者（列表顶部计数与筛选都看这个字段）。
     *
     * <p>口径由参数 {@code ABX_PENDING_DECISION_RULE} 决定，因为两种口径对应两种排班习惯：
     * <ul>
     *   <li>{@code TODAY_NO_DECISION}（默认）：当天没有决策记录就算待决策，含从未决策的。
     *       适合每天晨间把全科过一遍的科室。</li>
     *   <li>{@code ADMIT_24H_NEVER}：入科满 24 小时且从没做过抗感染决策才算。
     *       适合"新入科先观察、在科一天以上必须评估"的科室——
     *       否则凌晨入科的患者一进列表就挂在待决策里，夜间没人看只会徒增噪音。</li>
     * </ul>
     *
     * <p>放在服务端算而不是前端：口径要全院统一，前端各算各的会出现
     * 「同一个列表，两个医生看到的人数不一样」。
     */
    private void markPending(List<IcuPatientBrief> patients) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        String rule = pendingRule();
        for (IcuPatientBrief p : patients) {
            p.setPendingDecision(isPending(p, rule));
        }
    }

    private String pendingRule() {
        String raw = sysParamService.value(SysParamService.KEY_ABX_PENDING_RULE);
        if (!StringUtils.hasText(raw)) {
            return SysParamService.ABX_PENDING_TODAY;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPending(IcuPatientBrief p, String rule) {
        if (SysParamService.ABX_PENDING_ADMIT_24H.equals(rule)) {
            if (p.getDecisionStatus() != null) {
                return false;
            }
            // 入科时间拿不到时不计入：没有依据判断他是不是已经住了一天以上，
            // 宁可漏提醒，也不要把整科患者全挂成待决策
            return p.getInDepartTime() != null
                    && p.getInDepartTime().isBefore(LocalDateTime.now().minusHours(24));
        }
        // 默认口径：当天没有决策记录即待决策（含从未决策）
        LocalDateTime t = p.getDecisionTime();
        return t == null || !t.toLocalDate().equals(LocalDate.now());
    }

    /**
     * 回填最近一次抗感染决策状态（一次查完，不逐患者查）。
     *
     * <p>列表上的"今日待决策"与决策状态列都靠它：医生扫列表时要能一眼看出
     * 哪些患者今天还没被评估过，否则只能逐个点进去才知道。
     *
     * <p>查询失败只记日志不抛出：这是本系统库的附属信息，取不到时列表仍应显示患者，
     * 只是决策状态为空——不能因为决策记录表出状况就让整个感染列表打不开。
     */
    private void enrichDecisionStatus(List<IcuPatientBrief> patients) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        List<String> patientIds = new ArrayList<>();
        for (IcuPatientBrief p : patients) {
            if (StrUtil.isNotBlank(p.getPatientId())) {
                patientIds.add(p.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return;
        }
        List<DecisionRecord> records;
        try {
            records = decisionRecordMapper.selectList(new LambdaQueryWrapper<DecisionRecord>()
                    .in(DecisionRecord::getPatientId, patientIds)
                    .orderByDesc(DecisionRecord::getCreateTime));
        } catch (Exception e) {
            log.warn("批量查询抗感染决策状态失败，列表决策状态留空", e);
            return;
        }
        if (records == null || records.isEmpty()) {
            return;
        }
        // 已按时间倒序：每个患者取遇到的第一条即其最近一次决策
        Map<String, DecisionRecord> latest = new HashMap<>();
        for (DecisionRecord r : records) {
            if (r == null || StrUtil.isBlank(r.getPatientId())) {
                continue;
            }
            latest.putIfAbsent(r.getPatientId(), r);
        }
        for (IcuPatientBrief p : patients) {
            DecisionRecord r = latest.get(p.getPatientId());
            if (r == null) {
                continue;
            }
            p.setDecisionStatus(r.getDecisionStatus());
            p.setDecisionTime(r.getCreateTime());
            p.setDecisionDoctor(r.getDoctorName());
        }
    }

    @Override
    public PatientAssessmentView getAssessmentView(String patientId) {
        IcuPatientAssessment assessment = icuPatientService.getAssessment(patientId);
        // 患者信息直接取自评估结果（含基础信息 + 检验/风险增强），不依赖“疑似感染列表”过滤，
        // 使 ICU 外链可打开任意在科患者的决策页。
        IcuPatientBrief patient = assessment.getPatient();

        List<AdviceItem> advice = generateAdvice(patient);
        PatientAssessmentView view = new PatientAssessmentView();
        view.setPatient(patient);
        view.setAssessment(assessment);
        view.setAdviceList(advice);
        view.setPlanSummary(advice.stream()
                .map(AdviceItem::getDrugName)
                .distinct()
                .collect(Collectors.joining(" + ")));
        return view;
    }

    @Override
    public List<AdviceItem> generateAdvice(IcuPatientBrief p) {
        List<AdviceItem> list = new ArrayList<>();
        boolean shock = Boolean.TRUE.equals(p.getSepticShock());
        boolean mrsa = Boolean.TRUE.equals(p.getMrsaRisk());
        boolean mdr = Boolean.TRUE.equals(p.getMdrRisk());
        boolean fungal = Boolean.TRUE.equals(p.getFungalRisk());

        if (shock) {
            // 脓毒性休克：1 小时内广谱经验性覆盖
            String gramNegative = mdr
                    ? advice(list, "美罗培南", "1g q8h（延长输注 3h）", "IV", "强",
                    "MDR 高风险脓毒性休克，覆盖 ESBL/耐药革兰阴性菌",
                    "SSC 2021：脓毒性休克 1h 内给予广谱抗菌药物；MDR 高风险可选用碳青霉烯类")
                    : advice(list, "哌拉西林/他唑巴坦", "4.5g q6h 静脉输注", "IV", "强",
                    "脓毒性休克一线广谱覆盖（抗铜绿假单胞菌）",
                    "SSC 2021：脓毒性休克 1h 内给予广谱抗菌药物");
            if (mrsa) {
                advice(list, "万古霉素", "负荷 25-30mg/kg 后按肾功能调整（AUC 400-600 目标）", "IV", "强",
                        "MRSA 高风险，需经验性覆盖",
                        "中国万古霉素 TDM 指南 2020 更新版：AUC/MIC 400-600");
            }
            if (fungal) {
                advice(list, "棘白菌素类（如卡泊芬净）", "负荷 70mg 后 50mg qd", "IV", "弱",
                        "真菌高风险（长期广谱/免疫抑制/留置导管），考虑覆盖念珠菌",
                        "中国念珠菌病诊断与治疗指南：危重患者经验性抗真菌治疗");
            }
        } else {
            // 无休克：按感染部位分层
            String type = p.getInfectionType() == null ? "" : p.getInfectionType();
            if (type.contains("肺炎")) {
                if (type.contains("院内") || type.contains("呼吸机") || type.contains("医院")) {
                    String base = mdr
                            ? advice(list, "美罗培南", "1g q8h", "IV", "强",
                            "HAP/VAP，MDR 高风险", "SSC 2021 / 中国 HAP-VAP 指南")
                            : advice(list, "哌拉西林/他唑巴坦", "4.5g q6h", "IV", "强",
                            "HAP/VAP 一线覆盖", "中国 HAP-VAP 指南");
                    if (mrsa) {
                        advice(list, "万古霉素", "负荷 25-30mg/kg 后按肾功能调整", "IV", "强",
                                "HAP/VAP 合并 MRSA 高风险", "IDSA 2023 HAP-VAP 指南");
                    }
                } else {
                    advice(list, "头孢曲松", "2g qd", "IV", "强",
                            "社区获得性肺炎经验性覆盖", "中国 CAP 指南：住院 CAP 一线");
                    if (mrsa) {
                        advice(list, "万古霉素", "负荷 25-30mg/kg 后按肾功能调整", "IV", "弱",
                                "CAP 合并 MRSA 高风险", "IDSA/ATS 2019 CAP 指南");
                    }
                }
            } else if (type.contains("腹腔")) {
                advice(list, "哌拉西林/他唑巴坦", "4.5g q6h", "IV", "强",
                        "腹腔感染：覆盖肠道需氧兼性/厌氧菌", "中国腹腔感染诊治指南（2024）");
                if (fungal) {
                    advice(list, "棘白菌素类（如卡泊芬净）", "负荷 70mg 后 50mg qd", "IV", "弱",
                            "重症腹腔感染伴真菌高风险，经验性覆盖念珠菌",
                            "中国念珠菌病诊断与治疗指南");
                }
            } else {
                advice(list, "哌拉西林/他唑巴坦", "4.5g q6h", "IV", "强",
                        "感染部位待明确的广谱初始覆盖", "SSC 2021：初始广谱覆盖，48-72h 依据培养降阶梯");
                if (mrsa) {
                    advice(list, "万古霉素", "负荷 25-30mg/kg 后按肾功能调整", "IV", "弱",
                            "MRSA 高风险", "中国万古霉素 TDM 指南 2020");
                }
            }
        }

        // 通用建议
        advice(list, "48-72 小时复评", "依据培养药敏与 PCT 趋势评估降阶梯/停药", "—", "强",
                "经验性治疗启动 48-72h 后必须复评",
                "SSC 2021：每日评估降阶梯；PCT 指导停药");
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDecision(String patientId, String doctorDecision, String decisionStatus,
                             String doctorId, String doctorName) {
        // 仅需患者基本信息（patientNo/感染类型/风险标记）即可生成方案与留痕；
        // 用轻量单患者查询，避免触发全院疑似感染列表扫描（大表慢查询导致保存转圈）。
        IcuPatientBrief patient = icuPatientService.getPatientBrief(patientId);
        if (patient == null) {
            throw new BizException(404, "未找到患者：" + patientId);
        }

        List<AdviceItem> advice = generateAdvice(patient);

        DecisionRecord record = new DecisionRecord();
        record.setPatientId(patientId);
        record.setPatientNo(patient.getPatientNo());
        record.setPageCode("abx-decision");
        record.setSourceSystem("icu");
        record.setInfectionType(patient.getInfectionType());
        record.setSepticShock(boolToInt(patient.getSepticShock()));
        record.setMrsaRisk(boolToInt(patient.getMrsaRisk()));
        record.setMdrRisk(boolToInt(patient.getMdrRisk()));
        record.setFungalRisk(boolToInt(patient.getFungalRisk()));
        record.setRecommendedPlan(advice.stream()
                .map(AdviceItem::getDrugName).distinct().collect(Collectors.joining(" + ")));
        record.setDoctorDecision(doctorDecision);
        record.setDecisionStatus(StrUtil.isBlank(decisionStatus) ? "pending" : decisionStatus);
        record.setDoctorId(doctorId);
        record.setDoctorName(doctorName);
        decisionRecordMapper.insert(record);

        if ("accepted".equalsIgnoreCase(record.getDecisionStatus())) {
            reassessmentService.createPending(record.getId(), patient.getPatientId(), patient.getPatientNo(),
                    patient.getInHospitalNo(), patient.getDepartCode());
        }

        for (AdviceItem item : advice) {
            if ("48-72 小时复评".equals(item.getDrugName())) {
                continue;
            }
            AdviceLog log = new AdviceLog();
            log.setDecisionRecordId(record.getId());
            log.setDrugName(item.getDrugName());
            log.setDosePlan(item.getDosePlan());
            log.setRoute(item.getRoute());
            log.setAdviceLevel(item.getAdviceLevel());
            log.setReason(item.getReason());
            log.setEvidence(item.getEvidence());
            adviceLogMapper.insert(log);
        }
        return record.getId();
    }

    /** 查询某患者的决策历史（供前端展示） */
    public List<DecisionRecord> listByPatient(String patientId) {
        return decisionRecordMapper.selectList(new LambdaQueryWrapper<DecisionRecord>()
                .eq(DecisionRecord::getPatientId, patientId)
                .orderByDesc(DecisionRecord::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateDecisionRecord(Long id, String doctorDecision, String decisionStatus,
                                     String doctorId, String doctorName) {
        DecisionRecord record = decisionRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(404, "决策记录不存在：" + id);
        }
        record.setDoctorDecision(doctorDecision);
        if (StrUtil.isNotBlank(decisionStatus)) {
            record.setDecisionStatus(decisionStatus);
        }
        record.setDoctorId(doctorId);
        record.setDoctorName(doctorName);
        record.setUpdateTime(LocalDateTime.now());
        decisionRecordMapper.updateById(record);
        IcuPatientBrief patient = icuPatientService.getPatientBrief(record.getPatientId());
        if ("accepted".equalsIgnoreCase(record.getDecisionStatus())) {
            if (patient != null) {
                reassessmentService.createPending(record.getId(), patient.getPatientId(), patient.getPatientNo(),
                        patient.getInHospitalNo(), patient.getDepartCode());
            }
        } else {
            reassessmentService.voidPendingByDecision(record.getId());
        }
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDecisionRecord(Long id) {
        DecisionRecord record = decisionRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(404, "决策记录不存在：" + id);
        }
        reassessmentService.voidPendingByDecision(id);
        decisionRecordMapper.deleteById(id);
        // 级联删除该记录的推荐明细
        adviceLogMapper.delete(new LambdaQueryWrapper<AdviceLog>()
                .eq(AdviceLog::getDecisionRecordId, id));
    }

    private String advice(List<AdviceItem> list, String drug, String dose, String route,
                          String level, String reason, String evidence) {
        AdviceItem item = new AdviceItem();
        item.setDrugName(drug);
        item.setDosePlan(dose);
        item.setRoute(route);
        item.setAdviceLevel(level);
        item.setReason(reason);
        item.setEvidence(evidence);
        list.add(item);
        return drug;
    }

    private Integer boolToInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }
}
