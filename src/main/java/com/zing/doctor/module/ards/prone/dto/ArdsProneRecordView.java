package com.zing.doctor.module.ards.prone.dto;

import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import lombok.Data;

import java.util.List;

/**
 * 填写页视图：主记录 + 时点 + 单元格矩阵 + APACHE II 显示开关。
 */
@Data
public class ArdsProneRecordView {

    private ArdsProneRecord record;

    private List<ArdsProneTimepoint> timepoints;

    private List<ArdsProneCellVo> cells;

    /**
     * APACHE II 是否显示：由参数 ARDS_PRONE_APACHE2_SHOW 控制（默认显示、全院统一），
     * 同时作用于填写页、文书预览、打印文书与回传文书。
     */
    private Boolean apache2Show;
}
