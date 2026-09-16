package com.zing.doctor.module.handover.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单患者交班详情（抽屉展开时懒加载，复用既有单患者查询）。
 * 各明细为 Map 列表，key 为查询别名，直接供前端分区展示。
 */
@Data
public class HandoverPatientDetail {

    /** 取数班次区间 */
    private ShiftRange shiftRange;

    /** 患者基础信息 */
    private Map<String, Object> patient;

    /** 班内监护记录（倒序，含 CVP/MAP/呼吸参数等全部项目） */
    private List<Map<String, Object>> vitals;

    /** 班内出入量明细（倒序） */
    private List<Map<String, Object>> ioRecords;

    /** 出入量汇总（和总览卡片逻辑一致） */
    private IoSummary ioSummary;

    /** 出量项目分组汇总（按项目名称分组累加） */
    private List<Map<String, Object>> outputItemSummary;

    /** 出入量汇总内部类 */
    @Data
    public static class IoSummary {
        /** 总入量（非药品入量 + 药品入量） */
        private Double intakeTotal;
        /** 总出量（config_io_item.io_type='o' 所有记录） */
        private Double outputTotal;
        /** 尿量（item_code='ii_nl'） */
        private Double urineTotal;
        /** 是否有导尿管 */
        private Boolean urineCatheter;
        /** 平衡（入量 - 出量） */
        private Double balanceTotal;
    }

    /** 班内检验结果（倒序，异常项带 abnormal=true 标记） */
    private List<Map<String, Object>> labs;

    /** 血管活性/升压药医嘱 */
    private List<Map<String, Object>> vasopressors;

    /** 当前抗菌药物医嘱 */
    private List<Map<String, Object>> antibiotics;

    /** 最新 CRRT 记录 */
    private Map<String, Object> crrt;

    /** 诊断记录 */
    private List<Map<String, Object>> diagnoses;

    /** 微生物培养/药敏结果 */
    private List<Map<String, Object>> microbiology;

    /** 本班手工交班病情变化 */
    private Map<String, Object> note;
}
