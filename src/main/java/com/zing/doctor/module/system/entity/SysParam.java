package com.zing.doctor.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数表（参数设置页面维护）。
 *
 * <p>当前承载「文书归档接口地址」（param_key = {@code ARCHIVE_API_URL}），
 * SOFA 与 APACHE II 共用同一个地址。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_sys_param\"")
public class SysParam {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 参数键（唯一） */
    private String paramKey;

    /** 参数名称（页面展示） */
    private String paramName;

    /** 参数值 */
    private String paramValue;

    /** 参数分组：archive=文书归档 */
    private String paramGroup;

    private Integer sortNo;

    /** 参数类型：text / textarea / number / switch / select，页面据此渲染控件 */
    private String paramType;

    /** select 选项 JSON：[{"label":"显示名","value":"存储值"}] */
    private String options;

    /** 默认值（参数值为空时取用） */
    private String defaultValue;

    /** 是否必填：1 必填 / 0 选填 */
    private Integer required;

    /** 校验正则（可选，保存时校验参数值） */
    private String regex;

    /** 状态：1启用 0停用 */
    private Integer status;

    private String remark;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
