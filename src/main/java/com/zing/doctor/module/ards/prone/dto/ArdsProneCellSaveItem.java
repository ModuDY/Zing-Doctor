package com.zing.doctor.module.ards.prone.dto;

import lombok.Data;

/**
 * 单元格保存项（前端按「时点 + 参数」提交）。
 */
@Data
public class ArdsProneCellSaveItem {

    private Integer tpIndex;

    private String paramKey;

    private String value;

    /** 更正原因：覆盖自动采集 / 检验同步值时必填 */
    private String reason;
}
