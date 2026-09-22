package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 细菌培养监测配置表：第四维度（细菌培养检出监测与院感防控）的配置知识库。
 * 包含细菌分类（革兰阳性/阴性/真菌）、高风险细菌列表、标本类型等配置。
 * 由于 ICU 库细菌名称中无耐药关键词，MDRO 精确判定需药敏结果支持，
 * 本表用于细菌分类统计和高风险细菌标记。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_mdro\"")
public class MdroConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置类型：bacteria_class细菌分类, high_risk高风险细菌, specimen标本类型 */
    private String configType;

    /** 细菌名称 */
    private String bacteriaName;

    /** 细菌分类：gram_positive革兰阳性, gram_negative革兰阴性, fungi真菌, other其他 */
    private String bacteriaClass;

    /** 是否高风险细菌：1是0否（ICU常见MDRO风险菌） */
    private Integer isHighRisk;

    /** 匹配关键词（逗号分隔，用于细菌名称模糊匹配分类） */
    private String keywords;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
