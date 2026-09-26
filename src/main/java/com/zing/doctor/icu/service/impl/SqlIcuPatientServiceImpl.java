package com.zing.doctor.icu.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.AbxCurrentItem;
import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.dto.LabTrend;
import com.zing.doctor.icu.dto.TrendPoint;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ICU 数据 SQL 实现（只读 zing_icu_db_prod，基于 ICU 系统真实表结构）。
 *
 * <p>表结构映射（第一维度所需）：
 * <pre>
 *   patient_info                               患者信息/过敏史/体重/脓毒性休克/多耐药标记
 *   patient_info_diagnosis                     诊断 → 感染类型推断、真菌风险
 *   patient_info_lis_item                      检验明细 → PCT/WBC/CRP/肌酐/乳酸
 *   patient_info_lis_item + patient_info_lis   微生物培养/药敏结果（结果值即菌名/药敏结论）→ pastCultures 与风险增强
 *   patient_observe_module_item_record +
 *     config_observe_item                      体温（观察项记录）
 *   patient_advice_execute                     当前抗菌药物医嘱
 * </pre>
 *
 * <p>激活：application.yml 中 {@code zing.doctor.icu-data-provider=sql}（默认 mock）。
 * 数据库为达梦 DM8，SQL 遵循达梦方言（双引号小写标识符、SYSDATE-7、ROWNUM）。
 * 既往培养信息来自 patient_info_lis_item 微生物明细 + patient_info 多耐药标记。
 */
@Slf4j
@Service
@DS("icu")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zing.doctor.icu-data-provider", havingValue = "sql")
public class SqlIcuPatientServiceImpl implements IcuPatientService {

    /** 抗菌药物关键词（医嘱名称匹配，用于识别当前抗菌用药） */
    private static final List<String> ABX_KEYWORDS = Arrays.asList(
            "哌拉西林", "头孢", "美罗培南", "亚胺培南", "厄他培南", "比阿培南",
            "万古霉素", "利奈唑胺", "替考拉宁", "达托霉素",
            "左氧氟沙星", "莫西沙星", "环丙沙星", "奈诺沙星", "阿奇霉素", "克拉霉素",
            "阿莫西林", "氨苄西林", "阿米卡星", "庆大霉素", "妥布霉素",
            "替加环素", "多黏菌素", "多粘菌素", "磷霉素", "氨曲南",
            "卡泊芬净", "米卡芬净", "阿尼芬净", "氟康唑", "伏立康唑", "泊沙康唑",
            "两性霉素", "伊曲康唑", "甲硝唑", "奥硝唑", "替硝唑",
            "舒巴坦", "他唑巴坦", "克拉维酸", "复方新诺明", "磺胺",
            "四环素", "多西环素", "米诺环素", "利福平", "青霉素");

    /** 溶媒关键词（patient_advice 同一 group 下溶媒和溶质分开存储，排除溶媒只取溶质） */
    private static final List<String> SOLVENT_KEYWORDS = Arrays.asList(
            "氯化钠", "葡萄糖", "乳酸钠林格", "灭菌注射用水", "木糖醇",
            "转化糖", "果糖", "复方氯化钠", "甘油果糖");

    private final IcuPatientMapper icuPatientMapper;

    /**
     * 抗菌药统一识别器（HIS 药品字典 + 词表白/黑名单）。
     * 纯内存判定、不产生数据库访问，因此在 {@code @DS("icu")} 的类内调用不会污染数据源上下文。
     */
    private final AbxDrugRecognizer abxDrugRecognizer;

