package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抗感染 48～72 小时复评任务与复评留痕。
 *
 * <p>复评是决策后的独立任务，不改写原始决策快照。第一版只记录临床决策支持结果，
 * 不直接执行或修改 HIS 医嘱。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_abx_reassessment\"")
public class AntibioticReassessment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long decisionRecordId;
    private String patientId;
    private String patientNo;
    private String inHospitalNo;
    private String departCode;
    /** 复评计时起点；优先首次实际给药时间，缺失时使用决策采纳时间。 */
    private LocalDateTime treatmentStartTime;
    /** 建议复评窗口开放时间（计时起点后 48 小时）。 */
    private LocalDateTime reviewOpenTime;
    /** 建议复评窗口截止时间（计时起点后 72 小时）。 */
    private LocalDateTime reviewDueTime;
    /** FIRST_ADMINISTRATION / DECISION_ACCEPTED，防止把两个时间概念混为一谈。 */
    private String timeSource;
    private LocalDateTime reviewTime;

    /** PENDING / COMPLETED / SKIPPED / VOID */
    private String reviewStatus;

    private String cultureSummary;
    private String clinicalResponse;
    private String pctTrend;

    /** CONTINUE / DE_ESCALATE / ESCALATE / SWITCH / STOP / OTHER */
    private String decisionAction;

    private String doctorDecision;
    private String doctorId;
    private String doctorName;
    private String remark;
    private Integer voidFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 展示状态不落库：PENDING 是任务生命周期状态，SCHEDULED/OVERDUE 是时间窗口状态。
     * 这样既兼容既有数据和查询，又不会依赖定时任务修改状态。
     */
    public String getDisplayStatus() {
        if (!"PENDING".equals(reviewStatus)) {
            return reviewStatus;
        }
        LocalDateTime now = LocalDateTime.now();
        if (reviewOpenTime != null && now.isBefore(reviewOpenTime)) {
            return "SCHEDULED";
        }
        if (reviewDueTime != null && now.isAfter(reviewDueTime)) {
            return "OVERDUE";
        }
        return "PENDING";
    }
}
