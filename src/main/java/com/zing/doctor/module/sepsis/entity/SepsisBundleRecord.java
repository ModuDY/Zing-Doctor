package com.zing.doctor.module.sepsis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脓毒症休克集束化治疗记录表
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_sepsis_bundle_record\"")
public class SepsisBundleRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime diagnosisTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inDepartTime;

    /** 1H集束化是否完成：1是 0否 */
    @TableField("bundle_1h_completed")
    private Integer bundle1hCompleted;

    /** 3H集束化是否完成：1是 0否 */
    @TableField("bundle_3h_completed")
    private Integer bundle3hCompleted;

    /** 6H集束化是否完成：1是 0否 */
    @TableField("bundle_6h_completed")
    private Integer bundle6hCompleted;

    /** 1H项目详细数据（JSON） */
    @TableField("bundle_1h_data")
    private String bundle1hData;

    /** 3H项目详细数据（JSON） */
    @TableField("bundle_3h_data")
    private String bundle3hData;

    /** 6H项目详细数据（JSON） */
    @TableField("bundle_6h_data")
    private String bundle6hData;

    /** 感染部位 */
    private String infectionSite;

    /** 致病菌 */
    private String pathogen;

    /** 抗生素 */
    private String antibiotic;

    /** 液体复苏未达30ml/kg原因（JSON） */
    private String fluidReason;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    private String createBy;

    /**
     * 记录时间（业务字段：医生可回改，用于补录过去的评估）。
     *
     * <p>必须显式标注 {@code @JsonFormat}：Jackson 对 LocalDateTime 默认只认 ISO
     * （{@code 2026-09-16T14:30:00}），而前端统一提交 {@code yyyy-MM-dd HH:mm:ss}（空格分隔）。
     * 漏标会导致请求体在进 Controller 之前就反序列化失败，返回 400「提交的数据格式有误」，
     * Controller 里的 try-catch 拦不到 —— 与 HandoverNote 曾踩过的坑同源。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
