package com.zing.doctor.module.labtrend.service;

import com.zing.doctor.module.labtrend.dto.LabTrendResponse;

/**
 * 检验趋势（肝功能 / 凝血功能 / 肾功能与电解质）。
 *
 * <p>编码与口径来自 docs/25 生产库核查结果：按 <b>lis_item_name</b> 匹配而非只按 code ——
 * 生产库存在同一项目多编码（ALT 两套、血钾的「*钾 / 钾离子」两套），只按 code 会漏数据。
 * 肝功能、凝血、肾功能与电解质三组均已做近 90 天实测核查（肾功能与电解质见 docs/25 §9）。
 */
public interface LabTrendService {

    /** type: liver（肝功能）/ coag（凝血功能）/ renal（肾功能与电解质） */
    LabTrendResponse trend(String patientId, String type, int hours);
}