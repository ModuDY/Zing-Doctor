package com.zing.doctor.module.apache2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * APACHE II 配置表
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_apache2\"")
public class Apache2Config {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置类型：observe_item监护item_code/lis_item检验item_code/chronic_keyword慢性健康关键词 */
    private String configType;

    /** 配置键（如：temperature/heart_rate/sodium/potassium/creatinine/hct/wbc） */
    private String configKey;

    /** 配置值（item_code或lis_item_code，多个用逗号分隔） */
    private String configValue;

    /** 项目名称 */
    private String itemName;

    private String remark;

    private Integer sortNo;

    /** 状态：1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
