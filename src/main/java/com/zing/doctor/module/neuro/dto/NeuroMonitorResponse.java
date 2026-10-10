package com.zing.doctor.module.neuro.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单患者「急性脑损伤 / 神经功能监测」聚合响应（只读、零录入）。
 *
 * <p>各区块相互独立、各自带 dataStatus（六态子集）：
 * <ul>
 *   <li>{@link #diagnosis}：急性脑损伤 ICD-10 识别（I60-I63 / S06，排除后遗症 I69、个人史 Z86.7、鉴别诊断）；</li>
 *   <li>{@link #gcs}：GCS 正式文书，E/V/M 分项，插管言语不可评记 T（如 3T），不反哺 SOFA；</li>
 *   <li>{@link #consciousness}：高频神志（1-10 类别码，仅 1→5 主线用于昏迷加深提示）；</li>
 *   <li>{@link #pupils}：双侧瞳孔 mm / 对光反射，展示左右差等事实，不启用未审批阈值预警；</li>
 *   <li>{@link #icp}：颅内压 mmHg；脑损伤患者无记录标 NOT_RECORDED，非脑损伤患者标 NOT_APPLICABLE。</li>
 * </ul>
 * 查询失败标 UNKNOWN，绝不伪装成「无数据」。
 */
@Data
public class NeuroMonitorResponse {

    private PatientHeader patient;
    private Window window;

    private DiagnosisBlock diagnosis = new DiagnosisBlock();
    private GcsBlock gcs = new GcsBlock();
    private ConsciousnessBlock consciousness = new ConsciousnessBlock();
    private PupilBlock pupils = new PupilBlock();
    private IcpBlock icp = new IcpBlock();

    /** FOUND（全部成功）/ PARTIAL（部分源失败）/ UNKNOWN（整体失败） */
    private String dataStatus = "FOUND";
    private List<String> failedSources = new ArrayList<>();

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
        private Boolean ventilated;
    }

    @Data
    public static class Window {
        private Integer hours;
        private String startTime;
        private String endTime;
    }

    /** 急性脑损伤诊断识别区块。 */
    @Data
    public static class DiagnosisBlock {
        /** FOUND（检索成功）/ UNKNOWN（查询失败） */
        private String dataStatus = "FOUND";
        /** 是否命中急性脑损伤（I60-I63 / S06） */
        private boolean brainInjury;
        /** 主类别码：SAH / ICH / OTHER_NONTRAUMATIC / ISCHEMIC / TRAUMATIC */
        private String category;
        /** 主类别中文名 */
        private String categoryName;
        /** 命中的急性脑损伤诊断（去重后，按时间倒序） */
        private List<Diagnosis> items = new ArrayList<>();
        private String note;
    }

    @Data
    public static class Diagnosis {
        private String diagCode;
        private String diagName;
        private String diagTypeName;
        private String diagKindName;
        private String diagTime;
        /** 该条所属类别码 */
        private String category;
        private String categoryName;
    }

    /** 神志区块（oi_shenzhi，类别码 1-10）。 */
    @Data
    public static class ConsciousnessBlock {
        /** FOUND / NOT_RECORDED / UNKNOWN */
        private String dataStatus = "NOT_RECORDED";
        private ConsciousPoint latest;
        private List<ConsciousPoint> points = new ArrayList<>();
        private int count;
        private String note;
    }

    @Data
    public static class ConsciousPoint {
        private String time;
        private String staffName;
        /** 原始类别码（1-10） */
        private Integer code;
        /** 映射后的神志状态文本 */
        private String text;
        /** 是否属于 1神清→2嗜睡→3浅昏迷→4中昏迷→5深昏迷 主线（仅主线参与昏迷加深提示） */
        private boolean mainline;
        private String raw;
    }

    /** 双侧瞳孔区块（oi_tkz 左 / oi_tky 右，值 mm/反射）。 */
    @Data
    public static class PupilBlock {
        /** FOUND / NOT_RECORDED / PARTIAL（仅一侧有记录）/ UNKNOWN */
        private String dataStatus = "NOT_RECORDED";
        private PupilPoint latest;
        private List<PupilPoint> points = new ArrayList<>();
        private int count;
        private String note;
    }

    @Data
    public static class PupilPoint {
        private String time;
        private String staffName;
        private Double leftMm;
        private String leftReflex;
        private String leftRaw;
        private Double rightMm;
        private String rightReflex;
        private String rightRaw;
        /** 左右直径差（mm），任一侧非数值时为 null */
        private Double diffMm;
        /** 任一侧为不规则 / 无法观察等不可测状态 */
        private boolean unobservable;
    }

    /** 颅内压区块（oi_lny，mmHg）。 */
    @Data
    public static class IcpBlock {
        /** FOUND / NOT_RECORDED（脑损伤但无记录）/ NOT_APPLICABLE（非脑损伤）/ UNKNOWN */
        private String dataStatus = "NOT_RECORDED";
        private IcpPoint latest;
        private List<IcpPoint> points = new ArrayList<>();
        private int count;
        private Integer min;
        private Integer max;
        private String note;
    }

    @Data
    public static class IcpPoint {
        private String time;
        private String staffName;
        private Integer value;
        private String raw;
    }

    /** GCS 正式评分区块（Z_ICU_GCS）。 */
    @Data
    public static class GcsBlock {
        /** FOUND / NOT_RECORDED / PARTIAL（有总分但分项缺失）/ UNKNOWN */
        private String dataStatus = "NOT_RECORDED";
        private GcsPoint latest;
        private List<GcsPoint> points = new ArrayList<>();
        private int count;
        private String note;
    }

    @Data
    public static class GcsPoint {
        private String time;
        private String staffName;
        /** 睁眼 E（1-4） */
        private Integer e;
        /** 言语 V（1-5）；插管 / 气切时为 null */
        private Integer v;
        /** 运动 M（1-6） */
        private Integer m;
        /** 可计算总分：插管时为 E+M（标注 T），否则 E+V+M */
        private Integer total;
        /** 展示文本，如 15 / 3T */
        private String display;
        /** 是否插管 / 气切（言语不可评） */
        private boolean intubated;
        private String rawScore;
        private String rawJson;
        private Boolean audited;
    }
}
