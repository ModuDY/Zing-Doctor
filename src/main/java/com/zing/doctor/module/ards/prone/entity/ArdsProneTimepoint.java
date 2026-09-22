package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ARDS 俯卧位记录时点表。
 *
 * <p>删除时点走软删除（status=0）：已填写的历史数据保留可查。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_prone_timepoint\"")
public class ArdsProneTimepoint {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recordId;

    /** 时点序号：0 = T0 翻身前 */
    private Integer tpIndex;

    private String tpLabel;

    /** 相对开始时间的偏移（分钟） */
    private Integer offsetMinutes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planTime;

    /** pending 待采集 / done 已采集 / closed 已关闭（中性状态，不做告警） */
    private String collectStatus;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
