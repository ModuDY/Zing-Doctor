package com.zing.doctor.module.sedation.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse.PatientHeader;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse.Window;
import com.zing.doctor.module.sedation.mapper.SedationAssessMapper;
import com.zing.doctor.module.sedation.service.SedationMonitorService;
import com.zing.doctor.module.sedation.support.SedationScoreParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 镇痛镇静监测分析实现：单患者一次批量查询 doc 四量表，Java 内存分组解析，杜绝 N+1。
 *
 * <p>RASS 为配对 CAM 的可评估性，查询开始时间比展示窗口前推
 * {@link SedationScoreParser#DEFAULT_RASS_PAIR_LOOKBACK_HOURS} 小时；前推段 RASS 仅用于配对，不展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SedationMonitorServiceImpl implements SedationMonitorService {

    private final SedationAssessMapper sedationAssessMapper;

    /** 时间窗上下限（小时），与前端快捷项一致，自定义不得超过 30 天。 */
    private static final int DEFAULT_HOURS = 24;
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 720;

    private static final List<String> DOC_CODES = Arrays.asList(
            SedationScoreParser.DOC_NRS,
            SedationScoreParser.DOC_CPOT,
            SedationScoreParser.DOC_RASS,
            SedationScoreParser.DOC_CAM);

    @Override
    public SedationMonitorResponse analyze(WorkbenchPatient patient, Integer hours) {
        return analyze(patient, hours, null, null);
    }

    @Override
    public SedationMonitorResponse analyze(WorkbenchPatient patient, Integer hours,
                                          String startIso, String endIso) {
        int lookback = SedationScoreParser.DEFAULT_RASS_PAIR_LOOKBACK_HOURS;
        boolean hasStart = startIso != null && !startIso.trim().isEmpty();
        boolean hasEnd = endIso != null && !endIso.trim().isEmpty();
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException("自定义起止时间需同时提供开始与结束");
        }

        int windowHours;
        LocalDateTime end;
        LocalDateTime displayStart;
        if (hasStart) {
            LocalDateTime s = parseIso(startIso.trim(), "开始");
            LocalDateTime e = parseIso(endIso.trim(), "结束");
            if (!e.isAfter(s)) {
                throw new IllegalArgumentException("结束时间必须晚于开始时间");
            }
            long minutes = Duration.between(s, e).toMinutes();
            if (minutes > (long) MAX_HOURS * 60) {
                throw new IllegalArgumentException("自定义时间范围不能超过 30 天");
            }
            displayStart = s;
            end = e;
            windowHours = (int) Math.max(MIN_HOURS, Math.round(minutes / 60.0));
        } else {
            windowHours = normalizeHours(hours);
            end = LocalDateTime.now();
            displayStart = end.minusHours(windowHours);
        }
        LocalDateTime queryStart = displayStart.minusHours(lookback);

        SedationMonitorResponse resp = new SedationMonitorResponse();
        resp.setPatient(toHeader(patient));
        Window w = new Window();
        w.setHours(windowHours);
        w.setStartTime(displayStart.toString());
        w.setEndTime(end.toString());
        w.setRassLookbackHours(lookback);
        resp.setWindow(w);
        applyNotes(resp);

        try {
            List<Map<String, Object>> rows = sedationAssessMapper.selectDocScoreRecords(
                    patient.getPatientId(), DOC_CODES, queryStart.toString(), end.toString());
            SedationScoreParser.Result parsed =
                    SedationScoreParser.parse(rows, displayStart, end, lookback);
            resp.setNrs(parsed.nrs);
            resp.setCpot(parsed.cpot);
            resp.setRass(parsed.rass);
            resp.setCam(parsed.cam);
            // 解析后重新挂上口径说明（parser 新建的 block 未带 note）
            applyNotes(resp);
            resp.setDataStatus("FOUND");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("镇痛镇静监测查询失败(patientId={})", patient.getPatientId(), e);
            markUnknown(resp);
        }
        return resp;
    }

    private static LocalDateTime parseIso(String value, String label) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + "时间格式错误，应为 yyyy-MM-ddTHH:mm:ss");
        }
    }

    private int normalizeHours(Integer hours) {
        if (hours == null || hours <= 0) return DEFAULT_HOURS;
        if (hours < MIN_HOURS) return MIN_HOURS;
        return Math.min(hours, MAX_HOURS);
    }

    private PatientHeader toHeader(WorkbenchPatient p) {
        PatientHeader h = new PatientHeader();
        h.setPatientId(p.getPatientId());
        h.setInHospitalNo(p.getInHospitalNo());
        h.setName(p.getName());
        h.setAge(p.getAge());
        h.setGender(p.getGender());
        h.setDepartCode(p.getDepartCode());
        h.setDepartName(p.getDepartName());
        h.setWardName(p.getWardName());
        h.setBedNo(p.getBedNo());
        h.setInDepartmentTime(p.getInDepartmentTime() == null ? null : p.getInDepartmentTime().toString());
        h.setIcuDays(p.getIcuDays());
        h.setVentilated(p.getVentilated());
        return h;
    }

    private void applyNotes(SedationMonitorResponse r) {
        r.getNrs().setNote("NRS 数字疼痛评分（0-10），适用于能自述的患者；数据来自重症系统正式评分文书。");
        r.getCpot().setNote("CPOT 重症疼痛观察工具（0-8），适用于无法自述 / 机械通气患者；面部、体动、肌紧张、"
                + "通气顺应性(或发声)四维度各 0-2，条目选项中文以护理部 / 厂商确认为准。");
        r.getRass().setNote("RASS 镇静躁动评分（-5～+4）；深镇静可能是治疗目标，本期仅展示事实与趋势，不判定「过度镇静」。");
        r.getCam().setNote("CAM-ICU 谵妄评估为分类结果，不求均值；RASS -4/-5 时按流程无法评估，"
                + "源系统无「无法评估」选项，该镇静深度下录得的阴性已标注「建议核查」，源结果原样保留。");
    }

    private void markUnknown(SedationMonitorResponse r) {
        r.setDataStatus("UNKNOWN");
        r.setFailedSources(Arrays.asList("NRS", "CPOT", "RASS", "CAM"));
        r.getNrs().setDataStatus("UNKNOWN");
        r.getCpot().setDataStatus("UNKNOWN");
        r.getRass().setDataStatus("UNKNOWN");
        r.getCam().setDataStatus("UNKNOWN");
        r.getNrs().setNote("数据暂不可用");
        r.getCpot().setNote("数据暂不可用");
        r.getRass().setNote("数据暂不可用");
        r.getCam().setNote("数据暂不可用");
    }
}
