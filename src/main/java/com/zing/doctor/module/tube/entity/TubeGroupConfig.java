package com.zing.doctor.module.tube.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导管类型分组配置（自定义导管类型 + 危重等级）。
 *
 * <p>导管统计页默认只按三管白名单（CVC / 导尿管 / 气道导管 / 其他）归类；
 * 本表允许科室把任意 tube_code 归入自定义组并标注危重等级，
 * 统计页按「危重等级 → 分组」分模块展示（高危置顶）。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_tube_group\"")
public class TubeGroupConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** patient_tube.tube_code，如 tube_CVC管（唯一） */
    private String tubeCode;

    /** 展示名（默认取 patient_tube.tube_name） */
    private String tubeName;

    /** 归入的统计组：CVC / 导尿管 / 气道导管 / 其他 / 自定义名 */
    private String groupName;

    /** 危重等级：高 / 中 / 低 */
    private String riskLevel;

    private Integer sortNo;

    private String remark;

    /** 状态：1启用 0停用 */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
