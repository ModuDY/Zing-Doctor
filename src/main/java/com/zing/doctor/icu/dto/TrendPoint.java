package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 检验趋势点（含时间与数值）。
 */
@Data
public class TrendPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 时间（MM-dd HH:mm） */
    private String time;

    /** 数值（可参与趋势绘制） */
    private BigDecimal value;
}
