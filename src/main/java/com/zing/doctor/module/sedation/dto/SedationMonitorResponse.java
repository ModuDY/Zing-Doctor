package com.zing.doctor.module.sedation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单患者「镇痛镇静监测分析」聚合响应（只读、零录入）。
 *
 * <p>四个量表各自独立成块，<b>NRS 与 CPOT 独立图层 / 独立坐标，不合并、不求混合均值</b>；
 * CAM 为分类事件，只做阳性 / 阴性 / 无法评估分类计数，不求均值。
 *
 * <p>每块都带 dataStatus（六态子集），查询失败标 UNKNOWN 而非伪装成「无数据」；
 * 顶层 failedSources 记录部分失败的数据源，前端据此提示「部分数据暂不可用」。
 */
@Data
public class SedationMonitorResponse {

    /** 患者头区（精简，仅展示与定位用） */
    private PatientHeader patient;
    /** 本次查询时间窗 */
    private Window window;

    /** NRS 数字疼痛评分（能自述患者，0-10） */
    private ScaleBlock nrs = new ScaleBlock();
    /** CPOT 重症疼痛观察工具（无法自述 / 机械通气患者，0-8） */
    private ScaleBlock cpot = new ScaleBlock();
    /** RASS 镇静躁动评分（-5~+4） */
    private ScaleBlock rass = new ScaleBlock();
    /** CAM-ICU 谵妄评估（分类） */
    private CamBlock cam = new CamBlock();

    /** 整体数据状态：FOUND（全部成功）/ PARTIAL（部分源失败）/ UNKNOWN（整体失败） */
    private String dataStatus = "FOUND";
    /** 部分失败的数据源标识，如 [NRS, CAM] */
    private List<String> failedSources = new ArrayList<>();

    /** 患者头区 */
    @Data
    public static class PatientHeader {
        private String patientId;
        private String inHospitalNo;
        private String name;
        private Integer age;
        private String gender;
        private String departCode;
        private String departName;
        private String wardName;
        private String bedNo;
        private String inDepartmentTime;
        private Long icuDays;
        /** 当前是否机械通气（决定疼痛评估更可能走 CPOT，仅提示不强制） */
        private Boolean ventilated;
    }

    /** 查询时间窗 */
    @Data
    public static class Window {
        /** 窗口小时数（24/72/168/720） */
        private Integer hours;
        private String startTime;
        private String endTime;
        /** RASS 为配对 CAM 额外前推的小时数 */
        private Integer rassLookbackHours;
    }

    /** 数值型量表区块（NRS / CPOT / RASS）。 */
    @Data
    public static class ScaleBlock {
        /**
         * 数据状态：FOUND / PARTIAL（有总分但分项缺失等）/ NOT_RECORDED（查询成功但无记录）
         * / NOT_APPLICABLE（该患者不适用，如清醒患者 CPOT）/ UNKNOWN（查询失败）。
         */
        private String dataStatus = "NOT_RECORDED";
        /** 最近一次评估点 */
        private SedationPoint latest;
        /** 窗口内全部评估点（升序，供趋势图） */
        private List<SedationPoint> points = new ArrayList<>();
        /** 记录数 */
        private int count;
        /** 窗口最小值（RASS 表示最深镇静程度；NRS/CPOT 表示最低疼痛分） */
        private Integer min;
        /** 窗口最大值 */
        private Integer max;
        /** 说明 / 口径提示 */
        private String note;
    }

    /** CAM-ICU 分类区块。 */
    @Data
    public static class CamBlock {
        /** 数据状态：FOUND / NOT_RECORDED / PARTIAL / UNKNOWN */
        private String dataStatus = "NOT_RECORDED";
        private List<CamPoint> points = new ArrayList<>();
        private int total;
        private int positive;
        private int negative;
        /** 深镇静下录得阴性、按流程应无法评估（建议核查）数 */
        private int utaSuspect;
        /** 缺可配对 RASS、可评估性不明数 */
        private int pairUnknown;
        /** 源值无法识别数 */
        private int unparsed;
        private String note;
    }
}
