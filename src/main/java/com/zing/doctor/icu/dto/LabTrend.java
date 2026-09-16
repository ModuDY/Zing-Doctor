package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 关键检验指标趋势（决策页趋势图用）。
 * 每个指标一条趋势，series 按时间升序。
 */
@Data
public class LabTrend implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指标名（PCT / WBC / CRP / 乳酸 / 肌酐） */
    private String itemName;

    /** 趋势点（时间升序） */
    private List<TrendPoint> series = new ArrayList<>();

    /** 参考低限（来自 lis_item_low_value 或解析 lis_item_limit，无则 null） */
    private BigDecimal lowLimit;

    /** 参考高限（来自 lis_item_height_value 或解析 lis_item_limit，无则 null） */
    private BigDecimal highLimit;
}
