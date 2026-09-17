package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ARDS 俯卧位通气治疗记录主表。
 *
 * <p>一次俯卧位疗程一条记录；37 项参数 × N 个时点的值落在 {@code ards_prone_cell}，
 * 时点定义落在 {@code ards_prone_timepoint}。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"ards_prone_record\"")
public class ArdsProneRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 记录编号：PP-yyyyMMdd-序号 */
    private String recordNo;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String sex;

    private String age;

    private String bedCode;

    private String departCode;

    private String diagnosis;

    /** ARDS 分级：轻度 / 中度 / 重度 */
    private String ardsGrade;

    /** 入院日期 yyyy-MM-dd */
    private String admitDate;

    /** 俯卧位天数，如「第 2 天」 */
    private String proneDay;

    /** 俯卧位次数 */
    private Integer proneTimes;

    /** 经管医师 */
    private String attendingDoctor;

    /** 记录日期 yyyy-MM-dd */
    private String recordDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 持续时长（分钟），按 start/end 自动计算 */
    private Integer durationMin;

    /** APACHE II 评分（是否显示由参数 ARDS_PRONE_APACHE2_SHOW 控制） */
    private String apache2Score;

    /** 并发症勾选（JSON 数组） */
    private String complicationJson;

    /** 并发症详细描述及处理 */
    private String complicationDesc;

    /** 终止类型：none / reach / emergency */
    private String stopType;

    /** 终止原因详情 */
    private String stopDetail;

    private String remark;

    /** 记录护士签名（实名） */
    private String nurseSign;

    /** 记录医师签名 */
    private String doctorSign;

    /** 上级医师签名 */
    private String seniorSign;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signTime;

    /** draft 填写中 / submitted 已提交 */
    private String recordStatus;

    /** 文书 PDF Base64（不含 data: 前缀）；默认不随行返回 */
    @TableField(value = "\"pdf_data\"", select = false)
    private String pdfData;

    private String pdfName;

    /** 归档状态：0 未归档 1 已归档 */
    private Integer archiveStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archiveTime;

    /** HIS 归档文档号 */
    private String archiveDocNo;

    private String filePath;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
