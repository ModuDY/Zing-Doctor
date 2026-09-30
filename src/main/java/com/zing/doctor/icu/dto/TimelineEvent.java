package com.zing.doctor.icu.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 临床时间线事件
 */
@Data
public class TimelineEvent {

    /** 事件时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    /** 事件类型：ADMISSION/SOFA/APACHE2/ABX_DECISION/ABX_REASSESSMENT/CULTURE/SEPSIS_BUNDLE/PRONE/ROUND */
    private String type;

    /** 事件标题 */
    private String title;

    /** 关键结果 */
    private String result;

    /** 数据来源：ICU_AUTO/DOCTOR_SCORE/SYSTEM_CALC/DOCTOR_INPUT */
    private String source;

    /** 关联详情（可选，如评分详情页路径） */
    private String detail;

    public TimelineEvent() {}

    public TimelineEvent(LocalDateTime time, String type, String title, String result, String source) {
        this.time = time;
        this.type = type;
        this.title = title;
        this.result = result;
        this.source = source;
    }
}
