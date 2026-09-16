package com.zing.doctor.module.antibiotic.knowledge;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * ICU 常用抗菌药物 PK/PD 知识库。
 * 基于药品说明书、《抗菌药物药代动力学/药效学理论临床应用专家共识》、
 * 《中国脓毒症/脓毒性休克急诊治疗指南》等整理。
 *
 * <p>PK/PD 分类：
 * <ul>
 *   <li>TIME_DEPENDENT：时间依赖性（β-内酰胺类），目标参数 %T>MIC</li>
 *   <li>CONCENTRATION_DEPENDENT：浓度依赖性（氨基糖苷类/喹诺酮类/达托霉素），目标 Cmax/MIC、AUC/MIC</li>
 *   <li>TIME_DEPENDENT_LONG_PAE：时间依赖性且长PAE（万古霉素/替考拉宁/利奈唑胺/四环素类），目标 AUC/MIC</li>
 * </ul>
 */
@Getter
public enum AbxDrugKnowledge {

    // ========== β-内酰胺类（时间依赖性） ==========
    MEROPENEM("美罗培南", "TIME_DEPENDENT", "%T>MIC", "≥70-100%（脓毒症）",
            2, "renal", "1g q8h", "CrCl<50 调整剂量/间隔",
            false, false, "3h 延长输注或持续输注"),
    IMIPENEM("亚胺培南", "TIME_DEPENDENT", "%T>MIC", "≥70-100%",
            20, "renal", "0.5g q6h", "CrCl<70 调整",
            false, false, "可延长输注"),
    PIPERACILLIN_TAZOBACTAM("哌拉西林他唑巴坦", "TIME_DEPENDENT", "%T>MIC", "≥50-70%",
            30, "renal", "4.5g q6h", "CrCl<40 调整",
            false, false, "3h 延长输注"),
    CEFTAZIDIME("头孢他啶", "TIME_DEPENDENT", "%T>MIC", "≥50-70%",
            17, "renal", "2g q8h", "CrCl<50 调整",
            false, false, "3h 延长输注"),
    CEFEPIME("头孢吡肟", "TIME_DEPENDENT", "%T>MIC", "≥50-70%",
            20, "renal", "2g q8h", "CrCl<60 调整",
            false, false, "3h 延长输注"),
    CEFTRIAXONE("头孢曲松", "TIME_DEPENDENT", "%T>MIC", "≥50-70%",
            95, "dual", "2g qd", "肾功能不全无需调整（肝肾双通道）",
            true, false, "高蛋白结合率，低蛋白血症时注意"),
    CEFOPERAZONE_SULBACTAM("头孢哌酮舒巴坦", "TIME_DEPENDENT", "%T>MIC", "≥50-70%",
            82, "dual", "3g q8h", "肝功能不全需调整",
            true, false, "高蛋白结合率"),
    AMOXICILLIN_CLAVULANATE("阿莫西林克拉维酸", "TIME_DEPENDENT", "%T>MIC", "≥50%",
            18, "renal", "1.2g q8h", "CrCl<30 调整",
            false, false, ""),

    // ========== 糖肽类（时间依赖性+长PAE） ==========
    VANCOMYCIN("万古霉素", "TIME_DEPENDENT_LONG_PAE", "AUC/MIC", "≥400",
            55, "renal", "15-20mg/kg q12h（负荷20-25mg/kg）", "按 CrCl 调整间隔，CrCl<30 q48-72h",
            false, true, "TDM：谷浓度15-20mg/L（严重感染），第4剂前30min采样"),
    NORVANCOMYCIN("去甲万古霉素", "TIME_DEPENDENT_LONG_PAE", "AUC/MIC", "≥400",
            55, "renal", "0.8-1.6g/d 分2-4次", "同万古霉素",
            false, true, "TDM 同万古霉素"),
    TEICOPLANIN("替考拉宁", "TIME_DEPENDENT_LONG_PAE", "AUC/MIC", "≥400",
            90, "renal", "负荷400mg q12h×3剂，维持400mg qd", "CrCl<50 调整剂量/间隔",
            true, true, "TDM：谷浓度15-30mg/L，第3剂前采样；高蛋白结合率"),

