package com.zing.doctor.module.neuro.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.PatientHeader;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.Window;
import com.zing.doctor.module.neuro.mapper.NeuroAssessMapper;
import com.zing.doctor.module.neuro.service.NeuroMonitorService;
import com.zing.doctor.module.neuro.support.NeuroScoreParser;
import com.zing.doctor.module.neuro.support.NeuroScoreParser.ObserveResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 神经功能监测实现：单患者一次批量查询诊断 + 监护评估（神志/瞳孔/ICP）+ GCS 文书，
 * Java 内存分组解析，杜绝 N+1；各数据源独立容错，失败标 UNKNOWN 而非伪装成无数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeuroMonitorServiceImpl implements NeuroMonitorService {

    private final NeuroAssessMapper neuroAssessMapper;

    private static final int DEFAULT_HOURS = 24;
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 720;

    private static final List<String> OBSERVE_ITEMS = Arrays.asList(
            NeuroScoreParser.ITEM_SHENZHI,
            NeuroScoreParser.ITEM_PUPIL_LEFT,
            NeuroScoreParser.ITEM_PUPIL_RIGHT,
            NeuroScoreParser.ITEM_ICP);

    private static final String SRC_DIAGNOSIS = "DIAGNOSIS";
    private static final String SRC_CONSCIOUSNESS = "CONSCIOUSNESS";
    private static final String SRC_PUPILS = "PUPILS";
    private static final String SRC_ICP = "ICP";
    private static final String SRC_GCS = "GCS";

    @Override
    public NeuroMonitorResponse analyze(WorkbenchPatient patient, Integer hours) {
        return analyze(patient, hours, null, null);
    }

    @Override
    public NeuroMonitorResponse analyze(WorkbenchPatient patient, Integer hours,
                                        String startIso, String endIso) {
        boolean hasStart = startIso != null && !startIso.trim().isEmpty();
        boolean hasEnd = endIso != null && !endIso.trim().isEmpty();
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException("自定义起止时间需同时提供开始与结束");
        }

        int windowHours;
        LocalDateTime end;
        LocalDateTime start;
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
            start = s;
            end = e;
            windowHours = (int) Math.max(MIN_HOURS, Math.round(minutes / 60.0));
        } else {
            windowHours = normalizeHours(hours);
            end = LocalDateTime.now();
            start = end.minusHours(windowHours);
        }

        NeuroMonitorResponse resp = new NeuroMonitorResponse();
        resp.setPatient(toHeader(patient));
        Window w = new Window();
        w.setHours(windowHours);
        w.setStartTime(start.toString());
        w.setEndTime(end.toString());
        resp.setWindow(w);

        List<String> failed = new ArrayList<>();
        String pid = patient.getPatientId();
        String startStr = start.toString();
        String endStr = end.toString();

        // 1) 诊断识别（决定是否急性脑损伤，进而决定 ICP 空态语义）
        try {
            List<Map<String, Object>> diagRows = neuroAssessMapper.selectDiagnoses(pid);
            resp.setDiagnosis(NeuroScoreParser.parseDiagnoses(diagRows));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("神经监测-诊断查询失败(patientId={})", pid, e);
            resp.getDiagnosis().setDataStatus("UNKNOWN");
            resp.getDiagnosis().setNote("诊断数据暂不可用");
            failed.add(SRC_DIAGNOSIS);
        }

        // 2) 监护评估：神志 / 双侧瞳孔 / ICP（一次 IN 查询）
        boolean observeOk = true;
        try {
            List<Map<String, Object>> obsRows = neuroAssessMapper.selectObserveNeuro(
                    pid, OBSERVE_ITEMS, startStr, endStr);
            ObserveResult parsed = NeuroScoreParser.parseObserve(obsRows);
            resp.setConsciousness(parsed.consciousness);
            resp.setPupils(parsed.pupils);
            resp.setIcp(parsed.icp);
        } catch (Exception e) {
            observeOk = false;
            log.warn("神经监测-监护评估查询失败(patientId={})", pid, e);
            resp.getConsciousness().setDataStatus("UNKNOWN");
            resp.getConsciousness().setNote("数据暂不可用");
            resp.getPupils().setDataStatus("UNKNOWN");
            resp.getPupils().setNote("数据暂不可用");
            resp.getIcp().setDataStatus("UNKNOWN");
            resp.getIcp().setNote("数据暂不可用");
            failed.add(SRC_CONSCIOUSNESS);
            failed.add(SRC_PUPILS);
            failed.add(SRC_ICP);
        }

        // ICP 空态语义：查询成功且无记录时，非脑损伤患者为 NOT_APPLICABLE，脑损伤患者为 NOT_RECORDED
        if (observeOk && "NOT_RECORDED".equals(resp.getIcp().getDataStatus())) {
            if (!resp.getDiagnosis().isBrainInjury()) {
                resp.getIcp().setDataStatus("NOT_APPLICABLE");
                resp.getIcp().setNote("非急性脑损伤患者，通常不进行有创颅内压监测；如临床已监测但无数据，请核对重症系统记录。");
            }
        }

        // 3) GCS 正式文书
        try {
            List<Map<String, Object>> gcsRows = neuroAssessMapper.selectGcsDoc(pid, startStr, endStr);
            resp.setGcs(NeuroScoreParser.parseGcs(gcsRows));
        } catch (Exception e) {
            log.warn("神经监测-GCS文书查询失败(patientId={})", pid, e);
            resp.getGcs().setDataStatus("UNKNOWN");
            resp.getGcs().setNote("数据暂不可用");
            failed.add(SRC_GCS);
        }

        // 4) FOUR 评分文书（2024版 ICU-18 认可的意识评估工具；源未接入时 NOT_RECORDED，
        //    不计入 failedSources —— 源上线前不出数属预期状态，不算查询失败）
        try {
            List<Map<String, Object>> fourRows = neuroAssessMapper.selectScoreDoc(
                    pid, NeuroScoreParser.DOC_FOUR, startStr, endStr);
            resp.setFour(NeuroScoreParser.parseFour(fourRows));
        } catch (Exception e) {
            log.warn("神经监测-FOUR文书查询失败(patientId={})", pid, e);
            resp.getFour().setDataStatus("UNKNOWN");
            resp.getFour().setNote("数据暂不可用");
        }

        resp.setFailedSources(failed);
        if (failed.isEmpty()) {
            resp.setDataStatus("FOUND");
        } else if (failed.size() >= 5) {
            resp.setDataStatus("UNKNOWN");
        } else {
            resp.setDataStatus("PARTIAL");
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
}
