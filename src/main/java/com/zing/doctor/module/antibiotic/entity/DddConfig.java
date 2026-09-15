package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抗菌药物 DDD 值配置表：第三维度（使用强度分析）的核心知识库。
 * 初始数据按 WHO ATC/DDD 最新版本录入，支持后台页面修改。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_ddd_config\"")
public class DddConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 药品通用名 */
    private String drugName;

    /** WHO ATC 编码 */
    private String atcCode;

    /** DDD 值（限定日剂量） */
    private BigDecimal dddValue;

    /** DDD 单位（g/mg/MU等） */
    private String dddUnit;

    /** 给药途径：注射/口服 */
    private String route;

    /** 管理级别：非限制/限制/特殊 */
    private String manageLevel;

    /** 药物分类（青霉素类/头孢类/碳青霉烯类等） */
    private String drugClass;

    /** 匹配关键词（逗号分隔，用于医嘱名称模糊匹配） */
    private String keywords;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
