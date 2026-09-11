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
}
