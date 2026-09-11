package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.dto.MdroBacteriaRank;
import com.zing.doctor.module.antibiotic.dto.MdroOverviewView;
import com.zing.doctor.module.antibiotic.dto.MdroPatientDetail;

import java.util.List;
import java.util.Map;

/**
 * 第四维度：细菌培养检出监测统计 Service。
 */
public interface MdroStatsService {

    /** 获取所有科室列表 */
    List<Map<String, Object>> getAllDepartments();

    /** 总览仪表盘统计 */
    MdroOverviewView getOverview(String startTime, String endTime, String departCode);

    /** 菌株排名 TOP20 */
    List<MdroBacteriaRank> getBacteriaRankTop20(String startTime, String endTime, String departCode);

    /** 月度趋势统计 */
    List<MdroOverviewView.MdroTrendPoint> getMonthlyTrend(String startTime, String endTime, String departCode);

    /** 患者明细列表 */
    List<MdroPatientDetail> getPatientDetails(String startTime, String endTime, String departCode);

    /** 单患者明细 */
    MdroPatientDetail getPatientDetail(String inHospitalNo, String startTime, String endTime);

    /** 标本类型分布 */
    List<Map<String, Object>> getSpecimenDistribution(String startTime, String endTime, String departCode);

    /** 高风险细菌检出预警（当日/当周新检出） */
    List<MdroPatientDetail> getHighRiskAlerts(String startTime, String endTime, String departCode);
}
