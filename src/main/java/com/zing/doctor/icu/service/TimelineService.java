package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.TimelineResponse;

public interface TimelineService {

    /**
     * 获取指定患者的临床时间线事件（按时间倒序，最多50条）。
     * 返回 dataStatus 区分完整/部分失败/不可用，failedSources 列出查询失败的模块。
     */
    TimelineResponse getPatientTimeline(String patientId);
}
