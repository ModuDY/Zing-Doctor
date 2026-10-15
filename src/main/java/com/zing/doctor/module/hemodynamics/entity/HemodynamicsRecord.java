package com.zing.doctor.module.hemodynamics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 血流动力学 / 心功能手工录入。
 *
 * <p>源系统没有心排量（CO）、CVP、EF 的结构化数据（docs/25 §3：血流动力学「心排量类不承诺」），
 * 本表由医生按测量设备（PiCCO / Swan-Ganz / 床旁超声）手工录入，服务端派生计算：
 * <ul>
 *   <li>MAP：未直接录入时按 MAP = DBP + (SBP − DBP) / 3 补算；</li>
 *   <li>SVR = (MAP − CVP) / CO × 80（dyn·s/cm⁵），三项齐全才计算，缺一项存空；</li>
 *   <li>EF（射血分数 %）来自心脏超声报告，仅展示，不做心衰结论。</li>
 * </ul>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_hemodynamics\"")
public class HemodynamicsRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    /** 测量时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime measureTime;

    /** 收缩压 mmHg（可选，用于补算 MAP） */
    private Double sbp;

    /** 舒张压 mmHg（可选） */
    private Double dbp;

    /** 平均动脉压 mmHg；缺省时按 SBP/DBP 补算 */
    private Double mapVal;

    /** 中心静脉压 mmHg（注意：若录的是 cmH2O 需先 ÷1.36 换算） */
    private Double cvp;

    /** 心输出量 L/min */
    private Double co;

    /** 外周血管阻力 dyn·s/cm⁵ = (MAP − CVP)/CO × 80，服务端计算 */
    private Double svr;

    /** 射血分数 %（心脏超声） */
    private Double ef;

    /** 测量方式：PiCCO / Swan-Ganz / 床旁超声 / 其他 */
    private String method;

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
