package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控月度汇总宽表（一行一个指标，1-12 月横排）。
 *
 * <p>页面与 xlsx 导出直读此表，一次查询出全年 12 个月，不需要前端拼 12 次请求。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_monthly_report\"")
public class QualityMonthlyReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** year 为达梦保留字，显式加引号 */
    @TableField("\"year\"")
    private Integer year;

    private String indexCode;
    private String indexName;
    private String domainCode;
    private String unit;
    private String valueType;
    private String departCode;

    private BigDecimal m01;
    private BigDecimal m02;
    private BigDecimal m03;
    private BigDecimal m04;
    private BigDecimal m05;
    private BigDecimal m06;
    private BigDecimal m07;
    private BigDecimal m08;
    private BigDecimal m09;
    private BigDecimal m10;
    private BigDecimal m11;
    private BigDecimal m12;

    private BigDecimal q1;
    private BigDecimal q2;
    private BigDecimal q3;
    private BigDecimal q4;

    /** 全年合计（数类求和；率类为空） */
    private BigDecimal yearTotal;
    /** 全年平均（率类为月度均值；数类为月均） */
    private BigDecimal yearAvg;

    private Integer maxMonth;
    private Integer minMonth;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 月度值数组（非数据库字段）：m01..m12，供前端与导出直接遍历。 */
    @TableField(exist = false)
    private BigDecimal[] months;
}
