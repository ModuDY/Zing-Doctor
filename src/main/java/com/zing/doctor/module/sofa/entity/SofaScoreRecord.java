package com.zing.doctor.module.sofa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SOFA 评分记录表（序贯器官衰竭评估，总分 0~24）。
 *
 * <p>6 个器官系统各 0~4 分：呼吸 / 凝血 / 肝 / 循环 / 神经 / 肾。
 * 各项原始值以 JSON 落库（resp_data 等），供医生回填复核与「来源」弹窗追溯。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_sofa_score_record\"")
public class SofaScoreRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** ICU 患者 ID（patient_info.id） */
    private String patientId;

    /** 住院号 */
    private String inHospitalNo;

    private String patientName;

    private String departCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scoreTime;

    /** 来源：auto 系统自动 / daily 每日 / custom 自定义 */
    private String scoreType;

    /** 呼吸 0-4（PaO2/FiO2） */
    private Integer respScore;
    /** 凝血 0-4（血小板） */
    private Integer coagScore;
    /** 肝 0-4（总胆红素） */
    private Integer liverScore;
    /** 循环 0-4（MAP / 血管活性药） */
    private Integer cardioScore;
    /** 神经 0-4（GCS） */
    private Integer neuroScore;
    /** 肾 0-4（肌酐 / 24h 尿量） */
    private Integer renalScore;

    /** SOFA 总分 0-24（6 项之和） */
    private Integer totalScore;

    /** 各项原始值 JSON（含取值时间，供复核） */
    private String respData;
    private String coagData;
    private String liverData;
    private String cardioData;
    private String neuroData;
    private String renalData;

    /** 血管活性药明细 JSON（药名 / 归一剂量 µg·kg⁻¹·min⁻¹ / 泵速 / 浓度 / 是否降级） */
    private String vasopressorJson;

    /** 取数窗口内尿量合计（mL） */
    private BigDecimal urineMl;

    /** GCS 总分（3-15）；未评全为 null */
    private Integer gcsTotal;

    /** GCS 明细（E/V/M） */
    private String gcsDetail;

    /** 是否有呼吸支持：1是 0否 */
    private Integer respiratorySupport;

    /** 本次剂量换算所用体重（kg） */
    private BigDecimal weightUsed;

    /** 体重来源：actual / ibw / default_age_sex / fallback70 */
    private String weightSource;

    /** 较上一次评分的总分变化（正数为恶化） */
    private Integer deltaSofa;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dataStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dataEndTime;

    private String remark;

    /** 归档状态：0待归档 1已归档（成功推送到院方文书归档接口后置 1） */
    private Integer archiveStatus;

    /** 最近一次归档成功时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archiveTime;

    /** 文书归档路径（按参数设置页的归档目录规则生成） */
    private String filePath;

    /** 评分文书PDF的Base64（不含data前缀）；默认查询不返回，避免列表臃肿 */
    @TableField(value = "\"pdf_data\"", select = false)
    private String pdfData;

    /** PDF文件名 */
    private String pdfName;

    /** 是否已生成PDF（非数据库字段，列表用） */
    @TableField(exist = false)
    private Integer hasPdf;

    /** 状态：1正常 0已删除 */
    private Integer status;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
