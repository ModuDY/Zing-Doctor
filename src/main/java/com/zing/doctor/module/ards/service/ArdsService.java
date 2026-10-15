package com.zing.doctor.module.ards.service;

import java.util.List;
import java.util.Map;

/**
 * ARDS 监测 Service
 */
public interface ArdsService {

    /**
     * 获取ARDS总览：统计数据 + 患者列表（含最新呼吸机参数、氧合指数、分级、达标情况）
     */
    Map<String, Object> getArdsOverview(String departCode, String startTime, String endTime, String grade);

    /**
     * 获取患者呼吸机参数趋势（最近days天）
     */
    List<Map<String, Object>> getVentilatorTrend(String patientId, String startTime, String endTime);

    /**
     * 获取患者氧合指数历史（血气时间点）
     */
    List<Map<String, Object>> getOxygenationHistory(String patientId);

    /**
     * ARDS 全院筛查：周期内「在院或已出科」且诊断含 ARDS 的患者清单（不限定科室，
     * departCode 由 Controller 层按账号权限收敛，ALL=全院）。附在科天数与汇总计数。
     */
    Map<String, Object> hospitalScreen(String startTime, String endTime, String departCode);
}
