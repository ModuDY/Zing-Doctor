package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单条方案推荐项。
 */
@Data
public class AdviceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推荐药品/方案名 */
    private String drugName;

    /** 剂量方案 */
    private String dosePlan;

    /** 给药途径 */
    private String route;

    /** 建议强度：强 / 弱 */
    private String adviceLevel;

    /** 推荐理由 */
    private String reason;

    /** 证据说明 */
    private String evidence;
}
