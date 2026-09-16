package com.zing.doctor.module.sofa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SOFA 配置表。
 *
 * <p>config_type 取值：
 * <ul>
 *   <li>{@code lis_item}：检验项 item_code 映射（platelet / bilirubin / creatinine）</li>
 *   <li>{@code observe_item}：监护项 item_code 映射（map / fio2 / peep）</li>
 *   <li>{@code io_item}：出入量项（urine）</li>
 *   <li>{@code vasopressor}：血管活性药药名关键词与阈值（norepinephrine / epinephrine / dopamine / dobutamine）</li>
 *   <li>{@code conversion}：剂量换算系数（重酒石酸盐↔碱基等）</li>
 *   <li>{@code default_weight}：身高体重均缺失时的年龄+性别默认体重</li>
 * </ul>
 * 表空时回退代码内置默认值。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"sofa_config\"")
public class SofaConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String configType;

    private String configKey;

    private String configValue;

    private String itemName;

    private String remark;

    private Integer sortNo;

    /** 状态：1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
