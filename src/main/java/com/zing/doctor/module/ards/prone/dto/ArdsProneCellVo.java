package com.zing.doctor.module.ards.prone.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单元格值视图（填写页矩阵与打印文书共用同一份数据）。
 */
@Data
public class ArdsProneCellVo {

    private Integer tpIndex;

    private String paramKey;

    private String value;

    /** auto / lis / calc / man */
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;

    /** 人工修正原因（覆盖自动值时必填） */
    private String manualReason;

    /** 是否超出内置参考区间（仅界面标红，不做告警） */
    private Boolean abnormal;
}
