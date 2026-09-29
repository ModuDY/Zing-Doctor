package com.zing.doctor.quality.config;

import com.zing.doctor.quality.dsl.MetricDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标口径批量导入请求。
 *
 * <p>导入与导出的载荷都是 {@link MetricDefinition} 列表，因此<b>导出文件可直接回灌</b>：
 * 跨环境迁移（测试库 → 生产库）、批量改口径（本地改好 JSON 再导入）都走这一条路径。
 */
@Data
public class QualityMetricImportRequest {

    /** 冲突策略：{@code skip}（默认，同编号已存在则跳过）/ {@code overwrite}（覆盖为导入内容） */
    private String mode = "skip";

    /**
     * 是否逐条试跑。
     *
     * <p>默认 <b>false</b>：一次导入上百条时逐条真跑需要上百次查询，
     * 页面会长时间无响应。批量场景依赖 L1–L3 静态校验，试跑留给单条编辑。
     */
    private boolean trial = false;

    /** 待导入的指标定义 */
    private List<MetricDefinition> metrics = new ArrayList<>();
}
