package com.zing.doctor.module.sofa.service;

import com.zing.doctor.module.sofa.dto.SofaAssessmentView;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;

import java.util.List;
import java.util.Map;

/**
 * SOFA（序贯器官衰竭评估）Service。
 *
 * <p>取数窗口内**最差值**；呼吸/循环/肾按 SOFA 规则取分，缺数据项按 0 分并标注。
 */
public interface SofaService {

    /**
     * 单患者 SOFA 评估（自动取数 + 计分，不落库）。
     *
     * @param patientId ICU 患者 ID
     * @param startTime 取数起（含），yyyy-MM-dd HH:mm:ss
     * @param endTime   取数止（不含），yyyy-MM-dd HH:mm:ss
     */
    SofaAssessmentView getAssessment(String patientId, String startTime, String endTime);

    /** 按住院号评估（ICU 外链用）：先解析 patientId 再走完整评估流程 */
    SofaAssessmentView getAssessmentByInHospitalNo(String inHospitalNo, String startTime, String endTime);

    /**
     * 保存评分记录。
     * <p>若未传各项分值与总分（医生仅填了备注等），服务会按当前取数范围重新计算后落库。
     */
    SofaScoreRecord saveRecord(SofaScoreRecord record, String startTime, String endTime);

    /** 某患者的历史评分（按 score_time 倒序，仅正常状态） */
    List<SofaScoreRecord> listByPatient(String inHospitalNo);

    /** 逻辑删除 */
    boolean deleteRecord(Long id);

    /** 科室总览：评分分布、平均分、ΔSOFA 恶化预警、患者列表 */
    Map<String, Object> getOverview(String departCode, String startTime, String endTime);

    /** 配置列表（configType 为空返回全部启用项） */
    List<Map<String, Object>> listConfig(String configType);

    /**
     * 评分主体保存后单独补传文书 PDF 大字段。
     * <p>与主体保存解耦：大字段慢/失败都不影响已落库的评分。
     */
    boolean attachPdf(Long id, String pdfData, String pdfName);

    /** 取某条记录的 PDF Base64 与文件名（供在线预览/下载） */
    SofaScoreRecord getRecordPdf(Long id);

    /**
     * 自动初评（定时任务与手动触发共用）：
     * 为「在科且入科超过 overHours 小时、当日尚无评分记录」的患者生成一份初评（score_type=daily）。
     * <p>幂等：按“当日是否已有记录”跳过；单实例内加互斥锁，避免定时与手动并发重复建档。
     *
     * @return scanned / created / skipped / failed 统计
     */
    Map<String, Object> autoGenerateScores(String departCode, int overHours);

    /**
     * 单指标趋势数据，供评分页「来源」弹窗趋势图。
     *
     * @param metricKey resp 氧合指数 / coag 血小板 / liver 总胆红素 / cardio MAP /
     *                  neuro GCS / renal 肌酐（total SOFA 总分趋势已下线）
     * @return {time,value} 列表（时间升序）
     */
    List<Map<String, Object>> getMetricTrend(String patientId, String metricKey, String startTime, String endTime);

    /**
     * 患者在重症系统（Z_ICU_GCS）已评估的 GCS 记录，按评估时间倒序（含插管/未评全记录）。
     * <p>供评分页 GCS 弹窗的「自动同步最新 / 手动选择已有记录」使用，与 APACHE II 同源同口径。
     */
    List<Map<String, Object>> listSystemGcs(String patientId);

    /** 配置保存：id 为空新增，否则更新 */
    boolean saveConfig(Map<String, Object> body);

    /** 配置删除（逻辑删除 status=0） */
    boolean deleteConfig(Long id);

    /** 配置启用/停用 */
    boolean toggleConfig(Long id, Integer status);
}
