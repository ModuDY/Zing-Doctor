package com.zing.doctor.module.handover.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.handover.dto.HandoverOverview;
import com.zing.doctor.module.handover.dto.HandoverPatientDetail;
import com.zing.doctor.module.handover.entity.HandoverNote;
import com.zing.doctor.module.handover.service.HandoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 医生交班览表 Controller（pageCode: handover-board）。
 */
@Slf4j
@RestController
@RequestMapping("/api/handover")
public class HandoverController {

    @Autowired
    private HandoverService handoverService;

    /** 科室交班总览：默认取最近一个已封板全天班次，支持 shiftDate 选择指定日期 */
    @GetMapping("/ward-overview")
    public Result<HandoverOverview> wardOverview(
            @RequestParam(required = false) String departCode,
            @RequestParam(required = false) String shiftDate) {
        try {
            return Result.ok(handoverService.getWardOverview(departCode, shiftDate));
        } catch (Exception e) {
            log.error("加载交班总览失败: departCode={}, shiftDate={}", departCode, shiftDate, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 单患者交班详情（抽屉懒加载） */
    @GetMapping("/patient-detail")
    public Result<HandoverPatientDetail> patientDetail(
            @RequestParam String inHospitalNo,
            @RequestParam(required = false) String shiftDate) {
        try {
            return Result.ok(handoverService.getPatientDetail(inHospitalNo, shiftDate));
        } catch (Exception e) {
            log.error("加载患者交班详情失败: inHospitalNo={}, shiftDate={}", inHospitalNo, shiftDate, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 保存/更新本班病情变化（一患者一班一条） */
    @PostMapping("/save-note")
    public Result<HandoverNote> saveNote(@RequestBody HandoverNote note) {
        try {
            return Result.ok(handoverService.saveNote(note));
        } catch (Exception e) {
            log.error("保存交班病情变化失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 删除一条交班记录（逻辑删除） */
    @PostMapping("/delete-note")
    public Result<Boolean> deleteNote(@RequestParam Long id) {
        try {
            boolean ok = handoverService.deleteNote(id);
            return ok ? Result.ok(true) : Result.fail("记录不存在");
        } catch (Exception e) {
            log.error("删除交班记录失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 启用科室下拉 */
    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> departments() {
        try {
            return Result.ok(handoverService.listDepartments());
        } catch (Exception e) {
            log.error("加载科室列表失败", e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 患者出科统计列表 */
    @GetMapping("/discharge-list")
    public Result<List<Map<String, Object>>> dischargeList(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(handoverService.listDischargedPatients(startTime, endTime, departCode));
        } catch (Exception e) {
            log.error("加载患者出科统计失败: startTime={}, endTime={}, departCode={}", startTime, endTime, departCode, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 导出患者出科统计CSV */
    @GetMapping("/discharge-export")
    public void dischargeExport(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String departCode,
            javax.servlet.http.HttpServletResponse response) {
        try {
            byte[] csv = handoverService.exportDischargedCsv(startTime, endTime, departCode);
            String filename = "患者出科统计_" + startTime.substring(0, 10).replace("-", "") + "_" + endTime.substring(0, 10).replace("-", "") + ".csv";
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"");
            response.setContentLength(csv.length);
            response.getOutputStream().write(csv);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("导出患者出科统计失败: startTime={}, endTime={}, departCode={}", startTime, endTime, departCode, e);
        }
    }
}
