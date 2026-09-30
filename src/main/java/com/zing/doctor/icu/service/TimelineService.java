package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.TimelineEvent;

import java.util.List;

public interface TimelineService {

    /**
     * 获取指定患者的临床时间线事件（按时间倒序，最多50条）。
     */
    List<TimelineEvent> getPatientTimeline(String patientId);
}
