package com.zing.doctor.module.sofa.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.service.DepartScopeAsserts;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.sofa.dto.SofaAssessmentView;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.service.SofaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOFA（序贯器官衰竭评估）Controller。
 *
 * <p>所有 /api 请求需携带外链鉴权请求头（由 ExternalLinkInterceptor 校验）。
 * 取数窗口 startTime/endTime 缺省时按「过去 24 小时」处理。
 *
 * <p><b>科室收敛（security-audit 2026-10：patient-endpoint-no-depart-scope）</b>：
 * 患者级与总览端点统一过 {@link UserDepartScopeService#resolveQueryDepart} ——
 * 普通直连账号仅能访问其授权科室（越权 403 / 未选科室 400）；
 * 管理员全院；外链维持既有语义。配置类端点由 AdminWriteInterceptor 保护。
 */
@Slf4j
@RestController
@RequestMapping("/api/sofa")
public class SofaController {

    @Autowired
    private SofaService sofaService;

    @Autowired
    private IcuPatientService icuPatientService;

    @Autowired
    private UserDepartScopeService departScopeService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 患者级科室收敛：按 patientId 取患者所属科室后过 resolveQueryDepart，越权抛 403/400。
     */
    private void assertPatientAccess(HttpServletRequest request, String patientId) {
        com.zing.doctor.icu.dto.WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            throw new com.zing.doctor.common.BizException(404, "患者不存在：" + patientId);
        }
        DepartScopeAsserts.assertPatientDepart(departScopeService, request, patient);
    }

    /**
     * 患者级科室收敛：按住院号定位患者后过 resolveQueryDepart。
     */
    private void assertPatientAccessByNo(HttpServletRequest request, String inHospitalNo) {
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            throw new com.zing.doctor.common.BizException(404, "患者不存在：" + inHospitalNo);
        }
        assertPatientAccess(request, patientId);
    }

    /** 评分记录科室收敛：按记录所属患者收敛，防止按 id 跨科室读写 */
    private void assertRecordAccess(HttpServletRequest request, Long recordId) {
        SofaScoreRecord r = sofaService.getRecordPdf(recordId);
        if (r == null) {
            throw new com.zing.doctor.common.BizException(404, "记录不存在：" + recordId);
        }
        if (r.getPatientId() == null) {
            return; // 无患者关联的历史数据，按记录科室兜底跳过
        }
        assertPatientAccess(request, r.getPatientId());
    }

    /**
     * 单患者 SOFA 评估（自动取数 + 计分）。
     */
    @GetMapping("/assessment/{patientId}")
    public Result<SofaAssessmentView> assessment(@PathVariable String patientId,
                                                 @RequestParam(required = false) String startTime,
                                                 @RequestParam(required = false) String endTime,
                                                 HttpServletRequest request) {
        try {
            assertPatientAccess(request, patientId);
            String[] range = normalizeRange(startTime, endTime);
            return Result.ok(sofaService.getAssessment(patientId, range[0], range[1]));
        } catch (Exception e) {
            log.error("SOFA 评估失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 按住院号评估（ICU 外链用）。
     */
    @GetMapping("/assessment/by-no")
    public Result<SofaAssessmentView> assessmentByNo(@RequestParam("inHospitalNo") String inHospitalNo,
                                                     @RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime,
                                                     HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            String[] range = normalizeRange(startTime, endTime);
            return Result.ok(sofaService.getAssessmentByInHospitalNo(inHospitalNo, range[0], range[1]));
        } catch (Exception e) {
            log.error("SOFA 评估失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 保存/更新评分记录。未传分值时服务会按取数范围重新计算后落库。
     */
    @PostMapping("/record")
    public Result<SofaScoreRecord> saveRecord(@RequestBody SofaScoreRecord record,
                                              @RequestParam(required = false) String startTime,
                                              @RequestParam(required = false) String endTime,
                                              HttpServletRequest request) {
        try {
            if (record != null && record.getPatientId() != null) {
                assertPatientAccess(request, record.getPatientId());
            }
            String[] range = normalizeRange(startTime, endTime);
            return Result.ok(sofaService.saveRecord(record, range[0], range[1]));
        } catch (Exception e) {
            log.error("SOFA 评分保存失败: inHospitalNo={}", record == null ? null : record.getInHospitalNo(), e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 患者历史评分（倒序）。
     */
    @GetMapping("/records")
    public Result<List<SofaScoreRecord>> records(@RequestParam("inHospitalNo") String inHospitalNo,
                                                 HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            return Result.ok(sofaService.listByPatient(inHospitalNo));
        } catch (Exception e) {
            log.error("SOFA 历史查询失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 逻辑删除一条评分记录。
     */
    @PostMapping("/record/delete")
    public Result<Boolean> deleteRecord(@RequestParam Long id, HttpServletRequest request) {
        try {
            assertRecordAccess(request, id);
            boolean ok = sofaService.deleteRecord(id);
            return ok ? Result.ok(true) : Result.fail("记录不存在");
        } catch (Exception e) {
            log.error("SOFA 记录删除失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 科室总览：评分分布、平均分、ΔSOFA 恶化预警、患者列表。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) String departCode,
                                                @RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime,
                                                HttpServletRequest request) {
        try {
            // 科室收敛：普通账号必须显式指定其授权科室；空白/ALL → 400（不再默认全院）
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            String[] range = normalizeRange(startTime, endTime);
            return Result.ok(sofaService.getOverview(allowed, range[0], range[1]));
        } catch (Exception e) {
            log.error("SOFA 总览失败: departCode={}", departCode, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 配置读取（configType 为空返回全部启用项）。
     */
    @GetMapping({"/config", "/config/{configType}"})
    public Result<List<Map<String, Object>>> config(@PathVariable(required = false) String configType) {
        try {
            return Result.ok(sofaService.listConfig(configType));
        } catch (Exception e) {
            log.error("SOFA 配置查询失败: configType={}", configType, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 取某条评分记录的文书 PDF（Base64 + 文件名），供在线预览/下载。
     */
    @GetMapping("/record/{id}/pdf")
    public Result<Map<String, Object>> getRecordPdf(@PathVariable Long id, HttpServletRequest request) {
        try {
            assertRecordAccess(request, id);
            SofaScoreRecord r = sofaService.getRecordPdf(id);
            if (r == null) return Result.fail("记录不存在");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("pdfData", r.getPdfData());
            m.put("pdfName", r.getPdfName());
            return Result.ok(m);
        } catch (Exception e) {
            log.error("SOFA 文书下载失败: id={}", id, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 补传文书 PDF（与评分主体保存解耦：大字段慢/失败都不影响已落库的评分）。
     * <p>请求体：{@code {"pdfData":"<Base64，不含 data: 前缀>","pdfName":"xxx.pdf"}}
     */
    @PostMapping("/record/{id}/pdf")
    public Result<Boolean> attachPdf(@PathVariable Long id, @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        try {
            assertRecordAccess(request, id);
            String pdfData = body == null ? null : body.get("pdfData");
            String pdfName = body == null ? null : body.get("pdfName");
            boolean ok = sofaService.attachPdf(id, pdfData, pdfName);
            return ok ? Result.ok(true) : Result.fail("记录不存在或数据为空");
        } catch (Exception e) {
            log.error("SOFA 文书归档失败: id={}", id, e);
            return Result.fail("归档失败: " + e.getMessage());
        }
    }

    /**
     * 单指标趋势，供评分页「来源」弹窗趋势图。
     *
     * @param metricKey resp / coag / liver / cardio / neuro / renal
     */
    @GetMapping("/metric-trend/{patientId}")
    public Result<List<Map<String, Object>>> metricTrend(@PathVariable String patientId,
                                                         @RequestParam String metricKey,
                                                         @RequestParam(required = false) String startTime,
                                                         @RequestParam(required = false) String endTime,
                                                         HttpServletRequest request) {
        try {
            assertPatientAccess(request, patientId);
            String[] range = normalizeRange(startTime, endTime);
            return Result.ok(sofaService.getMetricTrend(patientId, metricKey, range[0], range[1]));
        } catch (Exception e) {
            log.error("SOFA 指标趋势失败: patientId={}, metricKey={}", patientId, metricKey, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 患者在重症系统（Z_ICU_GCS）已评估的 GCS 记录，供评分页 GCS 弹窗同步/选择。
     * <p>返回按评估时间倒序的 E/V/M 记录（含插管/未评全记录，由前端决定是否可带入）。
     */
    @GetMapping("/patient/{patientId}/gcs-records")
    public Result<List<Map<String, Object>>> gcsRecords(@PathVariable String patientId,
                                                        HttpServletRequest request) {
        try {
            assertPatientAccess(request, patientId);
            return Result.ok(sofaService.listSystemGcs(patientId));
        } catch (Exception e) {
            log.error("SOFA 获取重症系统GCS记录失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发自动初评（与每日定时任务同一套逻辑，幂等）。
     * <p>为在科超 {@code overHours}（默认 24）小时且当日无评分的患者生成初评。
     */
    @PostMapping("/auto-generate")
    public Result<Map<String, Object>> autoGenerate(@RequestParam(required = false) String departCode,
                                                    @RequestParam(required = false, defaultValue = "24") Integer overHours) {
        try {
            return Result.ok(sofaService.autoGenerateScores(departCode, overHours));
        } catch (Exception e) {
            log.error("SOFA 自动初评失败: departCode={}", departCode, e);
            return Result.fail("执行失败: " + e.getMessage());
        }
    }

    /**
     * 配置保存（id 为空新增，否则更新）。
     */
    @PostMapping("/config/save")
    public Result<Boolean> saveConfig(@RequestBody Map<String, Object> body) {
        try {
            return Result.ok(sofaService.saveConfig(body));
        } catch (Exception e) {
            log.error("SOFA 配置保存失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 配置删除（逻辑删除）。
     */
    @PostMapping("/config/delete")
    public Result<Boolean> deleteConfig(@RequestParam Long id) {
        try {
            return sofaService.deleteConfig(id) ? Result.ok(true) : Result.fail("配置不存在");
        } catch (Exception e) {
            log.error("SOFA 配置删除失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 配置启用/停用。
     */
    @PostMapping("/config/toggle")
    public Result<Boolean> toggleConfig(@RequestParam Long id, @RequestParam Integer status) {
        try {
            return sofaService.toggleConfig(id, status) ? Result.ok(true) : Result.fail("配置不存在");
        } catch (Exception e) {
            log.error("SOFA 配置启停失败: id={}", id, e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }

    /** 取数窗口缺省兜底：过去 24 小时 */
    private String[] normalizeRange(String startTime, String endTime) {
        LocalDateTime end = parse(startTime, endTime, true);
        LocalDateTime start = parse(startTime, endTime, false);
        if (end == null) end = LocalDateTime.now();
        if (start == null) start = end.minusHours(24);
        if (!start.isBefore(end)) {
            start = end.minusHours(24);
        }
        return new String[]{start.format(DT_FMT), end.format(DT_FMT)};
    }

    private LocalDateTime parse(String startTime, String endTime, boolean isEnd) {
        String s = isEnd ? endTime : startTime;
        if (s == null || s.trim().isEmpty()) return null;
        try {
            String t = s.trim();
            if (t.length() > 19) t = t.substring(0, 19);
            return LocalDateTime.parse(t.replace('T', ' '), DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
