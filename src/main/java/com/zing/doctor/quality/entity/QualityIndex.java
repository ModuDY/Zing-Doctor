package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质控指标字典（127 条）。
 *
 * <p>由 {@code quality/metrics/*.yaml} 启动同步而来，因此这里只读不写配置口径；
 * 页面需要的展示语义（分类/单位/是否显示患者）全部落库，避免前端再解析配置。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_index\"")
public class QualityIndex {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指标编号 quality_xxx */
    private String indexCode;
    private String indexName;
    /** 所属域：患者流转 / 脓毒症_感染性休克 / ... */
    private String domainCode;
    private String categoryCode;
    private String groupCode;
    private String qualityTypeCode;
    private String indexStandardCode;
    private String unit;
    /** RATE / COUNT / DAYS / RATIO / AMOUNT */
    private String valueType;
    /** DSL / MANUAL / CUSTOM_SQL */
    private String calcMode;
    private String factName;
    /** IMPL / PLACEHOLDER / PENDING_SOURCE / MANUAL */
    private String implStatus;
    private Integer expressionVersion;
    private String amountShowType;
    private String analysisCountType;
    private Integer isUseZeroShow;
    private Integer isShowPatient;
    private String legacyScript;
    private String legacySource;
    private String newTarget;
    private String reuseLevel;
    private String dependKeys;
    private Integer sortNo;
    private String remark;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 当前周期是否可计算（非数据库字段）：由 impl_status 推导，供前端置灰"暂未开放"。 */
    @TableField(exist = false)
    private Integer computable;
}
