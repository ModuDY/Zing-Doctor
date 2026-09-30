package com.zing.doctor.module.round.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.round.entity.RoundRecord;
import com.zing.doctor.module.round.service.RoundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 医生查房记录接口
 *
 * <p>查房记录是患者日级的诊疗计划记录，与班次级的交班表分开。
 * 同一患者同一天只保留一份，保存时自动覆盖更新。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/round")
@RequiredArgsConstructor
public class RoundController {

    private final RoundService roundService;

    /**
     * 获取指定患者指定日期的查房记录。
     * roundDate 不传时默认今天。
     */
    @GetMapping("/record")
    public Result<RoundRecord> getRecord(
            @RequestParam String patientId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate roundDate) {
        if (roundDate == null) roundDate = LocalDate.now();
        RoundRecord record = roundService.getByDate(patientId, roundDate);
        return Result.ok(record);
    }

    /**
     * 获取指定患者的历史查房记录列表（按日期倒序，最多30条）。
     */
    @GetMapping("/history")
    public Result<List<RoundRecord>> listHistory(@RequestParam String patientId) {
        List<RoundRecord> list = roundService.listHistory(patientId);
        return Result.ok(list);
    }

    /**
     * 保存查房记录。同一患者同一天只保留一份，已存在则更新。
     */
    @PostMapping("/save")
    public Result<RoundRecord> save(@RequestBody RoundRecord record) {
        if (record.getPatientId() == null || record.getPatientId().isEmpty()) {
            return Result.fail("patientId 不能为空");
        }
        RoundRecord saved = roundService.save(record);
        return Result.ok(saved);
    }
}
