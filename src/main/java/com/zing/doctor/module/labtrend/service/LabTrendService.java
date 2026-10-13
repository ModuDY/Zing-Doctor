package com.zing.doctor.module.labtrend.service;

import com.zing.doctor.module.labtrend.dto.LabTrendResponse;

/**
 * 检验趋势（肝功能 / 凝血功能）。
 *
 * <p>编码与口径来自 docs/25 生产库核查结果：按 <b>lis_item_name</b> 匹配而非只按 code ——
 * 生产库存在同一项目多编码（ALT 两套、PCT 三项），只按 code 会漏数据。
 */
public interface LabTrendService {

    /** type: liver（肝功能）/ coag（凝血功能） */
    LabTrendResponse trend(String patientId, String type, int hours);
}