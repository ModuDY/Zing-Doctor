package com.zing.doctor.module.sedation.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse;

/**
 * 镇痛镇静监测分析（只读、零录入）。
 *
 * <p>聚合重症系统正式评分文书（NRS / CPOT / RASS / CAM-ICU），不新增任何录入项。
 * 数据权限由 Controller 层复用 {@code UserDepartScopeService} 按患者所属科室校验后传入患者。
 */
public interface SedationMonitorService {

    /**
     * 分析单个患者在指定时间窗内的镇痛镇静 / 谵妄评估（按小时回溯，窗口结束=当前时间）。
     *
     * @param patient 已通过科室权限校验的在科患者
     * @param hours   时间窗小时数；null 默认 24，范围 1~720（30 天）
     */
    SedationMonitorResponse analyze(WorkbenchPatient patient, Integer hours);

    /**
     * 分析单个患者在显式起止时间窗内的镇痛镇静 / 谵妄评估。
     *
     * @param patient  已通过科室权限校验的在科患者
     * @param hours    自定义起止为空时使用的回溯小时数；非空时仅用于窗口回显兜底
     * @param startIso 展示窗口开始时间（含），ISO {@code yyyy-MM-dd'T'HH:mm:ss}；与 endIso 同时为空则按 hours
     * @param endIso    展示窗口结束时间（不含），ISO；与 startIso 同时为空则按 hours
     * @throws IllegalArgumentException 起止只传一个、格式错误、结束不晚于开始或窗口超过 30 天
     */
    SedationMonitorResponse analyze(WorkbenchPatient patient, Integer hours, String startIso, String endIso);

    /**
     * 窗口内阿片类镇痛药执行医嘱（芬太尼/瑞芬/舒芬、吗啡、地佐辛等），
     * 供监测页「镇痛药使用与评分关联」区块 —— 药物暴露与 NRS/CPOT 评分同时间轴对照。
     *
     * @param patient  已通过科室权限校验的患者
     * @param hours    自定义起止为空时的回溯小时数；null 默认 24，范围 1~720
     * @param startIso 与 endIso 同时提供则优先（格式与约束同 {@link #analyze}）
     * @param endIso   与 startIso 同时提供则优先
     * @throws IllegalArgumentException 起止只传一个、格式错误或窗口超过 30 天
     */
    java.util.List<java.util.Map<String, Object>> analgesiaDrugs(WorkbenchPatient patient, Integer hours,
                                                                 String startIso, String endIso);
}
