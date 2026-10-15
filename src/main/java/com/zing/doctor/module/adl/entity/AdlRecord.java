package com.zing.doctor.module.adl.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ADL（Barthel 指数）手工录入。
 *
 * <p>源系统评分文书无 ADL 结构化数据（docs/26 连库结论：Z_ICU_GCS / Z_ICU_SCORE_NRS 之外
 * 无护士 ADL 文书），由护士 / 医生按 Barthel 十项手工录入，总分服务端计算：</p>
 * <p>进食 10 / 洗澡 5 / 修饰 5 / 穿衣 10 / 控制大便 10 / 控制小便 10 / 如厕 10 /
 * 床椅转移 15 / 平地行走 15 / 上下楼梯 10，满分 100。</p>
 * <p>分级（展示用，不做诊断）：≥60 良好（轻度依赖）/ 41~60 中度依赖 /
 * 20~40 重度依赖 / &lt;20 完全依赖。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_adl\"")
public class AdlRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    /** 评估时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime adlTime;

    /** 进食 0/5/10 */
    private Integer feeding;

    /** 洗澡 0/5 */
    private Integer bathing;

    /** 修饰 0/5 */
    private Integer grooming;

    /** 穿衣 0/5/10 */
    private Integer dressing;

    /** 控制大便 0/5/10 */
    private Integer bowels;

    /** 控制小便 0/5/10 */
    private Integer bladder;

    /** 如厕 0/5/10 */
    private Integer toilet;

    /** 床椅转移 0/5/10/15 */
    private Integer transfer;

    /** 平地行走 0/5/10/15 */
    private Integer mobility;

    /** 上下楼梯 0/5/10 */
    private Integer stairs;

    /** 总分 0~100，服务端按十项求和 */
    private Integer totalScore;

    private String remark;

    /** 状态：1正常 0已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
