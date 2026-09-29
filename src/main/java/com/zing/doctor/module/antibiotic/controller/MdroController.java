package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.antibiotic.dto.MdroBacteriaRank;
import com.zing.doctor.module.antibiotic.dto.MdroOverviewView;
import com.zing.doctor.module.antibiotic.dto.MdroPatientDetail;
import com.zing.doctor.module.antibiotic.entity.MdroConfig;
import com.zing.doctor.module.antibiotic.service.MdroConfigService;
import com.zing.doctor.module.antibiotic.service.MdroStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第四维度：细菌培养检出监测与院感防控 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/antibiotic/mdro")
@RequiredArgsConstructor
public class MdroController {

    private final MdroStatsService mdroStatsService;
    private final MdroConfigService mdroConfigService;

    // ==================================================================
    // 统计分析接口
    // ==================================================================

    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> departments() {
        try {
            List<Map<String, Object>> list = mdroStatsService.getAllDepartments();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[MDRO] 科室列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/overview")
    public Result<MdroOverviewView> overview(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 总览仪表盘统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            MdroOverviewView view = mdroStatsService.getOverview(startTime, endTime, departCode);
            return Result.ok(view);
        } catch (Exception e) {
            log.error("[MDRO] 总览仪表盘统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/bacteria-rank")
    public Result<List<MdroBacteriaRank>> bacteriaRank(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 菌株排名统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<MdroBacteriaRank> ranks = mdroStatsService.getBacteriaRankTop20(startTime, endTime, departCode);
            return Result.ok(ranks);
        } catch (Exception e) {
            log.error("[MDRO] 菌株排名统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/monthly-trend")
    public Result<List<MdroOverviewView.MdroTrendPoint>> monthlyTrend(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 月度趋势统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<MdroOverviewView.MdroTrendPoint> trend = mdroStatsService.getMonthlyTrend(startTime, endTime, departCode);
            return Result.ok(trend);
        } catch (Exception e) {
            log.error("[MDRO] 月度趋势统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/patients")
    public Result<List<MdroPatientDetail>> patients(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 患者明细统计: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<MdroPatientDetail> details = mdroStatsService.getPatientDetails(startTime, endTime, departCode);
            return Result.ok(details);
        } catch (Exception e) {
            log.error("[MDRO] 患者明细统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/patient/{inHospitalNo}")
    public Result<MdroPatientDetail> patientDetail(
            @PathVariable String inHospitalNo,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        log.info("[MDRO] 患者明细: inHospitalNo={}, {} ~ {}", inHospitalNo, startTime, endTime);
        try {
            MdroPatientDetail detail = mdroStatsService.getPatientDetail(inHospitalNo, startTime, endTime);
            return Result.ok(detail);
        } catch (Exception e) {
            log.error("[MDRO] 患者明细查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/specimen-distribution")
    public Result<List<Map<String, Object>>> specimenDistribution(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 标本类型分布: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<Map<String, Object>> distribution = mdroStatsService.getSpecimenDistribution(startTime, endTime, departCode);
            return Result.ok(distribution);
        } catch (Exception e) {
            log.error("[MDRO] 标本类型分布统计失败", e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/high-risk-alerts")
    public Result<List<MdroPatientDetail>> highRiskAlerts(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String departCode) {
        log.info("[MDRO] 高风险细菌预警: {} ~ {}, departCode={}", startTime, endTime, departCode);
        try {
            List<MdroPatientDetail> alerts = mdroStatsService.getHighRiskAlerts(startTime, endTime, departCode);
            return Result.ok(alerts);
        } catch (Exception e) {
            log.error("[MDRO] 高风险细菌预警查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    // ==================================================================
    // 配置管理接口
    // ==================================================================

    @GetMapping("/config/list")
    public Result<List<MdroConfig>> configList() {
        try {
            List<MdroConfig> list = mdroConfigService.listAllActive();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[MDRO] 配置列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/by-type")
    public Result<List<MdroConfig>> configByType(@RequestParam String configType) {
        try {
            List<MdroConfig> list = mdroConfigService.listByConfigType(configType);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[MDRO] 按类型查询配置失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/by-class")
    public Result<List<MdroConfig>> configByClass(@RequestParam String bacteriaClass) {
        try {
            List<MdroConfig> list = mdroConfigService.listByBacteriaClass(bacteriaClass);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[MDRO] 按分类查询配置失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/high-risk")
    public Result<List<MdroConfig>> configHighRisk() {
        try {
            List<MdroConfig> list = mdroConfigService.listHighRiskBacteria();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("[MDRO] 高风险细菌列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/config/classes")
    public Result<List<String>> configClasses() {
        try {
            List<String> classes = mdroConfigService.listAllBacteriaClasses();
            return Result.ok(classes);
        } catch (Exception e) {
            log.error("[MDRO] 获取细菌分类失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/add")
    public Result<Boolean> addConfig(@RequestBody MdroConfig config) {
        log.info("[MDRO] 新增配置: {}", config.getBacteriaName());
        try {
            boolean ok = mdroConfigService.addConfig(config);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[MDRO] 新增配置失败", e);
            return Result.fail("新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/update")
    public Result<Boolean> updateConfig(@RequestBody MdroConfig config) {
        log.info("[MDRO] 修改配置: id={}, bacteriaName={}", config.getId(), config.getBacteriaName());
        try {
            boolean ok = mdroConfigService.updateConfig(config);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[MDRO] 修改配置失败", e);
            return Result.fail("修改失败: " + e.getMessage());
        }
    }

    @PostMapping("/config/toggle")
    public Result<Boolean> toggleConfig(
            @RequestParam Long id,
            @RequestParam Integer status) {
        log.info("[MDRO] 切换状态: id={}, status={}", id, status);
        try {
            boolean ok = mdroConfigService.toggleStatus(id, status);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("[MDRO] 切换状态失败", e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }
}
