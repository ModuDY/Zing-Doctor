package com.zing.doctor.module.msgpush.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条预警推送请求（自动推送用，页面不调）。
 *
 * <p>与 {@link MsgPushTestRequest} 的差别：测试发送是「现场填一段话发出去」，
 * 本请求是「按规则模板 + 患者上下文渲染一条预警」。因此文案在这里是<b>模板</b>，
 * 变量由生成预警的一方提供 —— 数值（GCS 升降分、俯卧位时长、呼吸频率）
 * 是现算的、库里没有，只能随请求带过来。
 */
@Data
public class MsgPushAlertRequest {

    /** 预警规则代码，如 GCS_DROP_2；写入 msg_push_log.rule_code，也是去重与统计的依据 */
    private String ruleCode;

    /** 患者内部 ID（patient_info.id）；为空则不去重（调用方应直接跳过，不做无标识的群发） */
    private String patientId;

    private String patientName;
    private String bedNo;
    /** sys_depart.org_code */
    private String deptCode;

    /** 文案模板（含 #占位符#）；为空则由实现回退到默认模板 */
    private String contentTemplate;

    /**
     * 渲染变量（键不带 # 号）。至少应含：
     * patientName / bedNo / inHospitalNo / departName / wardName / age / gender，
     * 恶化类规则另带 gcsFrom / gcsTo / gcsDrop / proneStart / proneHours / rrValue / brainDiag。
     */
    private Map<String, String> vars = new LinkedHashMap<>();
}