    // ========== 恶唑烷酮类（时间依赖性+长PAE） ==========
    LINEZOLID("利奈唑胺", "TIME_DEPENDENT_LONG_PAE", "AUC/MIC", "≥80-100",
            31, "hepatic", "600mg q12h", "肾功能不全无需调整",
            false, true, "TDM：谷浓度2-7mg/L，>7 血小板减少风险"),

    // ========== 氨基糖苷类（浓度依赖性） ==========
    AMIKACIN("阿米卡星", "CONCENTRATION_DEPENDENT", "Cmax/MIC", "≥8-10",
            4, "renal", "15mg/kg qd", "按 CrCl 调整间隔，CrCl<30 q48-72h",
            false, true, "TDM：峰浓度20-30mg/L，谷浓度<4mg/L"),
    GENTAMICIN("庆大霉素", "CONCENTRATION_DEPENDENT", "Cmax/MIC", "≥8-10",
            30, "renal", "5-7mg/kg qd", "按 CrCl 调整",
            false, true, "TDM：峰浓度8-12mg/L，谷浓度<1mg/L"),
    TOBRAMYCIN("妥布霉素", "CONCENTRATION_DEPENDENT", "Cmax/MIC", "≥8-10",
            30, "renal", "5-7mg/kg qd", "按 CrCl 调整",
            false, true, "TDM 同庆大霉素"),

    // ========== 氟喹诺酮类（浓度依赖性） ==========
    LEVOFLOXACIN("左氧氟沙星", "CONCENTRATION_DEPENDENT", "AUC/MIC", "≥100-125",
            38, "renal", "750mg qd", "CrCl<50 调整（500mg q48h）",
            false, false, ""),
    MOXIFLOXACIN("莫西沙星", "CONCENTRATION_DEPENDENT", "AUC/MIC", "≥100-125",
            45, "hepatic", "400mg qd", "肾功能不全无需调整",
            false, false, ""),
    CIPROFLOXACIN("环丙沙星", "CONCENTRATION_DEPENDENT", "AUC/MIC", "≥100-125",
            30, "renal", "400mg q12h", "CrCl<30 调整",
            false, false, ""),

    // ========== 抗真菌药 ==========
    FLUCONAZOLE("氟康唑", "CONCENTRATION_DEPENDENT", "AUC/MIC", "≥20-25",
            12, "renal", "负荷800mg，维持400mg qd", "CrCl<50 减半",
            false, true, "TDM：谷浓度≥目标MIC的2倍，隐球菌≥10mg/L"),
    VORICONAZOLE("伏立康唑", "CONCENTRATION_DEPENDENT", "AUC/MIC", "≥20-25",
            58, "hepatic", "负荷400mg q12h×2，维持200mg q12h", "肾功能不全口服无需调整，静脉制剂需注意",
            false, true, "TDM：谷浓度1-5mg/L，>5 毒性增加，第4剂前采样"),
    CASPOFUNGIN("卡泊芬净", "TIME_DEPENDENT", "%T>MIC", "—",
            97, "hepatic", "负荷70mg，维持50mg qd", "肾功能不全无需调整，中度肝功能不全35mg qd",
            true, false, "高蛋白结合率"),
    MICAFUNGIN("米卡芬净", "TIME_DEPENDENT", "%T>MIC", "—",
            99, "hepatic", "100-150mg qd", "肾功能不全无需调整",
            true, false, "高蛋白结合率"),

    // ========== 其他 ==========
    TIGECYCLINE("替加环素", "TIME_DEPENDENT_LONG_PAE", "AUC/MIC", "≥12.8",
            80, "hepatic", "负荷100mg，维持50mg q12h", "肾功能不全无需调整，重度肝功能不全减量",
            true, false, "高蛋白结合率"),
    POLYMYXIN_B("多粘菌素B", "CONCENTRATION_DEPENDENT", "AUC/MIC", "—",
            90, "renal", "负荷2.5mg/kg，维持1.25-1.5mg/kg q12h", "按 CrCl 调整",
            true, false, "肾毒性/神经毒性，高蛋白结合率"),
    TMP_SMX("复方磺胺甲噁唑", "TIME_DEPENDENT", "%T>MIC", "≥50%",
            70, "renal", "15-20mg/kg q6h（按TMP计）", "CrCl<30 调整，CrCl<15 禁用",
            true, false, "高蛋白结合率，注意高钾/骨髓抑制");

