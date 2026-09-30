package com.zing.doctor.icu.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.TimelineEvent;
import com.zing.doctor.icu.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 临床时间线接口
 *
 * <p>聚合患者的关键临床事件：评分、抗感染决策/复评、脓毒症集束化、
 * 俯卧位、查房记录等，按时间倒序展示。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @GetMapping("/patients/{patientId}/timeline")
    public Result<List<TimelineEvent>> getTimeline(@PathVariable String patientId) {
        List<TimelineEvent> events = timelineService.getPatientTimeline(patientId);
        return Result.ok(events);
    }
}
