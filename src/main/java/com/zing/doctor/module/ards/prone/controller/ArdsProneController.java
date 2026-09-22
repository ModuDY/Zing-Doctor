package com.zing.doctor.module.ards.prone.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.ards.prone.dict.ArdsProneDict;
import com.zing.doctor.module.ards.prone.dto.ArdsProneCellSaveItem;
import com.zing.doctor.module.ards.prone.dto.ArdsProneRecordView;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCellLog;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTpTpl;
import com.zing.doctor.module.ards.prone.entity.ArdsProneConfig;
import com.zing.doctor.module.ards.prone.service.ArdsProneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ARDS 俯卧位通气治疗记录 Controller。
 *
 * <p>归档不在此处：复用现有 {@code /api/archive/push?biz=ARDS_PRONE&id=xxx}，
 * 与 APACHE II、SOFA 评分走同一归档接口与传参，仅 doc_code 不同。
 */
@Slf4j
@RestController
@RequestMapping("/api/ards-prone")
public class ArdsProneController {

    @Autowired
    private ArdsProneService ardsProneService;

    /** 参数字典（37 项）：填写页、打印文书、回传文书共用同一份 */
    @GetMapping("/dict")
    public Result<List<ArdsProneDict.ParamDef>> dict() {
        return Result.ok(ArdsProneDict.params());
    }

    /** 列表页结果指标摘要（批量）：P/F 首末值与趋势、最低 ΔP、已填项数 */
    @PostMapping("/summary")
    public Result<List<Map<String, Object>>> summary(@RequestBody List<Long> ids) {
        try {
            return Result.ok(ardsProneService.summary(ids));
        } catch (Exception e) {
            log.error("ARDS 俯卧位列表摘要统计失败: ids={}", ids == null ? null : ids.size(), e);
            return Result.fail("统计失败: " + e.getMessage());
        }
    }

    /** APACHE II 是否显示（参数控制，默认显示、全院统一） */
    @GetMapping("/apache2-show")
    public Result<Boolean> apache2Show() {
        return Result.ok(ardsProneService.apache2Show());
    }

    /** 归档回传是否启用（参数控制，默认关闭；关闭后前端隐藏归档按钮与状态列） */
    @GetMapping("/archive-enabled")
    public Result<Boolean> archiveEnabled() {
        return Result.ok(ardsProneService.archiveEnabled());
    }

    // ---------------------------------------------------------- 采集映射配置

    /** 映射配置列表（含停用项，供配置页管理）：configType / configKey 可空 */
    @GetMapping("/map")
    public Result<List<ArdsProneConfig>> listMap(@RequestParam(required = false) String configType,
                                                 @RequestParam(required = false) String configKey) {
        try {
            return Result.ok(ardsProneService.listConfig(configType, configKey));
        } catch (Exception e) {
            log.error("ARDS 俯卧位映射配置查询失败", e);
            return Result.fail(configErrMsg(e, "获取"));
        }
    }

    /** 映射配置保存（id 为空新增，否则更新）；保存后取数立即生效 */
    @PostMapping("/map/save")
    public Result<Boolean> saveMap(@RequestBody Map<String, Object> body) {
        try {
            return Result.ok(ardsProneService.saveConfig(body));
        } catch (Exception e) {
            log.error("ARDS 俯卧位映射配置保存失败", e);
            return Result.fail(configErrMsg(e, "保存"));
        }
    }

    /** 映射配置删除 */
    @PostMapping("/map/delete")
    public Result<Boolean> deleteMap(@RequestParam Long id) {
        try {
            return Result.ok(ardsProneService.deleteConfig(id));
        } catch (Exception e) {
            log.error("ARDS 俯卧位映射配置删除失败: id={}", id, e);
            return Result.fail(configErrMsg(e, "删除"));
        }
    }

    /** 映射配置启用 / 停用 */
    @PostMapping("/map/toggle")
    public Result<Boolean> toggleMap(@RequestParam Long id, @RequestParam Integer status) {
        try {
            return Result.ok(ardsProneService.toggleConfig(id, status));
        } catch (Exception e) {
            log.error("ARDS 俯卧位映射配置启停失败: id={}", id, e);
            return Result.fail(configErrMsg(e, "操作"));
        }
    }

    /** 一键用内置关键字生成映射配置（幂等，返回新增条数） */
    @PostMapping("/map/seed")
    public Result<Integer> seedMap() {
        try {
            return Result.ok(ardsProneService.seedConfig());
        } catch (Exception e) {
            log.error("ARDS 俯卧位映射配置一键生成失败", e);
            return Result.fail(configErrMsg(e, "生成"));
        }
    }

