package com.zing.doctor.module.fluid.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 体液平衡（docs/25 §2）。
 *
 * <p><b>刻意不给"净平衡"合计数</b>：核查发现入量有两条通道（I/O 记录 + 医嘱执行），
 * 以及 {@code config_io_item.io_type='i'}（252 个配置）从未被使用，去重关系未能证明。
 * 方案要求"来源可解释且已去重后才计算净平衡"，故一期只按来源分列。
 */
@Data
public class FluidBalanceResponse {

    private String patientId;
    private String patientName;
    private String inHospitalNo;
    private String rangeStart;
    private String rangeEnd;

    /** 按来源分栏（每栏独立 dataStatus，不合并） */
    private List<Source> sources = new ArrayList<>();

    private Integer recordCount;

    @Data
    public static class Source {
        private String key;
        private String name;
        /** AVAILABLE / NO_RECORD / UNKNOWN */
        private String dataStatus = "NO_RECORD";
        /** 该来源总量（同一来源内相加；跨来源不相加） */
        private Double total;
        private String unit = "mL";
        /** 按小时汇总（缺测小时保留空缺，不补零绘制） */
        private List<Bucket> buckets = new ArrayList<>();
        private List<Row> rows = new ArrayList<>();
        private String note;
    }

    @Data
    public static class Bucket {
        private String hour;
        private Double value;
    }

    @Data
    public static class Row {
        private String time;
        private String itemName;
        private String value;
        private String unit;
        private String staffName;
        private String source;
    }
}
