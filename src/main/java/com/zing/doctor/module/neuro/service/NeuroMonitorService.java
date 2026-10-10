package com.zing.doctor.module.neuro.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse;

/**
 * 急性脑损伤 / 神经功能监测聚合（只读）。
 */
public interface NeuroMonitorService {

    /** 按小时窗口分析（默认 24h，最长 30 天）。 */
    NeuroMonitorResponse analyze(WorkbenchPatient patient, Integer hours);

    /** 自定义起止窗口分析；startIso/endIso 同时提供时优先，窗口最长 30 天。 */
    NeuroMonitorResponse analyze(WorkbenchPatient patient, Integer hours, String startIso, String endIso);
}