    @Override
    public List<WorkbenchPatient> listInpatients(String departCode) {
        List<Map<String, Object>> rows = icuPatientMapper.selectInpatients(departCode);
        List<WorkbenchPatient> patients = new ArrayList<>();
        if (rows == null) return patients;
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : rows) {
            WorkbenchPatient patient = new WorkbenchPatient();
            // inHospitalNo 留原文：各业务模块的关联列是 in_hospital_no，
            // 待办聚合与跨模块跳转都靠它；patientNo 才是给页面看的脱敏值。
            patient.setInHospitalNo(str(row.get("in_hospital_no")));
            patient.setPatientId(str(row.get("patient_id")));
            // 工作台是 ICU 内网医生自己使用，姓名/住院号直接显示原文，不脱敏
            patient.setPatientNo(str(row.get("in_hospital_no")));
            patient.setName(str(row.get("name")));
            patient.setAge(parseInt(row.get("age")));
            patient.setGender(str(row.get("gender")));
            patient.setDepartCode(str(row.get("depart_code")));
            patient.setWardName(str(row.get("ward_name")));
            patient.setBedNo(str(row.get("bed_no")));
            LocalDateTime admittedAt = toLocalDateTime(row.get("in_depart_time"));
            patient.setInDepartmentTime(admittedAt);
            patient.setIcuDays(admittedAt == null ? null : Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(admittedAt.toLocalDate(), now.toLocalDate()) + 1));
            patients.add(patient);
        }
        return patients;
    }

    /**
     * 危重标签批量回填：机械通气 / 血管活性药 / CRRT。
     *
     * <p>复用交班览表已有的批量 SQL（selectHandoverVasopressor / selectHandoverCrrtPatients /
     * selectLatestVentilatorParams），不新增 SQL、不逐患者查。任何一类查询失败都不拖垮列表，
     * 仅打 warn 日志并保留默认 false。
     */
    @Override
    public void enrichCrisisFlags(List<WorkbenchPatient> patients, String departCode) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        String dept = (departCode == null || departCode.isEmpty()) ? "" : departCode;

        Set<String> vasoPatients = new HashSet<>();
        try {
            List<Map<String, Object>> vaso = icuPatientMapper.selectHandoverVasopressor(dept);
            if (vaso != null) {
                for (Map<String, Object> r : vaso) {
                    String pid = str(r.get("patient_id"));
                    if (!pid.isEmpty()) vasoPatients.add(pid);
                }
            }
        } catch (Exception e) {
            log.warn("工作台批量查询血管活性药失败", e);
        }

        Set<String> crrtPatients = new HashSet<>();
        try {
            List<String> crrt = icuPatientMapper.selectHandoverCrrtPatients(dept);
            if (crrt != null) crrtPatients.addAll(crrt);
        } catch (Exception e) {
            log.warn("工作台批量查询 CRRT 失败", e);
        }

        Set<String> ventPatients = new HashSet<>();
        try {
            List<String> pids = new ArrayList<>();
            for (WorkbenchPatient p : patients) {
                if (p.getPatientId() != null && !p.getPatientId().isEmpty()) pids.add(p.getPatientId());
            }
            if (!pids.isEmpty()) {
                List<Map<String, Object>> vent = icuPatientMapper.selectLatestVentilatorParams(pids);
                if (vent != null) {
                    for (Map<String, Object> r : vent) {
                        String pid = str(r.get("patient_id"));
                        if (!pid.isEmpty()) ventPatients.add(pid);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("工作台批量查询呼吸机参数失败", e);
        }

        for (WorkbenchPatient p : patients) {
            String pid = p.getPatientId();
            p.setOnVasopressor(vasoPatients.contains(pid));
            p.setOnCrrt(crrtPatients.contains(pid));
            p.setVentilated(ventPatients.contains(pid));
        }
    }

    private String maskName(String name) {
        if (StrUtil.isBlank(name)) return "未知";
        if (name.contains("*")) return name;
        if (name.length() == 1) return "*";
        StringBuilder masked = new StringBuilder().append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) masked.append("*");
        return masked.toString();
    }

    private String maskPatientNo(String patientNo) {
        if (StrUtil.isBlank(patientNo)) return "—";
        if (patientNo.length() <= 4) return "****";
        return patientNo.substring(0, 3) + "***" + patientNo.substring(patientNo.length() - 2);
    }
    /**
     * 疑似感染患者列表。
     *
     * <p><b>查询次数固定为 6 次，与患者数无关</b>：患者主表 1 次 + 诊断 1 次 + 检验 1 次 +
     * 体温 1 次 + 微生物 1 次 + 抗菌药 1 次。原先每个患者单独查诊断/检验/体温/微生物/抗菌药，
     * 50 个患者就是 250+ 次 ICU 库查询，这是列表要配 60 秒超时的真实原因。
     *
     * <p><b>入列的最后一道闸在这里</b>：SQL 只能判断"有没有 PCT 结果"，判不了数值。
     * 对<b>仅靠 PCT 命中</b>的患者，这里补一次 {@code PCT >= 0.5} 判断；
     * 靠休克标记或感染诊断命中的患者不参与这道过滤——他们的入列依据本身就是感染证据。
     *
     * @param departCode 科室编码（{@code sys_depart.org_code}）；null / 空 / "ALL" 不限科室。
     *                   科室授权由调用方（Controller）校验，这里只负责按值过滤。
     */
    @Override
    public List<IcuPatientBrief> listSuspectInfections(String departCode) {
        List<Map<String, Object>> rows = icuPatientMapper.selectSuspectPatients(departCode);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) 患者主表 → DTO，并记下每个患者是靠哪条规则入列的（[休克标记, 感染诊断, PCT 结果]）
        List<IcuPatientBrief> briefs = new ArrayList<>(rows.size());
        Map<String, boolean[]> hitFlags = new HashMap<>();
        List<String> patientIds = new ArrayList<>();
        List<String> inHospitalNos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                IcuPatientBrief brief = buildBrief(row);
                if (StrUtil.isBlank(brief.getPatientId())) {
                    continue;
                }
                briefs.add(brief);
                hitFlags.put(brief.getPatientId(), new boolean[]{
                        intToBool(row.get("shock_flag")) || intToBool(row.get("septic_shock")),
                        intToBool(row.get("diag_flag")),
                        intToBool(row.get("pct_flag"))});
                patientIds.add(brief.getPatientId());
                if (StrUtil.isNotBlank(brief.getPatientNo())) {
                    inHospitalNos.add(brief.getPatientNo());
                }
            } catch (Exception e) {
                log.warn("解析疑似感染患者数据失败, patientId={}", row.get("patient_id"), e);
            }
        }
        if (briefs.isEmpty()) {
            return Collections.emptyList();
        }

        // 2) 批量补齐：每类数据一次查询，Java 端按 patientId / 住院号分组
        Map<String, List<Map<String, Object>>> diagMap =
                groupRows(icuPatientMapper.selectDiagnosesByPatientIds(patientIds), "patient_id");
        Map<String, List<Map<String, Object>>> labMap = inHospitalNos.isEmpty()
                ? Collections.emptyMap()
                : groupRows(icuPatientMapper.selectInfectionLabsByNos(inHospitalNos), "in_hospital_no");
        Map<String, String> tempMap =
                toValueMap(icuPatientMapper.selectLatestTemperatureByPatientIds(patientIds),
                        "patient_id", "item_value");
        Map<String, List<Map<String, Object>>> microMap = inHospitalNos.isEmpty()
                ? Collections.emptyMap()
                : groupRows(icuPatientMapper.selectMicrobiologyByNos(inHospitalNos), "in_hospital_no");
        Map<String, List<Map<String, Object>>> abxMap = inHospitalNos.isEmpty()
                ? Collections.emptyMap()
                : groupRows(icuPatientMapper.selectCurrentAbxAdviceByNos(inHospitalNos), "in_hospital_no");

        // 3) 组装 + 过滤
        List<IcuPatientBrief> result = new ArrayList<>(briefs.size());
        for (IcuPatientBrief p : briefs) {
            boolean[] flags = hitFlags.get(p.getPatientId());
            boolean byShock = flags != null && flags[0];
            boolean byDiagnosis = flags != null && flags[1];
            List<Map<String, Object>> diagnoses =
                    diagMap.getOrDefault(p.getPatientId(), Collections.emptyList());

            // 感染类型与休克类型共用同一次诊断查询（原先各查一次，还各带一次兜底查询）
            p.setInfectionType(inferInfectionType(diagnoses, byShock));
            applyShockType(p, diagnoses, byShock);
            fillLabs(p, labMap.getOrDefault(p.getPatientNo(), Collections.emptyList()));
            p.setTemperature(parseDecimalValue(tempMap.get(p.getPatientId())));
            fillAbxStartTime(p, abxMap.getOrDefault(p.getPatientNo(), Collections.emptyList()));
            enhanceRiskFromCultures(p, microMap.getOrDefault(p.getPatientNo(), Collections.emptyList()));

            // 仅靠"有 PCT 结果"入列的：数值必须达到 0.5 ng/mL 才算疑似感染
            if (!byShock && !byDiagnosis && !geThreshold(p.getPct(), PCT_SUSPECT_THRESHOLD)) {
                continue;
            }
            p.setRiskLevel(evaluateRisk(p));
            result.add(p);
        }
        return result;
    }

    /** 疑似感染的 PCT 阈值（ng/mL）：与入列注释口径一致，低于此值不算感染证据 */
    private static final BigDecimal PCT_SUSPECT_THRESHOLD = new BigDecimal("0.5");

    /** PCT 高风险阈值（ng/mL） */
    private static final BigDecimal PCT_HIGH_THRESHOLD = new BigDecimal("2");

    /** 按指定列把查询结果分组（批量查询 → 按患者取用） */
    private Map<String, List<Map<String, Object>>> groupRows(List<Map<String, Object>> rows, String keyColumn) {
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            if (r == null) {
                continue;
            }
            String key = str(r.get(keyColumn));
            if (StrUtil.isBlank(key)) {
                continue;
            }
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    /** 两列结果转 Map（键列 → 值列），用于"每患者一条"的批量结果（如最新体温） */
    private Map<String, String> toValueMap(List<Map<String, Object>> rows, String keyColumn, String valueColumn) {
        Map<String, String> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            if (r == null) {
                continue;
            }
            String key = str(r.get(keyColumn));
            if (StrUtil.isBlank(key)) {
                continue;
            }
            out.put(key, str(r.get(valueColumn)));
        }
        return out;
    }

    /**
     * 从批量检验结果填 PCT / WBC（结果按时间倒序，每个指标取最新一条）。
     *
     * <p>数值解析用 {@link #parseDecimalValue}：检验结果常带单位（"0.85 ng/mL"）
     * 或比较符（"&lt;0.05"），直接 {@code new BigDecimal} 会整条失败、指标变 null。
     */
    private void fillLabs(IcuPatientBrief p, List<Map<String, Object>> rows) {
        Set<String> filled = new HashSet<>();
        for (Map<String, Object> r : rows) {
            String itemName = str(r.get("item_name"));
            String result = str(r.get("result"));
            if (StrUtil.isBlank(itemName) || StrUtil.isBlank(result)) {
                continue;
            }
            String key = matchLabKey(itemName);
            if (key == null || !filled.add(key)) {
                continue;
            }
            BigDecimal value = parseDecimalValue(result);
            if ("PCT".equals(key)) {
                p.setPct(value);
            } else if ("WBC".equals(key)) {
                p.setWbc(value);
            }
        }
    }

    /** 抗菌药开始时间（取当前在用的最早一条，用于判断经验性用药窗口） */
    private void fillAbxStartTime(IcuPatientBrief p, List<Map<String, Object>> rows) {
        LocalDateTime earliest = null;
        for (Map<String, Object> r : rows) {
            String name = str(r.get("name"));
            if (!matchesAbx(name) || isSolvent(name)) {
                continue;
            }
            LocalDateTime t = toLocalDateTime(r.get("start_time"));
            if (t != null && (earliest == null || t.isBefore(earliest))) {
                earliest = t;
            }
        }
        p.setAbxStartTime(earliest);
    }

    /** value 非空且 ≥ threshold */
    private boolean geThreshold(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) >= 0;
    }

    @Override
    public IcuPatientAssessment getAssessment(String patientId) {
        Map<String, Object> basic = icuPatientMapper.selectPatientById(patientId);
        if (basic == null || basic.isEmpty()) {
            throw new BizException(404, "未找到患者：" + patientId);
        }
        IcuPatientBrief patient = buildBrief(basic);
        // 诊断一次取回，感染类型与休克类型共用；休克标记兜底也用主表字段，不再为此回查患者表
        List<Map<String, Object>> diagnoses = icuPatientMapper.selectDiagnoses(patientId);
        boolean shockFlag = intToBool(basic.get("septic_shock"));
        patient.setInfectionType(inferInfectionType(diagnoses, shockFlag));
        applyShockType(patient, diagnoses, shockFlag);
        enrichLabsAndRisk(patient);

        IcuPatientAssessment assessment = new IcuPatientAssessment();
        assessment.setPatient(patient);
        assessment.setAllergies(parseAllergies(str(basic.get("allergy_content"))));
        assessment.setWeight(str(basic.get("weight")));

        Map<String, String> labs = loadLabs(str(basic.get("patient_no")));
        assessment.setLabs(labs);
        assessment.setLabTrends(loadLabTrends(str(basic.get("patient_no"))));
        assessment.setCreatinine(labs.get("肌酐"));

        assessment.setPastCultures(buildPastCultures(str(basic.get("patient_no")),
                str(basic.get("multidrug_resistant_bacteria")),
                str(basic.get("resistant_bacteria"))));
        assessment.setCurrentAntibiotics(loadCurrentAntibiotics(str(basic.get("patient_no"))));
        assessment.setRemark("数据来源：ICU 信息系统（zing_icu_db_prod）只读查询。"
                + "微生物培养/药敏来自 patient_info_lis_item（明细结果值即菌名/药敏结论，按住院号查询）；"
                + "多耐药标记来自 patient_info。");
        return assessment;
    }

    @Override
    public IcuPatientBrief getPatientBrief(String patientId) {
        Map<String, Object> row = icuPatientMapper.selectPatientById(patientId);
        if (row == null || row.isEmpty()) {
            return null;
        }
        IcuPatientBrief p = buildBrief(row);
        List<Map<String, Object>> diagnoses = icuPatientMapper.selectDiagnoses(patientId);
        boolean shockFlag = intToBool(row.get("septic_shock"));
        p.setInfectionType(inferInfectionType(diagnoses, shockFlag));
        applyShockType(p, diagnoses, shockFlag);
        return p;
    }

    @Override
    public String resolvePatientIdByInHospitalNo(String inHospitalNo) {
        if (StrUtil.isBlank(inHospitalNo)) {
            return null;
        }
        Map<String, Object> row = icuPatientMapper.selectPatientByInHospitalNo(inHospitalNo);
        return row == null ? null : str(row.get("patient_id"));
    }

    @Override
    public List<Map<String, Object>> searchStaff(String keyword) {
        String kw = StrUtil.isBlank(keyword) ? "" : keyword.trim();
        return icuPatientMapper.selectStaff(kw);
    }

    // ------------------------------------------------------------------
    // 内部组装
    // ------------------------------------------------------------------

    private IcuPatientBrief buildBrief(Map<String, Object> row) {
        IcuPatientBrief p = new IcuPatientBrief();
        p.setPatientId(str(row.get("patient_id")));
        p.setPatientNo(str(row.get("patient_no")));
        p.setName(str(row.get("name")));
        p.setAge(parseInt(row.get("age")));
        p.setGender(str(row.get("gender")));
        p.setDepartment(str(row.get("department")));
        p.setBedNo(str(row.get("bed_no")));
        // 体重（第二维度 PK/PD 剂量计算用）
        p.setWeight(parseDecimal(row.get("weight")));
        // 身高（第二维度 BMI/IBW/AdjBW 计算用）
        p.setHeight(parseDecimal(row.get("height")));
        // 休克由 applyShockType 统一判定（诊断文字优先，patient_info 标记兜底）。
        // 这里只是默认值——调用方必须接着调 applyShockType，否则患者一律是"非休克"，
        // 风险等级和决策页的休克分层推荐都会跟着错。
        p.setSepticShock(false);
        p.setShockType("none");
        parseRiskFlags(p, row);
        return p;
    }

    /** 多耐药标记 → MRSA / MDR / 真菌风险。字典：1MRSA 2VRE 3CRE 4CR-AB 5MDR/PDR-PA */
    private void parseRiskFlags(IcuPatientBrief p, Map<String, Object> row) {
        String mdrBacteria = str(row.get("mdr_bacteria"));
        String resistant = str(row.get("resistant_bacteria"));
        boolean mrsa = containsAnyChar(mdrBacteria, '1')
                || containsIgnoreCase(resistant, "MRSA", "耐甲氧西林");
        boolean mdr = containsAnyChar(mdrBacteria, '3', '4', '5')
                || containsIgnoreCase(resistant, "ESBL", "CRE", "碳青霉烯", "鲍曼", "铜绿", "MDR");
        boolean fungal = containsIgnoreCase(mdrBacteria, "真菌")
                || containsIgnoreCase(resistant, "念珠菌", "曲霉");
        p.setMrsaRisk(mrsa);
        p.setMdrRisk(mdr);
        p.setFungalRisk(fungal);
    }

    /**
     * 从诊断推断感染类型（优先级：脓毒 > HAP/VAP > CAP > 腹腔 > 血流 > 尿路 > 真菌 > 其他感染）。
     *
     * <p>诊断由调用方一次性批量取回后传入：列表页 50 个患者原本要查 50+ 次诊断，
     * 现在全表只用 1 次。
     *
     * @param shockFlag patient_info.is_sepsis_shock 标记，用于无感染诊断时的兜底归类
     */
    private String inferInfectionType(List<Map<String, Object>> diagnoses, boolean shockFlag) {
        if (diagnoses == null) {
            diagnoses = java.util.Collections.emptyList();
        }
        for (Map<String, Object> d : diagnoses) {
            if (d == null) {
                continue;
            }
            String name = str(d.get("diag_name"));
            if (StrUtil.isBlank(name)) {
                continue;
            }
            if (containsAny(name, "脓毒", "感染性休克")) {
                return "脓毒症/感染性休克";
            }
            if (name.contains("肺炎")) {
                if (containsAny(name, "医院获得", "呼吸机", "医院", "院内")) {
                    return "医院获得性肺炎（HAP/VAP）";
                }
                return "社区获得性肺炎（CAP）";
            }
            if (containsAny(name, "腹腔", "腹膜炎", "胆道")) {
                return "腹腔感染";
            }
            if (containsAny(name, "血流", "菌血症", "败血症")) {
                return "血流感染";
            }
            if (containsAny(name, "尿路", "泌尿", "肾盂")) {
                return "尿路感染";
            }
            if (containsAny(name, "真菌", "念珠菌", "曲霉")) {
                return "侵袭性真菌感染";
            }
            if (name.contains("感染")) {
                return name.length() > 30 ? "感染（部位待明确）" : name;
            }
        }
        // 无明确感染诊断：若休克标记，归类为脓毒症
        return shockFlag ? "脓毒症/感染性休克" : "感染（部位待明确）";
    }

    /**
     * 判定休克类型。
     * patient_info_diagnosis.diag_name 匹配：
     *  - 含"脓毒性休克" → septic（脓毒性休克）
     *  - 含"感染性休克" → infectious（感染性休克）
     *  - 都不匹配 → 看 patient_info.is_sepsis_shock 标记 → 仍为 septic
     *  - 都没有 → none（非休克）
     *
     * <p><b>为什么必须看主表标记兜底</b>：之前只在诊断文字里找"脓毒性休克/感染性休克"，
     * 诊断没写这两个词、但主表打了 is_sepsis_shock=1 的患者会被判成非休克，
     * 进而风险等级掉到中/低、决策页推荐也走不到脓毒性休克分支。
     * 标记位本身就是"脓毒性休克"的临床结论，不能因为诊断措辞不同就丢掉。
     *
     * <p>防御：查询结果可能为 null（无诊断记录），列表中也可能出现 null 元素。
     * 此处原先未做保护，遍历到 null 元素时 d.get("diag_name") 抛 NPE，
     * 表现为"只有个别患者的 PK/PD 页面 500、其他页面正常"。
     */
    private void applyShockType(IcuPatientBrief p, List<Map<String, Object>> diagnoses, boolean shockFlag) {
        if (diagnoses == null) {
            diagnoses = java.util.Collections.emptyList();
        }
        for (Map<String, Object> d : diagnoses) {
            if (d == null) {
                continue;
            }
            String name = str(d.get("diag_name"));
            if (StrUtil.isBlank(name)) {
                continue;
            }
            if (name.contains("脓毒性休克")) {
                p.setShockType("septic");
                p.setSepticShock(true);
                return;
            }
            if (name.contains("感染性休克")) {
                p.setShockType("infectious");
                p.setSepticShock(true);
                return;
            }
        }
        if (shockFlag) {
            p.setShockType("septic");
            p.setSepticShock(true);
            return;
        }
        p.setShockType("none");
        p.setSepticShock(false);
    }

    /** 补充检验指标（PCT/WBC）、体温、抗菌药开始时间、微生物风险增强与风险分层 */
    private void enrichLabsAndRisk(IcuPatientBrief p) {
        Map<String, String> labs = loadLabs(p.getPatientNo());
        p.setPct(parseDecimalValue(labs.get("PCT")));
        p.setWbc(parseDecimalValue(labs.get("WBC")));
        p.setTemperature(parseDecimalValue(loadTemperature(p.getPatientId())));
        p.setAbxStartTime(loadAbxStartTime(p.getPatientNo()));
        // 培养/药敏结果增强 MDR / MRSA / 真菌 风险判断
        enhanceRiskFromCultures(p, loadMicrobiologyRows(p.getPatientNo()));
        p.setRiskLevel(evaluateRisk(p));
    }

    /** 加载近期检验并按下述名称映射为指标（取每条指标最新一条） */
    private Map<String, String> loadLabs(String inHospitalNo) {
        Map<String, String> out = new LinkedHashMap<>();
        if (StrUtil.isBlank(inHospitalNo)) {
            return out;
        }
        List<Map<String, Object>> rows = icuPatientMapper.selectRecentLabs(inHospitalNo);
        Map<String, String> matched = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String itemName = str(r.get("item_name"));
            String result = str(r.get("result"));
            String unit = str(r.get("unit"));
            if (StrUtil.isBlank(itemName) || StrUtil.isBlank(result)) {
                continue;
            }
            String key = matchLabKey(itemName);
            if (key != null && !matched.containsKey(key)) {
                matched.put(key, result + (StrUtil.isBlank(unit) ? "" : " " + unit));
            }
        }
        // 顺序稳定输出
        for (String key : Arrays.asList("PCT", "WBC", "CRP", "乳酸", "肌酐")) {
            if (matched.containsKey(key)) {
                out.put(key, matched.get(key));
            }
        }
        return out;
    }

    private String matchLabKey(String itemName) {
        String s = itemName == null ? "" : itemName.trim();
        // 先排除易混淆项目：乳酸脱氢酶（非血乳酸）、尿素/肌酐（比值）、尿常规/白细胞分类计数（非血常规白细胞总数）
        if (s.contains("脱氢酶") || s.contains("尿素/肌酐")) {
            return null;
        }
        if (s.contains("降钙素原")) return "PCT";
        if (s.contains("白细胞") && !s.contains("尿") && !s.contains("分类")) return "WBC";
        if (s.contains("C反应蛋白") || s.contains("超敏C")) return "CRP";
        if (s.contains("乳酸")) return "乳酸";
        if (s.contains("肌酐")) return "肌酐";
        String upper = s.toUpperCase();
        if (upper.equals("PCT")) return "PCT";
        if (upper.equals("WBC")) return "WBC";
        if (upper.equals("CRP")) return "CRP";
        if (upper.contains("CREA") || upper.contains("CR") && upper.length() <= 4) return "肌酐";
        return null;
    }

    /** 关键检验指标趋势（按指标分组、时间升序，供前端趋势图）。非数值结果（如 <0.5 / 阴性）跳过。 */
    private List<LabTrend> loadLabTrends(String inHospitalNo) {
        List<LabTrend> out = new ArrayList<>();
        if (StrUtil.isBlank(inHospitalNo)) {
            return out;
        }
        List<Map<String, Object>> rows = icuPatientMapper.selectRecentLabs(inHospitalNo);
        Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
        // 每个指标的参考范围 [low, high]，取第一条非空记录的范围
        Map<String, BigDecimal[]> limits = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String itemName = str(r.get("item_name"));
            String result = str(r.get("result"));
            if (StrUtil.isBlank(itemName) || StrUtil.isBlank(result)) {
                continue;
            }
            String key = matchLabKey(itemName);
            if (key == null) {
                continue;
            }
            // 参考范围：优先结构化字段 low_value / height_value，否则解析 item_limit 文本
            if (!limits.containsKey(key)) {
                BigDecimal[] lim = parseLimit(r.get("low_value"), r.get("height_value"), r.get("item_limit"));
                if (lim != null) {
                    limits.put(key, lim);
                }
            }
            BigDecimal val = parseDecimal(result);
            LocalDateTime t = toLocalDateTime(r.get("check_time"));
            if (val == null || t == null) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new Object[]{t, val});
        }
        for (String key : Arrays.asList("PCT", "WBC", "CRP", "乳酸", "肌酐")) {
            List<Object[]> pts = grouped.get(key);
            if (pts == null || pts.isEmpty()) {
                continue;
            }
            pts.sort(Comparator.comparing(a -> (LocalDateTime) a[0]));
            // 同一指标同一时间只保留一个点（同一批次可能有多条记录，避免折线在同时刻重复）
            List<Object[]> dedup = new ArrayList<>();
            LocalDateTime lastTime = null;
            for (Object[] pt : pts) {
                LocalDateTime t = (LocalDateTime) pt[0];
                if (lastTime != null && t.equals(lastTime)) {
                    continue;
                }
                dedup.add(pt);
                lastTime = t;
            }
            LabTrend trend = new LabTrend();
            trend.setItemName(key);
            BigDecimal[] lim = limits.get(key);
            if (lim != null) {
                trend.setLowLimit(lim[0]);
                trend.setHighLimit(lim[1]);
            }
            for (Object[] pt : dedup) {
                TrendPoint p = new TrendPoint();
                p.setTime(formatChartTime((LocalDateTime) pt[0]));
                p.setValue((BigDecimal) pt[1]);
                trend.getSeries().add(p);
            }
            out.add(trend);
        }
        return out;
    }

    /**
     * 解析检验参考范围。优先用结构化字段 low_value / height_value；
     * 都为空时解析 item_limit 文本（支持 "3.5-9.5"、"<1.5"、">0.5"、"0.108~0.271" 等格式）。
     * 返回 [low, high]，无范围返回 null。
     */
    private BigDecimal[] parseLimit(Object lowValue, Object highValue, Object itemLimit) {
        BigDecimal low = parseDecimal(lowValue);
        BigDecimal high = parseDecimal(highValue);
        if (low != null || high != null) {
            return new BigDecimal[]{low, high};
        }
        String limit = str(itemLimit);
        if (StrUtil.isBlank(limit)) {
            return null;
        }
        limit = limit.trim();
        // "3.5-9.5" / "0.108~0.271" / "0.108～0.271"
        java.util.regex.Matcher mRange = java.util.regex.Pattern
                .compile("^\\s*(\\d+\\.?\\d*)\\s*[-~～]\\s*(\\d+\\.?\\d*)\\s*$").matcher(limit);
        if (mRange.find()) {
            return new BigDecimal[]{new BigDecimal(mRange.group(1)), new BigDecimal(mRange.group(2))};
        }
        // "<1.5" / "≤1.5"
        java.util.regex.Matcher mHigh = java.util.regex.Pattern
                .compile("^\\s*[<≤]\\s*(\\d+\\.?\\d*)\\s*$").matcher(limit);
        if (mHigh.find()) {
            return new BigDecimal[]{null, new BigDecimal(mHigh.group(1))};
        }
        // ">0.5" / "≥0.5"
        java.util.regex.Matcher mLow = java.util.regex.Pattern
                .compile("^\\s*[>≥]\\s*(\\d+\\.?\\d*)\\s*$").matcher(limit);
        if (mLow.find()) {
            return new BigDecimal[]{new BigDecimal(mLow.group(1)), null};
        }
        return null;
    }

    /** 趋势图时间标签：MM-dd HH:mm */
    private String formatChartTime(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        return t.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private String loadTemperature(String patientId) {
        Map<String, Object> row = icuPatientMapper.selectLatestTemperature(patientId);
        return row == null ? null : str(row.get("item_value"));
    }

    /** 当前抗菌药列表（patient_advice_pda JOIN patient_advice，按 group_id_mark 去重，排除溶媒） */
    private List<AbxCurrentItem> loadCurrentAntibiotics(String inHospitalNo) {
        List<AbxCurrentItem> out = new ArrayList<>();
        if (StrUtil.isBlank(inHospitalNo)) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> abxList = icuPatientMapper.selectCurrentAbxAdvice(inHospitalNo);
        if (abxList == null) abxList = java.util.Collections.emptyList();
        for (Map<String, Object> r : abxList) {
            String name = str(r.get("name"));
            if (StrUtil.isBlank(name) || !matchesAbx(name) || isSolvent(name)) {
                continue;
            }
            String key = str(r.get("group_id")) + "|" + name;
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);

            AbxCurrentItem item = new AbxCurrentItem();
            item.setName(name);
            item.setMethod(str(r.get("method_name")));
            item.setFreq(str(r.get("freq_name")));

            // 开始时间：取 patient_advice.plan_start_time
            LocalDateTime startTime = toLocalDateTime(r.get("start_time"));
            item.setStartTime(startTime != null ? formatChartTime(startTime) : "");

            out.add(item);
        }
        return out;
    }

    /** 判断是否为溶媒（同一 group 下溶媒与溶质分开存储，只取溶质） */
    private boolean isSolvent(String name) {
        if (StrUtil.isBlank(name)) {
            return false;
        }
        for (String kw : SOLVENT_KEYWORDS) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 最早一次抗菌药开始时间（用于判断已用时长；来源 patient_advice）。
     *
     * <p>原来依次取 pda_start_time / pda_plan_time / plan_start_time 三列，
     * 但 {@code selectCurrentAbxAdvice} 只返回 start_time，三列都不存在 → 恒为 null，
     * "抗菌药已用时长"一直是空的。改取实际返回的那列。
     */
    private LocalDateTime loadAbxStartTime(String inHospitalNo) {
        LocalDateTime earliest = null;
        List<Map<String, Object>> abxList = icuPatientMapper.selectCurrentAbxAdvice(inHospitalNo);
        if (abxList == null) abxList = java.util.Collections.emptyList();
        for (Map<String, Object> r : abxList) {
            String name = str(r.get("name"));
            if (!matchesAbx(name) || isSolvent(name)) {
                continue;
            }
            LocalDateTime t = toLocalDateTime(r.get("start_time"));
            if (t != null && (earliest == null || t.isBefore(earliest))) {
                earliest = t;
            }
        }
        return earliest;
    }

    private boolean matchesAbx(String name) {
        for (String kw : ABX_KEYWORDS) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 风险分层（多因素）。
     *
     * <p>原来只看"休克 / PCT"两件事，会出现「标签栏三个高危（MDR+MRSA+真菌）全亮、
     * 风险等级却是低风险」这种自相矛盾的显示——因为耐药与真菌风险根本没参与分级。
     * 医生看列表时首先扫的就是风险等级，标签与等级打架会直接削弱对页面的信任。
     *
     * <p>现行口径：
     * <ul>
     *   <li>高风险：脓毒性休克 / PCT ≥ 2 / MDR 合并 MRSA / MDR 合并真菌风险</li>
     *   <li>中风险：PCT 0.5~2 / MDR、MRSA、真菌任一风险 / HAP-VAP / 血流感染</li>
     *   <li>低风险：单纯疑似感染，暂无上述高危指标</li>
     * </ul>
     */
    private String evaluateRisk(IcuPatientBrief p) {
        if (Boolean.TRUE.equals(p.getSepticShock())) {
            return "高风险";
        }
        if (geThreshold(p.getPct(), PCT_HIGH_THRESHOLD)) {
            return "高风险";
        }
        boolean mdr = Boolean.TRUE.equals(p.getMdrRisk());
        boolean mrsa = Boolean.TRUE.equals(p.getMrsaRisk());
        boolean fungal = Boolean.TRUE.equals(p.getFungalRisk());
        if (mdr && (mrsa || fungal)) {
            return "高风险";
        }
        if (geThreshold(p.getPct(), PCT_SUSPECT_THRESHOLD) || mdr || mrsa || fungal) {
            return "中风险";
        }
        String type = p.getInfectionType() == null ? "" : p.getInfectionType();
        if (type.contains("HAP") || type.contains("VAP") || type.contains("血流")) {
            return "中风险";
        }
        return "低风险";
    }

    private List<String> parseAllergies(String allergyContent) {
        List<String> out = new ArrayList<>();
        if (StrUtil.isBlank(allergyContent)) {
            out.add("无记录");
            return out;
        }
        String trimmed = allergyContent.trim();
        if (trimmed.isEmpty() || "无".equals(trimmed) || "无过敏史".equals(trimmed)) {
            out.add("无记录");
            return out;
        }
        for (String part : trimmed.split("[,，;；、]")) {
            if (StrUtil.isNotBlank(part)) {
                out.add(part.trim());
            }
        }
        return out.isEmpty() ? Arrays.asList("无记录") : out;
    }

    /** 既往培养/药敏 + 患者多耐药标记（pastCultures 组装） */
    private List<String> buildPastCultures(String inHospitalNo, String mdrBacteria, String resistant) {
        List<String> out = buildCultureDescriptions(inHospitalNo);
        if (StrUtil.isNotBlank(mdrBacteria)) {
            out.add("患者多耐药菌标记：" + mdrBacteria);
        }
        if (StrUtil.isNotBlank(resistant)) {
            out.add("耐药菌记录：" + resistant);
        }
        if (out.isEmpty()) {
            out.add("无明确微生物培养/多耐药记录");
        }
        return out;
    }

    /** 从 patient_info_lis_item 解析微生物培养/药敏结果（结果值即菌名或药敏结论） */
    private List<String> buildCultureDescriptions(String inHospitalNo) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : loadMicrobiologyRows(inHospitalNo)) {
            String itemName = str(r.get("item_name"));
            String result = str(r.get("result"));
            String speciman = str(r.get("speciman"));
            String reportName = str(r.get("report_name"));
            if (StrUtil.isBlank(itemName) || StrUtil.isBlank(result)) {
                continue;
            }
            String specimenText = StrUtil.isBlank(speciman) ? "标本待定" : speciman;
            String time = formatShortDate(r.get("check_time"));
            String when = StrUtil.isBlank(time) ? "既往" : time;
            if (isSusceptibilityRow(itemName, result)) {
                // 药敏结论行（result 为 S/I/R 或 敏感/中介/耐药）
                out.add(when + " " + specimenText + " 药敏：" + itemName + " → " + result);
            } else {
                // 培养检出行（result 为菌名）
                String type = itemName.contains("涂片") || itemName.contains("镜检") ? "镜检" : "培养";
                out.add(when + " " + specimenText + type + "：" + result);
            }
        }
        return out;
    }

    /** 培养结果增强风险判断：菌名/药敏中含耐药关键词 → MRSA/MDR/真菌 */
    private void enhanceRiskFromCultures(IcuPatientBrief p, List<Map<String, Object>> microRows) {
        for (Map<String, Object> r : microRows) {
            String itemName = str(r.get("item_name"));
            String result = str(r.get("result"));
            String combined = itemName + " " + result;
            if (containsIgnoreCase(combined, "MRSA", "耐甲氧西林", "甲氧西林耐药")) {
                p.setMrsaRisk(true);
            }
            if (containsIgnoreCase(combined, "ESBL", "CRE", "CRKP", "CRAB", "耐碳青霉烯",
                    "碳青霉烯耐药", "鲍曼", "铜绿假单胞", "泛耐药", "MDR")) {
                p.setMdrRisk(true);
            }
            if (containsIgnoreCase(combined, "念珠菌", "曲霉", "隐球菌", "真菌")) {
                p.setFungalRisk(true);
            }
        }
    }

    private List<Map<String, Object>> loadMicrobiologyRows(String inHospitalNo) {
        if (StrUtil.isBlank(inHospitalNo)) {
            return Collections.emptyList();
        }
        return icuPatientMapper.selectMicrobiology(inHospitalNo);
    }

    /** 是否为药敏结论行：项目名含药敏，或结果为 S/I/R / 敏感 / 中介 / 耐药 */
    private boolean isSusceptibilityRow(String itemName, String result) {
        String r = result.trim().toUpperCase();
        if ("S".equals(r) || "I".equals(r) || "R".equals(r)
                || r.contains("SENSITIVE") || r.contains("敏感")
                || r.contains("中介") || r.contains("耐药")) {
            return true;
        }
        return itemName.contains("药敏") && !looksLikeBacteria(result);
    }

    /** 粗略判断结果是否为菌名（中文含菌/杆菌/球菌/真菌，或含拉丁属名特征） */
    private boolean looksLikeBacteria(String result) {
        if (StrUtil.isBlank(result)) {
            return false;
        }
        String r = result.trim();
        if (r.matches("^[0-9+\\-\\s.]+$")) {
            return false;
        }
        return r.contains("菌") || r.contains("霉") || r.contains("念珠")
                || r.matches(".*[a-zA-Z]{2,}.*");
    }

    /** 格式化报告时间为 MM-dd HH:mm（跨年数据保留年份） */
    private String formatShortDate(Object o) {
        LocalDateTime t = toLocalDateTime(o);
        if (t == null) {
            return "";
        }
        if (t.getYear() == LocalDateTime.now().getYear()) {
            return t.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        }
        return t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

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

    private Integer parseInt(Object o) {
        if (o == null || StrUtil.isBlank(str(o))) {
            return null;
        }
        try {
            String s = str(o);
            if (s.indexOf('.') >= 0) {
                s = s.substring(0, s.indexOf('.'));
            }
            return Integer.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(Object o) {
        if (o == null || StrUtil.isBlank(str(o))) {
            return null;
        }
        try {
            return new BigDecimal(str(o));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检验结果 → 数值。
     *
     * <p>检验结果常带单位（"0.85 ng/mL"）或比较符（"&lt;0.05"），
     * {@link #parseDecimal} 直接 {@code new BigDecimal} 会整条失败、指标变 null
     * （表现为列表里 PCT / 体温一列全空）。这里失败后退回到"截取第一个数值"：
     * 带单位时取数字部分，带比较符时取阈值本身（&lt;0.05 记作 0.05 —— 对
     * 0.5 门槛的判断是保守的，不会把低值误判成感染证据）。
     */
    private BigDecimal parseDecimalValue(Object o) {
        BigDecimal direct = parseDecimal(o);
        if (direct != null) {
            return direct;
        }
        String s = str(o);
        if (StrUtil.isBlank(s)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s);
        if (m.find()) {
            try {
                return new BigDecimal(m.group());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private boolean intToBool(Object o) {
        String s = str(o);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "是".equals(s);
    }

    private boolean containsAny(String s, String... keys) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        for (String k : keys) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyChar(String s, char... chars) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        for (char c : chars) {
            if (s.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String s, String... keys) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        String lower = s.toLowerCase();
        for (String k : keys) {
            if (lower.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime) {
            return (LocalDateTime) o;
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime();
        }
        if (o instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) o).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(str(o).replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }
}
