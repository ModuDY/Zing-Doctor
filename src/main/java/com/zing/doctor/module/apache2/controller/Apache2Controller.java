package com.zing.doctor.module.apache2.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.service.Apache2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * APACHE II 评分 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/apache2")
public class Apache2Controller {

    @Autowired
    private Apache2Service apache2Service;

    /**
     * 主任总览：统计数据 + 患者评分列表
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam String departCode,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            Map<String, Object> result = apache2Service.getOverview(departCode, startTime, endTime);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取APACHE II总览失败: departCode={}, startTime={}, endTime={}", departCode, startTime, endTime, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 患者评分记录列表
     */
    @GetMapping("/patient/{inHospitalNo}/records")
    public Result<List<Apache2ScoreRecord>> getPatientRecords(@PathVariable String inHospitalNo) {
        try {
            List<Apache2ScoreRecord> list = apache2Service.getPatientRecords(inHospitalNo);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取患者评分记录失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 单条评分记录详情
     */
    @GetMapping("/record/{id}")
    public Result<Apache2ScoreRecord> getRecordById(@PathVariable Long id) {
        try {
            Apache2ScoreRecord record = apache2Service.getRecordById(id);
            return Result.ok(record);
        } catch (Exception e) {
            log.error("获取评分记录详情失败: id={}", id, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 下载/在线预览评分文书 PDF（disposition=inline 浏览器内预览，attachment 下载）
     */
    @GetMapping("/record/{id}/pdf")
    public void downloadRecordPdf(@PathVariable Long id,
                                  @RequestParam(defaultValue = "inline") String disposition,
                                  HttpServletResponse response) {
        try {
            Apache2ScoreRecord rec = apache2Service.getRecordPdf(id);
            if (rec == null || rec.getPdfData() == null || rec.getPdfData().isEmpty()) {
                response.setStatus(404);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":404,\"message\":\"该记录尚未生成PDF文书\"}");
                return;
            }
            byte[] bytes = Base64.getDecoder().decode(rec.getPdfData());
            String name = (rec.getPdfName() != null && !rec.getPdfName().isEmpty()) ? rec.getPdfName() : "APACHE2评分.pdf";
            String encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            String disp = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", disp + "; filename*=UTF-8''" + encodedName);
            response.setContentLength(bytes.length);
            try (OutputStream os = response.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }
        } catch (Exception e) {
            log.error("输出评分PDF失败: id={}", id, e);
            response.setStatus(500);
        }
    }

    /**
     * 评分主体保存后，单独补传评分文书 PDF 大字段（解耦：大字段慢/失败不影响评分落库）
     */
    @PostMapping("/record/{id}/pdf")
    public Result<Boolean> attachRecordPdf(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object data = body == null ? null : body.get("pdfData");
            Object name = body == null ? null : body.get("pdfName");
            String pdfData = data == null ? null : data.toString();
            String pdfName = name == null ? null : name.toString();
            boolean ok = apache2Service.attachPdf(id, pdfData, pdfName);
            return Result.ok(ok);
        } catch (Exception e) {
            log.error("补传评分PDF失败: id={}", id, e);
            return Result.fail("文书补传失败: " + e.getMessage());
        }
    }

    /**
     * 自动获取患者生理数据（预填充）
     */
    @GetMapping("/auto-fetch/{patientId}")
    public Result<Map<String, Object>> autoFetchPhysiologyData(
            @PathVariable String patientId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            Map<String, Object> result = apache2Service.autoFetchPhysiologyData(patientId, startTime, endTime);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("自动获取生理数据失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 计算APACHE II评分
     */
    @PostMapping("/calculate")
    public Result<Map<String, Object>> calculateScore(@RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = apache2Service.calculateScore(params);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("计算APACHE II评分失败", e);
            return Result.fail("计算失败: " + e.getMessage());
        }
    }

    /**
     * 保存评分记录
     */
    @PostMapping("/save")
    public Result<Apache2ScoreRecord> saveScore(@RequestBody Apache2ScoreRecord record) {
        try {
            Apache2ScoreRecord saved = apache2Service.saveScore(record);
            return Result.ok(saved);
        } catch (Exception e) {
            log.error("保存评分记录失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 删除评分记录（逻辑删除）
     */
    @DeleteMapping("/record/{id}")
    public Result<Boolean> deleteScore(@PathVariable Long id, @RequestParam(required = false) String operator) {
        try {
            boolean success = apache2Service.deleteScore(id, operator);
            return Result.ok(success);
        } catch (Exception e) {
            log.error("删除评分记录失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置列表
     */
    @GetMapping("/config/{configType}")
    public Result<List<Map<String, Object>>> getConfigList(@PathVariable String configType) {
        try {
            List<Map<String, Object>> list = apache2Service.getConfigList(configType);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取配置列表失败: configType={}", configType, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 根据住院号获取患者基本信息
     */
    @GetMapping("/patient-info/{inHospitalNo}")
    public Result<Map<String, Object>> getPatientInfo(@PathVariable String inHospitalNo) {
        try {
            Map<String, Object> info = apache2Service.getPatientInfo(inHospitalNo);
            return Result.ok(info);
        } catch (Exception e) {
            log.error("获取患者信息失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单指标的历史趋势数据（用于来源弹窗趋势图）
     */
    @GetMapping("/metric-trend/{patientId}")
    public Result<List<Map<String, Object>>> getMetricTrend(
            @PathVariable String patientId,
            @RequestParam String metricKey,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            List<Map<String, Object>> trend = apache2Service.getMetricTrend(patientId, metricKey, startTime, endTime);
            return Result.ok(trend);
        } catch (Exception e) {
            log.error("获取指标趋势数据失败: patientId={}, metricKey={}", patientId, metricKey, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 氧合三要素（FiO2 / PaO2 / A-aDO2）趋势：供评分页「来源」弹窗展示三值及各自曲线。
     * 返回结构与 /metric-trend 一致的 {time,value} 列表，另含 worstTime 供图上高亮“评分选中的那一管”。
     */
    @GetMapping("/oxygen-trend/{patientId}")
    public Result<Map<String, Object>> getOxygenTrend(@PathVariable String patientId,
                                                      @RequestParam String startTime,
                                                      @RequestParam String endTime) {
        try {
            return Result.ok(apache2Service.getOxygenTrend(patientId, startTime, endTime));
        } catch (Exception e) {
            log.error("获取氧合趋势失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 拉取患者在重症系统已评估的 GCS 记录（Z_ICU_GCS，供评分时同步选择）
     */
    @GetMapping("/patient/{patientId}/gcs-records")
    public Result<List<Map<String, Object>>> listSystemGcs(@PathVariable String patientId) {
        try {
            return Result.ok(apache2Service.listSystemGcs(patientId));
        } catch (Exception e) {
            log.error("获取重症系统GCS记录失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发：为在科且入科超过指定小时数（默认24h）、尚无评分记录的患者自动生成 APACHE II 初评（幂等）。
     * departCode 为空则全科室。
     */
    @PostMapping("/auto-generate")
    public Result<Map<String, Object>> autoGenerate(
            @RequestParam(required = false) String departCode,
            @RequestParam(required = false, defaultValue = "24") Integer overHours) {
        try {
            int hours = (overHours == null || overHours <= 0) ? 24 : overHours;
            Map<String, Object> result = apache2Service.autoGenerateScores(departCode, hours);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("自动生成APACHE II初评失败: departCode={}", departCode, e);
            return Result.fail("自动生成失败: " + e.getMessage());
        }
    }
}
