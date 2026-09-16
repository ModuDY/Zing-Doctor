package com.zing.doctor.module.apache2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * APACHE II 评分记录表
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"apache2_score_record\"")
public class Apache2ScoreRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scoreTime;

    /** 评分时机：admission入科时/24h/48h/custom自定义 */
    private String scoreType;

    /** A年龄评分 */
    private Integer ageScore;

    /** B慢性健康状况评分 */
    private Integer chronicScore;

    /** C GCS评分（15-GCS） */
    private Integer gcsScore;

    /** D急性生理评分（12项合计） */
    private Integer physiologyScore;

    /** APACHE II总分（A+B+C+D） */
    private Integer totalScore;

    /** 预计院内死亡率（%） */
    private BigDecimal mortalityRate;

    /** 12项急性生理数据JSON */
    private String apsData;

    /** 疾病分类：nonoperative非手术/operative手术/none以上都不是 */
    private String diagnosisType;

    /** 诊断权重 */
    private BigDecimal diagnosisWeight;

    /** 是否急诊手术：1是 0否 */
    private Integer emergencySurgery;

    /** 慢性健康状况：none/nonoperative/elective */
    private String chronicHealth;

    /** GCS明细（E/V/M） */
    private String gcsDetail;

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
