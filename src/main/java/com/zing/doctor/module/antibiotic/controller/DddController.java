package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.DepartScopeAsserts;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.antibiotic.dto.DddDrugRank;
import com.zing.doctor.module.antibiotic.dto.DddOverviewView;
import com.zing.doctor.module.antibiotic.dto.DddPatientDetail;
import com.zing.doctor.module.antibiotic.entity.DddConfig;
import com.zing.doctor.module.antibiotic.service.DddConfigService;
import com.zing.doctor.module.antibiotic.service.DddStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 第三维度：抗菌药物使用强度（DDD）分析 Controller。
 *
 * <p><b>科室收敛（security-audit 2026-10：patient-endpoint-no-depart-scope）</b>：
 * 总览/患者列表按请求的 departCode 过 {@link UserDepartScopeService#resolveQueryDepart}，
 * 患者明细按患者所属科室收敛；配置类端点（/config/*）由 AdminWriteInterceptor 保护，不在此处重复。
 */
@Slf4j
@RestController
@RequestMapping("/api/antibiotic/ddd")
@RequiredArgsConstructor
public class DddController {

    private final DddStatsService dddStatsService;
    private final DddConfigService dddConfigService;
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;

    /** 患者级科室收敛：按住院号定位患者后过 resolveQueryDepart */
    private void assertPatientAccessByNo(HttpServletRequest request, String inHospitalNo) {
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            throw new BizException(404, "患者不存在：" + inHospitalNo);
        }
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            throw new BizException(404, "患者不存在：" + inHospitalNo);
        }
        DepartScopeAsserts.assertPatientDepart(departScopeService, request, patient);
    }

    // ==================================================================
    // 统计分析接口
    // ==================================================================

    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> departments() {
        try {
            List<Map<String, Object>> list = dddStatsService.getAllDepartments();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[DDD] 科室列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/overview")
    public Result<DddOverviewView> overview(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode,
            HttpServletRequest request) {
        log.info("[DDD] 总览仪表盘统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            // 科室收敛：普通账号必须指定其授权科室，越权 403
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            DddOverviewView view = dddStatsService.getOverview(startTime, endTime, allowed);
            return Result.ok(view);
        } catch (Exception e) {
            log.error("[DDD] 总览仪表盘统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/drug-rank")
    public Result<List<DddDrugRank>> drugRank(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[DDD] 药品排名统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<DddDrugRank> ranks = dddStatsService.getDrugRankTop20(startTime, endTime, departCode);
            return Result.ok(ranks);
        } catch (Exception e) {
            log.error("[DDD] 药品排名统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/monthly-trend")
    public Result<List<DddOverviewView.DddTrendPoint>> monthlyTrend(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[DDD] 月度趋势统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<DddOverviewView.DddTrendPoint> trend = dddStatsService.getMonthlyTrend(startTime, endTime, departCode);
            return Result.ok(trend);
        } catch (Exception e) {
            log.error("[DDD] 月度趋势统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/patients")
    public Result<List<DddPatientDetail>> patients(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode,
            HttpServletRequest request) {
        log.info("[DDD] 患者明细统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            // 科室收敛：服务端实际拉全院后按科室过滤，边界必须在这里定死
            String allowed = DepartScopeAsserts.scopeDepart(departScopeService, request, departCode);
            List<DddPatientDetail> details = dddStatsService.getPatientDetails(startTime, endTime, allowed);
            return Result.ok(details);
        } catch (Exception e) {
            log.error("[DDD] 患者明细统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/patient/{inHospitalNo}")
    public Result<DddPatientDetail> patientDetail(
            @PathVariable String inHospitalNo,
            @RequestParam String startTime,
            @RequestParam String endTime,
            HttpServletRequest request) {
        log.info("[DDD] 患者明细: inHospitalNo={}, {} ~ {}", inHospitalNo, startTime, endTime);
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            DddPatientDetail detail = dddStatsService.getPatientDetail(inHospitalNo, startTime, endTime);
            return Result.ok(detail);
        } catch (Exception e) {
            log.error("[DDD] 患者明细查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    // ==================================================================
    // DDD 配置管理接口
    // ==================================================================

    @GetMapping("/config/list")
    public Result<List<DddConfig>> configList() {
        try {
            List<DddConfig> list = dddConfigService.listAllActive();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[DDD] 配置列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/by-class")
    public Result<List<DddConfig>> configByClass(@RequestParam String drugClass) {
        try {
            List<DddConfig> list = dddConfigService.listByDrugClass(drugClass);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[DDD] 按分类查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/classes")
    public Result<List<String>> configClasses() {
        try {
            List<String> classes = dddConfigService.listAllDrugClasses();
            return Result.ok(classes);
        } catch (Exception e) {
            log.error("[DDD] 获取分类失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/count-by-class")
    public Result<Map<String, Long>> countByClass() {
        try {
            Map<String, Long> count = dddConfigService.countByDrugClass();
            return Result.ok(count);
        } catch (Exception e) {
            log.error("[DDD] 统计数量失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/add")
    public Result<Boolean> addConfig(@RequestBody DddConfig config) {
        log.info("[DDD] 新增配置: {}", config.getDrugName());
        try {
            boolean ok = dddConfigService.addConfig(config);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[DDD] 新增配置失败", e);
            return Result.fail("新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/update")
    public Result<Boolean> updateConfig(@RequestBody DddConfig config) {
        log.info("[DDD] 修改配置: id={}, drugName={}", config.getId(), config.getDrugName());
        try {
            boolean ok = dddConfigService.updateConfig(config);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[DDD] 修改配置失败", e);
            return Result.fail("修改失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/toggle")
    public Result<Boolean> toggleConfig(
            @RequestParam Long id,
            @RequestParam Integer status) {
        log.info("[DDD] 切换状态: id={}, status={}", id, status);
        try {
            boolean ok = dddConfigService.toggleStatus(id, status);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[DDD] 切换状态失败", e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }
}
