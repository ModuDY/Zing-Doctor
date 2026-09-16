package com.zing.doctor.quality.dsl;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源层配置（sources.yaml）。
 *
 * <p>把「物理库/schema/表」与指标彻底解耦：换库、换表名、换 schema 只改本文件。
 */
@Data
public class SourceConfig {

    private int version = 1;

    /** 逻辑数据源：icu / doctor，对应 dynamic-datasource 的 key */
    private Map<String, DataSourceDef> datasources = new LinkedHashMap<>();

    /** 逻辑表名 → 物理表定义 */
    private Map<String, TableDef> tables = new LinkedHashMap<>();

    @Data
    public static class DataSourceDef {
        /** dynamic-datasource 数据源名 */
        private String bean;
        /** 方言：dm（达梦） / mysql */
        private String dialect = "dm";
        /** 物理 schema */
        private String schema;
    }

    @Data
    public static class TableDef {
        /** 归属逻辑数据源 */
        private String ds;
        /** 物理表名 */
        private String name;
        /** 主键列，用于去重/追溯 */
        private String pk;
        /** 事件时间列 */
        private String timeField;
    }
}
