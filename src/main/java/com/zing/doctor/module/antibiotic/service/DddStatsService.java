package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.dto.DddDrugRank;
import com.zing.doctor.module.antibiotic.dto.DddOverviewView;
import com.zing.doctor.module.antibiotic.dto.DddPatientDetail;

import java.util.List;
import java.util.Map;

/**
 * 第三维度：抗菌药物使用强度（DDD）统计 Service。
 */
public interface DddStatsService {

    /**
     * 获取所有启用的科室列表（用于科室筛选下拉框）。
     */
    List<Map<String, Object>> getAllDepartments();

    /**
     * 获取总览仪表盘数据。
     * @param startTime 统计开始时间（yyyy-MM-dd HH:mm:ss）
     * @param endTime 统计结束时间（yyyy-MM-dd HH:mm:ss）
     * @param departCode 科室编码，为空时查全部科室
     */
    DddOverviewView getOverview(String startTime, String endTime, String departCode);

    /**
     * 获取药品消耗排名 TOP20。
     */
    List<DddDrugRank> getDrugRankTop20(String startTime, String endTime, String departCode);

    /**
     * 获取月度趋势（近12个月）。
     */
    List<DddOverviewView.DddTrendPoint> getMonthlyTrend(String startTime, String endTime, String departCode);

    /**
     * 获取抗菌药物使用患者明细列表。
     */
    List<DddPatientDetail> getPatientDetails(String startTime, String endTime, String departCode);

    /**
     * 获取单个患者的抗菌药物使用明细。
     */
    DddPatientDetail getPatientDetail(String inHospitalNo, String startTime, String endTime);
}
