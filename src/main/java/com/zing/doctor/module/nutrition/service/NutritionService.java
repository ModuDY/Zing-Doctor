package com.zing.doctor.module.nutrition.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.nutrition.dto.NutritionResponse;

public interface NutritionService {

    /**
     * 单患者营养支持分析。
     *
     * @param hours    时间窗小时（默认24，最大720=30天）
     * @param startIso 自定义起（含），与 endIso 同时提供
     * @param endIso   自定义止（不含），与 startIso 同时提供
     */
    NutritionResponse analyze(WorkbenchPatient patient, Integer hours, String startIso, String endIso);
}
