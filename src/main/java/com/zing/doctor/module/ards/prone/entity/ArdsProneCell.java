package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ARDS 俯卧位记录单元格值表（参数 × 时点）。
 *
 * <p>每个值都带来源与采集时间：自动采集（auto）、检验同步（lis）、系统计算（calc）、手工录入（man）。
 * 采集窗口内无数据一律置空转手工，不沿用历史值。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_prone_cell\"")
public class ArdsProneCell {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recordId;

    private Integer tpIndex;

    /** 参数编码（见 ArdsProneDict） */
    private String paramKey;

    private String valueText;

    private BigDecimal valueNum;

    /** auto / lis / calc / man */
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;

    /** 人工修正原因（覆盖自动值时必填） */
    private String manualReason;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
