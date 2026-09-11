package com.zing.doctor.module.apache2.service;

import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;

import java.util.List;
import java.util.Map;

/**
 * APACHE II 评分 Service
 */
public interface Apache2Service {

    /**
     * 主任总览：统计数据 + 患者评分列表
     */
    Map<String, Object> getOverview(String departCode, String startTime, String endTime);

    /**
     * 患者评分记录列表
     */
    List<Apache2ScoreRecord> getPatientRecords(String inHospitalNo);

    /**
     * 获取单条评分记录详情
     */
    Apache2ScoreRecord getRecordById(Long id);

    /**
     * 获取某条记录的评分文书 PDF（Base64 与文件名）
     */
    Apache2ScoreRecord getRecordPdf(Long id);

    /**
     * 自动获取患者生理数据（用于评分前预填充）
     */
    Map<String, Object> autoFetchPhysiologyData(String patientId, String startTime, String endTime);

    /**
     * 计算APACHE II评分
     */
    Map<String, Object> calculateScore(Map<String, Object> params);

    /**
     * 保存评分记录
     */
    Apache2ScoreRecord saveScore(Apache2ScoreRecord record);

    /**
     * 评分主体已落库后，单独补传评分文书 PDF 大字段（与主保存解耦，大字段慢/失败不影响评分本身）
     * @param id 已存在的评分记录ID
     * @param pdfData PDF的Base64（不含data前缀）
     * @param pdfName PDF文件名
     * @return 是否更新成功
     */
    boolean attachPdf(Long id, String pdfData, String pdfName);

    /**
     * 删除评分记录（逻辑删除）
     */
    boolean deleteScore(Long id, String operator);

    /**
     * 获取配置列表
     */
    List<Map<String, Object>> getConfigList(String configType);

    /**
     * 根据住院号获取患者基本信息
     */
    Map<String, Object> getPatientInfo(String inHospitalNo);

    /**
     * 获取单指标的历史趋势数据（用于来源弹窗趋势图）
     * @param patientId 患者ID
     * @param metricKey 指标key（temperature/heartRate/respiratoryRate/map/fio2/sodium/potassium/creatinine/hct/wbc/ph）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 趋势数据列表，每项包含time和value
     */
    List<Map<String, Object>> getMetricTrend(String patientId, String metricKey, String startTime, String endTime);

    /**
     * 氧合三要素（FiO2 / PaO2 / A-aDO2）趋势，供评分页「来源」弹窗展示。
     * <p>氧合是派生项：FiO2 决定评分分支（≥50% 用 A-aDO2，否则用 PaO2），
     * 故趋势需同时给出三条序列。返回键：fio2 / pao2 / aado2（{time,value} 列表，时间升序）、
     * worstTime（评分实际选中那一管的采集时间）、worstScore，以及该管的 worstFio2 / worstPao2 / worstAado2。
     */
    Map<String, Object> getOxygenTrend(String patientId, String startTime, String endTime);

    /**
     * 拉取患者在重症系统中已评估的 GCS 记录（Z_ICU_GCS），供评分时同步选择。
     * item1=E睁眼、item2=V言语、item3=M运动、item4=ET 表示插管/气切（言语不可评）。
     * 返回每项：recordTime/recordStaffName/eye/verbal/motor/totalText/intubated/auditable。
     */
    List<Map<String, Object>> listSystemGcs(String patientId);

    /**
     * 为所有“在科且入科超过指定小时数”、且尚无评分记录的患者自动生成一份 APACHE II 初评记录（幂等）。
     * @param departCode 科室编码，为空则全科室
     * @param overHours 入科需超过的小时数（需求为 24）
     * @return 扫描/新增/跳过/失败数量及明细
     */
    Map<String, Object> autoGenerateScores(String departCode, int overHours);
}
