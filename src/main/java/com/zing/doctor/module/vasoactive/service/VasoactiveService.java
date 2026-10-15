package com.zing.doctor.module.vasoactive.service;

import java.util.Map;

/**
 * 血管活性药分析 Service。
 */
public interface VasoactiveService {

    /**
     * 患者血管活性药分析。
     *
     * @param patientId ICU 患者主键
     * @param hours     回看窗口（小时）
     */
    Map<String, Object> analysis(String patientId, int hours);
}