    private final String drugName;
    private final String pkpdType;        // TIME_DEPENDENT / CONCENTRATION_DEPENDENT / TIME_DEPENDENT_LONG_PAE
    private final String targetParam;     // %T>MIC / Cmax/MIC / AUC/MIC
    private final String targetValue;     // 目标值
    private final int proteinBinding;     // 蛋白结合率（%）
    private final String clearanceRoute;   // renal / hepatic / dual
    private final String usualDose;        // 常规剂量
    private final String renalAdjustment;  // 肾功能调整说明
    private final boolean highProteinBinding; // 高蛋白结合率（>80%）
    private final boolean tdmRequired;     // 是否需要 TDM
    private final String remark;            // 备注

    AbxDrugKnowledge(String drugName, String pkpdType, String targetParam, String targetValue,
                     int proteinBinding, String clearanceRoute, String usualDose, String renalAdjustment,
                     boolean highProteinBinding, boolean tdmRequired, String remark) {
        this.drugName = drugName;
        this.pkpdType = pkpdType;
        this.targetParam = targetParam;
        this.targetValue = targetValue;
        this.proteinBinding = proteinBinding;
        this.clearanceRoute = clearanceRoute;
        this.usualDose = usualDose;
        this.renalAdjustment = renalAdjustment;
        this.highProteinBinding = highProteinBinding;
        this.tdmRequired = tdmRequired;
        this.remark = remark;
    }

    /**
     * 根据药物名称模糊匹配知识库。
     * 匹配规则：药物名称包含知识库中的通用名（如"注射用美罗培南"匹配 MEROPENEM）。
     */
    public static AbxDrugKnowledge match(String drugName) {
        if (drugName == null || drugName.trim().isEmpty()) {
            return null;
        }
        String name = drugName.trim();
        // 精确匹配优先
        for (AbxDrugKnowledge k : values()) {
            if (name.equals(k.drugName)) {
                return k;
            }
        }
        // 模糊匹配：药物名包含通用名，或通用名包含药物名
        for (AbxDrugKnowledge k : values()) {
            if (name.contains(k.drugName) || k.drugName.contains(name)) {
                return k;
            }
        }
        // 关键词匹配（处理"注射用盐酸万古霉素"这类带前缀的）
        for (AbxDrugKnowledge k : values()) {
            String shortName = k.drugName.length() > 2 ? k.drugName.substring(0, 2) : k.drugName;
            if (name.contains(shortName) && name.length() > 3) {
                // 二次确认：名称中至少包含通用名的一半字符
                int matchCount = 0;
                for (char c : k.drugName.toCharArray()) {
                    if (name.indexOf(c) >= 0) matchCount++;
                }
                if (matchCount >= k.drugName.length() * 0.6) {
                    return k;
                }
            }
        }
        return null;
    }

    /** PK/PD 分类的中文描述 */
    public String getPkpdTypeText() {
        switch (pkpdType) {
            case "TIME_DEPENDENT": return "时间依赖性";
            case "CONCENTRATION_DEPENDENT": return "浓度依赖性";
            case "TIME_DEPENDENT_LONG_PAE": return "时间依赖性+长PAE";
            default: return pkpdType;
        }
    }

    /** 清除途径中文描述 */
    public String getClearanceRouteText() {
        switch (clearanceRoute) {
            case "renal": return "肾脏清除";
            case "hepatic": return "肝脏清除";
            case "dual": return "肝肾双通道";
            default: return clearanceRoute;
        }
    }

    /** 获取所有需要 TDM 的药物 */
    public static List<AbxDrugKnowledge> getTdmDrugs() {
        return Arrays.stream(values()).filter(k -> k.tdmRequired).collect(java.util.stream.Collectors.toList());
    }
}
