package com.zing.doctor.module.handover.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.DepartScopeAsserts;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.handover.dto.HandoverOverview;
import com.zing.doctor.module.handover.dto.HandoverPatientDetail;
import com.zing.doctor.module.handover.entity.HandoverNote;
import com.zing.doctor.module.handover.mapper.HandoverNoteMapper;
import com.zing.doctor.module.handover.service.HandoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 医生交班览表 Controller（pageCode: handover-board）。
 *
 * <p><b>科室收敛（security-audit 2026-10：patient-endpoint-no-depart-scope）</b>：
 * 总览/出科列表/导出按请求的 departCode 过 {@link UserDepartScopeService#resolveQueryDepart}，
 * 患者级端点（按住院号 / 按记录 id）按患者所属科室收敛 —— 普通直连账号仅能访问其授权科室
 * （越权 403 / 未选科室 400）；管理员全院；外链维持既有语义。
 */
@Slf4j
@RestController
@RequestMapping("/api/handover")
public class HandoverController {

    @Autowired
    private HandoverService handoverService;

    @Autowired
    private HandoverNoteMapper handoverNoteMapper;

    @Autowired
    private IcuPatientService icuPatientService;

    @Autowired
    private UserDepartScopeService departScopeService;

    /** 患者级科室收敛：按 patientId 取患者所属科室后过 resolveQueryDepart */
    private void assertPatientAccess(HttpServletRequest request, String patientId) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            throw new BizException(404, "患者不存在：" + patientId);
        }
        DepartScopeAsserts.assertPatientDepart(departScopeService, request, patient);
    }

    /** 患者级科室收敛：按住院号定位患者后过 resolveQueryDepart */
    private void assertPatientAccessByNo(HttpServletRequest request, String inHospitalNo) {
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            throw new BizException(404, "患者不存在：" + inHospitalNo);
        }
        assertPatientAccess(request, patientId);
    }

    /** 交班记录级科室收敛：按记录所属患者收敛，防止按 id 跨科室删改 */
    private void assertNoteAccess(HttpServletRequest request, Long id) {
        HandoverNote note = handoverNoteMapper.selectById(id);
        if (note == null) {
            throw new BizException(404, "记录不存在：" + id);
        }
        if (note.getPatientId() != null) {
            assertPatientAccess(request, note.getPatientId());
        } else if (note.getInHospitalNo() != null) {
            assertPatientAccessByNo(request, note.getInHospitalNo());
        } else if (note.getDepartCode() != null) {
            DepartScopeAsserts.scopeDepart(departScopeService, request, note.getDepartCode());
        }
    }

    /** 科室交班总览：默认取最近一个已封板全天班次，支持 shiftDate 选择指定日期 */
    @GetMapping("/ward-overview")
    public Result<HandoverOverview> wardOverview(
            @RequestParam(required = false) String departCode,
            @RequestParam(required = false) String shiftDate,
            HttpServletRequest request) {
        try {
            // 科室收敛：普通账号必须指定其授权科室，越权 403
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            return Result.ok(handoverService.getWardOverview(allowed, shiftDate));
        } catch (Exception e) {
            log.error("加载交班总览失败: departCode={}, shiftDate={}", departCode, shiftDate, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 单患者交班详情（抽屉懒加载） */
    @GetMapping("/patient-detail")
    public Result<HandoverPatientDetail> patientDetail(
            @RequestParam String inHospitalNo,
            @RequestParam(required = false) String shiftDate,
            HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            return Result.ok(handoverService.getPatientDetail(inHospitalNo, shiftDate));
        } catch (Exception e) {
            log.error("加载患者交班详情失败: inHospitalNo={}, shiftDate={}", inHospitalNo, shiftDate, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 上一班次的手工交班记录（一键导入用），没有则返回 null */
    @GetMapping("/previous-note")
    public Result<HandoverNote> previousNote(
            @RequestParam String inHospitalNo,
            @RequestParam(required = false) String shiftDate,
            HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            return Result.ok(handoverService.getPreviousNote(inHospitalNo, shiftDate));
        } catch (Exception e) {
            log.error("加载上一班交班记录失败: inHospitalNo={}, shiftDate={}", inHospitalNo, shiftDate, e);
            return Result.fail("加载失败: " + e.getMessage());
        }
    }

    /** 保存/更新本班病情变化（一患者一班一条） */
    @PostMapping("/save-note")
    public Result<HandoverNote> saveNote(@RequestBody HandoverNote note, HttpServletRequest request) {
        try {
            if (note != null && note.getPatientId() != null) {
                assertPatientAccess(request, note.getPatientId());
            } else if (note != null && note.getInHospitalNo() != null) {
                assertPatientAccessByNo(request, note.getInHospitalNo());
            }
            return Result.ok(handoverService.saveNote(note));
        } catch (Exception e) {
            log.error("保存交班病情变化失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 删除一条交班记录（逻辑删除） */
    @PostMapping("/delete-note")
    public Result<Boolean> deleteNote(@RequestParam Long id, HttpServletRequest request) {
        try {
            assertNoteAccess(request, id);
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
            @RequestParam(required = false) String departCode,
            HttpServletRequest request) {
        try {
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            return Result.ok(handoverService.listDischargedPatients(startTime, endTime, allowed));
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
            HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response) {
        try {
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            byte[] csv = handoverService.exportDischargedCsv(startTime, endTime, allowed);
            String filename = "患者出科统计_" + startTime.substring(0, 10).replace("-", "") + "_" + endTime.substring(0, 10).replace("-", "") + ".csv";
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"");
            response.setContentLength(csv.length);
            response.getOutputStream().write(csv);
            response.getOutputStream().flush();
        } catch (BizException e) {
            // 越权/参数类异常交给全局处理器，避免导出接口静默返回空文件
            throw e;
        } catch (Exception e) {
            log.error("导出患者出科统计失败: startTime={}, endTime={}, departCode={}", startTime, endTime, departCode, e);
        }
    }
}
