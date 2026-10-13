package com.zing.doctor.module.fluid.service;

import com.zing.doctor.module.fluid.dto.FluidBalanceResponse;

/**
 * 体液平衡（只读）。docs/25 §2：按来源分列，不给未去重的净平衡合计数。
 */
public interface FluidBalanceService {

    /**
     * @param patientId 患者 patient_info.id
     * @param hours     回溯小时数，1~72（默认 24）
     */
    FluidBalanceResponse balance(String patientId, int hours);
}