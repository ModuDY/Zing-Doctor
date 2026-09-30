package com.zing.doctor.icu.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 临床时间线聚合响应
 *
 * <p>区分完整返回与部分失败：当部分模块查询失败时，
 * dataStatus=PARTIAL 且 failedSources 列出失败模块，
 * 避免医生误认为该模块无记录。</p>
 */
@Data
public class TimelineResponse {

    /** 数据状态：OK / PARTIAL / UNKNOWN */
    private String dataStatus;

    /** 事件列表 */
    private List<TimelineEvent> events = new ArrayList<>();

    /** 查询失败的模块列表，如 ["SEPSIS_BUNDLE","PRONE"] */
    private List<String> failedSources = new ArrayList<>();

    public TimelineResponse() {}

    public static TimelineResponse ok(List<TimelineEvent> events) {
        TimelineResponse r = new TimelineResponse();
        r.setDataStatus("OK");
        r.setEvents(events != null ? events : new ArrayList<>());
        return r;
    }

    public static TimelineResponse partial(List<TimelineEvent> events, List<String> failedSources) {
        TimelineResponse r = new TimelineResponse();
        r.setDataStatus("PARTIAL");
        r.setEvents(events != null ? events : new ArrayList<>());
        r.setFailedSources(failedSources != null ? failedSources : new ArrayList<>());
        return r;
    }

    public static TimelineResponse unknown(String error) {
        TimelineResponse r = new TimelineResponse();
        r.setDataStatus("UNKNOWN");
        return r;
    }
}
