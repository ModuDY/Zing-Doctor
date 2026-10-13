package com.zing.doctor.module.nutrition.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营养支持分析响应（只读聚合）。
 *
 * <p>口径：实际给入 ml = Σ patient_advice_execute_item.amount（按小时）；
 * 能量/蛋白 = 该值 × config_nutrition_formula 密度。未匹配配方的普通液体/药物不纳入营养合计。
 */
@Data
public class NutritionResponse {

    private PatientHeader header;
    private Window window;
    /** 窗口内识别为营养制剂的执行单（已按制剂合并同剂型） */
    private List<NutritionOrder> orders;
    /** 未被配方识别、但属于肠内/肠外途径的执行单数量（提示可能漏配） */
    private int unmappedCount;
    private Totals totals;
    /** FOUND / NOT_RECORDED / UNKNOWN */
    private String dataStatus;
    private String note;

    @Data
    public static class PatientHeader {
        private String patientId;
        private String inHospitalNo;
        private String name;
        private String bedNo;
        private String departName;
        private String inDepartmentTime;
    }

    @Data
    public static class Window {
        private Integer hours;
        private String startTime;
        private String endTime;
    }

    @Data
    public static class NutritionOrder {
        private String formulaCode;
        private String formulaName;
        /** ENTERAL 肠内 / PARENTERAL 肠外 */
        private String routeType;
        private String methodName;
        /** 整袋/整瓶规格 ml（仅参考，不等于已给入） */
        private BigDecimal bagVolume;
        private String startTime;
        private String endTime;
        /** 窗口内实际给入 ml（Σ小时 amount） */
        private BigDecimal givenMl;
        private BigDecimal kcal;
        private BigDecimal proteinG;
        private boolean mapped;
    }

    @Data
    public static class Totals {
        private BigDecimal enteralMl = BigDecimal.ZERO;
        private BigDecimal enteralKcal = BigDecimal.ZERO;
        private BigDecimal enteralProteinG = BigDecimal.ZERO;
        private BigDecimal parenteralMl = BigDecimal.ZERO;
        private BigDecimal parenteralKcal = BigDecimal.ZERO;
        private BigDecimal parenteralProteinG = BigDecimal.ZERO;
        private BigDecimal totalKcal = BigDecimal.ZERO;
        private BigDecimal totalProteinG = BigDecimal.ZERO;
    }
}
