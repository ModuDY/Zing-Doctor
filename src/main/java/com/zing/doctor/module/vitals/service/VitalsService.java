package com.zing.doctor.module.vitals.service;

import com.zing.doctor.module.vitals.dto.VitalsResponse;

/**
 * 生命体征趋势（只读）。
 *
 * <p>docs/25 §1：数据回顾工具，不自动诊断、不做早期预警评分。
 * 时间窗由后端校验（最长 7 天，防止无界查询）。
 */
public interface VitalsService {

    /**
     * @param patientId 患者 patient_info.id
     * @param hours     回溯小时数，1~168（7天）
     */
    VitalsResponse trend(String patientId, int hours);
}