    /** 数据元候选：type=observe 监护字典 / lis 近 7 天检验项目 */
    @GetMapping("/map/candidates")
    public Result<List<Map<String, Object>>> mapCandidates(
            @RequestParam(required = false, defaultValue = "observe") String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) Integer limit) {
        try {
            return Result.ok(ardsProneService.candidates(type, keyword, patientId, limit));
        } catch (Exception e) {
            log.error("ARDS 俯卧位数据元候选查询失败: type={}", type, e);
            String msg = String.valueOf(e.getMessage() == null ? "" : e.getMessage());
            if (msg.contains("无效的表或视图名") || msg.contains("表或视图不存在")) {
                return Result.fail("候选数据源表不存在（config_observe_item / patient_info_lis_item），请手工填写匹配值");
            }
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /** 试采（dry-run，不落库）：返回每项命中值与来源，供配置页核对映射 */
    @PostMapping("/map/preview")
    public Result<Map<String, Object>> previewCollect(@RequestParam Long recordId,
                                                      @RequestParam Integer tpIndex) {
        try {
            return Result.ok(ardsProneService.previewCollect(recordId, tpIndex));
        } catch (Exception e) {
            log.error("ARDS 俯卧位试采失败: recordId={}, tpIndex={}", recordId, tpIndex, e);
            return Result.fail("试采失败: " + e.getMessage());
        }
    }

    /**
     * 映射配置异常转可读提示：配置表未建时给出可执行指引，避免现场直接看到达梦原始堆栈。
     * 采集侧读规则失败会自动回退内置关键字，不受该表是否存在的影响。
     */
    private String configErrMsg(Exception e, String action) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("无效的表或视图名") || msg.contains("表或视图不存在")) {
            return "数据映射配置表 config_prone_item 未初始化：请先执行 sql/22_ards_prone_config.sql 建表后重试"
                    + "（采集功能不受影响，当前自动回退内置关键字）";
        }
        return action + "失败: " + msg;
    }

    /** 记录列表：按住院号或科室 */
    @GetMapping("/list")
    public Result<List<ArdsProneRecord>> list(@RequestParam(required = false) String inHospitalNo,
                                              @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(ardsProneService.list(inHospitalNo, departCode));
        } catch (Exception e) {
            log.error("ARDS 俯卧位记录列表失败: inHospitalNo={}, departCode={}", inHospitalNo, departCode, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 填写页视图 */
    @GetMapping("/get")
    public Result<ArdsProneRecordView> get(@RequestParam Long id) {
        try {
            ArdsProneRecordView view = ardsProneService.get(id);
            if (view == null) {
                return Result.fail("记录不存在");
            }
            return Result.ok(view);
        } catch (Exception e) {
            log.error("ARDS 俯卧位记录详情失败: id={}", id, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 新建一次俯卧位疗程记录（外链模式可带住院流水号与入科时间，用于精确定位患者内部 id） */
    @PostMapping("/create")
    public Result<ArdsProneRecord> create(@RequestParam String inHospitalNo,
                                          @RequestParam(required = false) String patientId,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) String inHospitalSerialNo,
                                          @RequestParam(required = false) String inDepartTime) {
        try {
            return Result.ok(ardsProneService.create(inHospitalNo, patientId, startTime,
                    inHospitalSerialNo, inDepartTime));
        } catch (Exception e) {
            log.error("ARDS 俯卧位记录创建失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("创建失败: " + e.getMessage());
        }
    }

    /**
     * 患者解析（外链参数 → patient_info.id）：新建弹窗自动填充患者 ID / 诊断 / 入院日期。
     *
     * <p>定位顺序：住院流水号（仅当 patient_info 存在该列）→ 住院号 + 入科时间 → 最近一次入科；
     * 查不到返回 found=false，不报错，允许手工填写。
     */
    @GetMapping("/patient/lookup")
    public Result<Map<String, Object>> lookupPatient(@RequestParam String inHospitalNo,
                                                     @RequestParam(required = false) String inHospitalSerialNo,
                                                     @RequestParam(required = false) String inDepartTime) {
        try {
            return Result.ok(ardsProneService.lookupPatient(inHospitalNo, inHospitalSerialNo, inDepartTime));
        } catch (Exception e) {
            log.error("ARDS 俯卧位患者解析失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 保存记录头（患者信息、并发症、终止指征、签名等） */
    @PostMapping("/record/save")
    public Result<ArdsProneRecord> saveRecord(@RequestBody ArdsProneRecord record) {
        try {
            return Result.ok(ardsProneService.saveRecord(record));
        } catch (Exception e) {
            log.error("ARDS 俯卧位记录保存失败: id={}", record == null ? null : record.getId(), e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 批量保存单元格值（覆盖自动值须填原因，全程留痕） */
    @PostMapping("/cells/save")
    public Result<Integer> saveCells(@RequestParam Long recordId,
                                     @RequestBody List<ArdsProneCellSaveItem> items) {
        try {
            return Result.ok(ardsProneService.saveCells(recordId, items));
        } catch (Exception e) {
            log.error("ARDS 俯卧位单元格保存失败: recordId={}", recordId, e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 自动采集某个时点（只填空值，窗口内无数据置空转手工） */
    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestParam Long recordId, @RequestParam Integer tpIndex, @RequestParam(required = false) Boolean forceRefresh) {
        try {
            return Result.ok(ardsProneService.collect(recordId, tpIndex, Boolean.TRUE.equals(forceRefresh)));
        } catch (Exception e) {
            log.error("ARDS 俯卧位自动采集失败: recordId={}, tpIndex={}", recordId, tpIndex, e);
            return Result.fail("采集失败: " + e.getMessage());
        }
    }

    /** 更正留痕 */
    @GetMapping("/logs")
    public Result<List<ArdsProneCellLog>> logs(@RequestParam Long recordId) {
        try {
            return Result.ok(ardsProneService.logs(recordId));
        } catch (Exception e) {
            log.error("ARDS 俯卧位留痕查询失败: recordId={}", recordId, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 补传文书 PDF（与记录主体保存解耦） */
    @PostMapping("/record/{id}/pdf")
    public Result<Boolean> attachPdf(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String pdfData = body == null ? null : body.get("pdfData");
            String pdfName = body == null ? null : body.get("pdfName");
            boolean ok = ardsProneService.attachPdf(id, pdfData, pdfName);
            return ok ? Result.ok(true) : Result.fail("记录不存在或数据为空");
        } catch (Exception e) {
            log.error("ARDS 俯卧位文书归档失败: id={}", id, e);
            return Result.fail("归档失败: " + e.getMessage());
        }
    }

    /** 逻辑删除记录 */
    @PostMapping("/record/delete")
    public Result<Boolean> delete(@RequestParam Long id) {
        try {
            boolean ok = ardsProneService.delete(id);
            return ok ? Result.ok(true) : Result.fail("记录不存在");
        } catch (Exception e) {
            log.error("ARDS 俯卧位记录删除失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------- 时点配置

    @PostMapping("/tp/add")
    public Result<List<ArdsProneTimepoint>> addTimepoint(@RequestParam Long recordId,
                                                         @RequestParam(required = false) String label,
                                                         @RequestParam(required = false) Integer offsetMinutes) {
        try {
            return Result.ok(ardsProneService.addTimepoint(recordId, label, offsetMinutes));
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点新增失败: recordId={}", recordId, e);
            return Result.fail("新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/tp/update")
    public Result<List<ArdsProneTimepoint>> updateTimepoint(@RequestParam Long tpId,
                                                            @RequestParam(required = false) String label,
                                                            @RequestParam(required = false) Integer offsetMinutes) {
        try {
            return Result.ok(ardsProneService.updateTimepoint(tpId, label, offsetMinutes));
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点修改失败: tpId={}", tpId, e);
            return Result.fail("修改失败: " + e.getMessage());
        }
    }

    /** 删除时点：软删除，已填数据保留可查 */
    @PostMapping("/tp/delete")
    public Result<List<ArdsProneTimepoint>> deleteTimepoint(@RequestParam Long tpId) {
        try {
            return Result.ok(ardsProneService.deleteTimepoint(tpId));
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点删除失败: tpId={}", tpId, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 恢复默认时点模板 */
    @PostMapping("/tp/reset")
    public Result<List<ArdsProneTimepoint>> resetTimepoints(@RequestParam Long recordId) {
        try {
            return Result.ok(ardsProneService.resetTimepoints(recordId));
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点重置失败: recordId={}", recordId, e);
            return Result.fail("重置失败: " + e.getMessage());
        }
    }

    @GetMapping("/tpl")
    public Result<List<ArdsProneTpTpl>> tpl(@RequestParam(required = false) String departCode) {
        try {
            return Result.ok(ardsProneService.tpl(departCode));
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点模板查询失败: departCode={}", departCode, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/tpl/save")
    public Result<Boolean> saveTpl(@RequestParam(required = false) String departCode,
                                   @RequestBody List<ArdsProneTpTpl> items) {
        try {
            ardsProneService.saveTpl(departCode, items);
            return Result.ok(true);
        } catch (Exception e) {
            log.error("ARDS 俯卧位时点模板保存失败: departCode={}", departCode, e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 打印前校验：缺项清单（缺项阻断打印，但不做告警） */
    @GetMapping("/check-print")
    public Result<Map<String, Object>> checkPrint(@RequestParam Long id) {
        try {
            ArdsProneRecordView view = ardsProneService.get(id);
            Map<String, Object> res = new HashMap<>();
            if (view == null) {
                res.put("ok", false);
                res.put("missing", java.util.Collections.singletonList("记录不存在"));
                return Result.ok(res);
            }
            boolean needApache = ardsProneService.apache2Show();
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (view.getRecord() != null && !org.springframework.util.StringUtils.hasText(view.getRecord().getPatientName())) {
                missing.add("患者姓名");
            }
            if (view.getRecord() != null && view.getRecord().getStartTime() == null) {
                missing.add("俯卧位开始时间");
            }
            if (needApache && view.getRecord() != null
                    && !org.springframework.util.StringUtils.hasText(view.getRecord().getApache2Score())) {
                missing.add("APACHE II 评分");
            }
            res.put("ok", missing.isEmpty());
            res.put("missing", missing);
            return Result.ok(res);
        } catch (Exception e) {
            log.error("ARDS 俯卧位打印校验失败: id={}", id, e);
            return Result.fail("校验失败: " + e.getMessage());
        }
    }
}
