package com.zing.doctor.module.handover.dto;

import lombok.Data;

import java.util.List;

/**
 * 科室交班览表总览（班次区间 + 汇总统计 + 患者卡片列表）。
 */
@Data
public class HandoverOverview {

    /** 取数班次区间 */
    private ShiftRange shiftRange;

    /** 顶部风险/状态汇总条 */
    private Summary summary;

    /** 在科患者交班卡片（按床号排序） */
    private List<HandoverPatientCard> patients;

    @Data
    public static class Summary {
        /** 在科患者数 */
        private int totalPatients;
        /** 呼吸机人数 */
        private int ventilatorCount;
        /** CRRT 人数 */
        private int crrtCount;
        /** ECMO 人数 */
        private int ecmoCount;
        /** 在用升压药人数 */
        private int vasopressorCount;
        /** 发热人数 */
        private int feverCount;
        /** 检验异常人数 */
        private int abnormalLabCount;
        /** 脓毒性休克人数 */
        private int sepsisShockCount;
        /** 隔离人数 */
        private int isolationCount;
        /** 本班新入科人数 */
        private int newInCount;
        /** 本班出科人数 */
        private int dischargeCount;
        /** 已填写病情变化交班人数 */
        private int noteFilledCount;
    }
}
