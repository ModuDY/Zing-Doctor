package com.zing.doctor.module.config.dataitem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据项映射配置（换院部署时唯一需要改的取数层）。
 *
 * <p>背景：各院 ICU 系统的「观察项编码」「LIS 项目名」不一致，而这些值原先写死在
 * {@code VitalsServiceImpl.DEFS}、{@code LabTrendServiceImpl} 的静态关键字表里，
 * 换一家医院就得改代码重新打包。本表把它们抽出来：
 * <b>item_key 是程序内部键（永远不变），match_value 才是各院实际的值</b>。
 *
 * <p>domain 取值：
 * <ul>
 *   <li>{@code VITAL} 生命体征（patient_observe_module_item_record.item_code）</li>
 *   <li>{@code VENT} 呼吸机参数（同上）</li>
 *   <li>{@code IO} 出入量（patient_io_module_item_record.item_code）</li>
 *   <li>{@code LAB} 检验项目（patient_info_lis_item.lis_item_name）</li>
 * </ul>
 *
 * <p>match_type 取值：{@code CODE} 编码精确 / {@code NAME_LIKE} 名称包含 / {@code NAME_EXACT} 名称相等。
 * match_value、exclude_value 均可用逗号或竖线分隔多个值。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_data_item\"")
public class DataItemConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务域：VITAL / VENT / IO / LAB */
    private String domain;

    /** LAB 的分组编码：LIVER / COAG / RENAL / INFECTION；其余域留空 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String groupCode;

    /** 分组显示名，如「肾小球滤过功能」 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String groupName;

    /** 程序内部键（hr / peep / alt / cr…），不随医院变化 */
    private String itemKey;

    /** 展示名，如「心率」「ALT 丙氨酸氨基转移酶」 */
    private String itemName;

    /** 单位 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String unit;

    /** 来源标注：无创 / 有创 / 未标明（生命体征用） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String source;

    /** 匹配方式：CODE / NAME_LIKE / NAME_EXACT */
    private String matchType;

    /** 匹配值：本院实际编码或项目名关键字，多值用逗号分隔 */
    private String matchValue;

    /** 排除词（名称包含匹配用），多值用竖线分隔。可清空，故显式 IGNORED */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String excludeValue;

    private Integer sortNo;

    /** 1 启用 0 停用（停用项不参与取数） */
    private Integer status;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
