package com.zing.doctor.module.handover.dto;

import lombok.Data;

/**
 * 交班取数班次区间（默认 = 最近一个已完整封板的全天班次）。
 */
@Data
public class ShiftRange {

    /** 班次开始时间（格式化字符串，前端直接展示） */
    private String startTime;

    /** 班次结束时间（封板时刻，开区间） */
    private String endTime;

    /** 服务器当前时间 */
    private String nowTime;

    /** 班次名称（来自 config_shift，默认"全天"） */
    private String shiftName;

    /** 区间来源：config=按班次配置解析 / default=未配到回退默认 07:01-次日07:00 */
    private String source;
}
