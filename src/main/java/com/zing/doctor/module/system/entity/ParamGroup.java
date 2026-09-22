package com.zing.doctor.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参数分组（参数设置页左侧导航）。
 *
 * <p>分组由页面维护而非写死在前端：新增功能模块的参数时，先在页面上加一个分组，
 * 再把参数挂到该分组下，不需要改代码发版。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"sys_param_group\"")
public class ParamGroup {

    /**
     * 主键由程序生成（15 位全局唯一 ID），不用数据库自增：
     * 自增 ID 从 1 开始，多院区合并或导历史数据时必然撞车。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分组编码（唯一，与 SysParam.paramGroup 对应） */
    private String groupCode;

    /** 分组名称（页面展示） */
    private String groupName;

    private Integer sortNo;

    /** Element Plus 图标名，可空 */
    private String icon;

    private String remark;

    /** 状态：1启用 0停用（停用后不在导航显示，其下参数也不再展示） */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
