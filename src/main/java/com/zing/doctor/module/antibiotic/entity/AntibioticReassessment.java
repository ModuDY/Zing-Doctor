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
    private LocalDateTime reviewDueTime;
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
}
