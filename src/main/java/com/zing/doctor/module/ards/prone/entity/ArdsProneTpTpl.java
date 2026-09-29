package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ARDS 俯卧位时点模板（depart_code 为空 = 全院默认模板）。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_prone_timepoint_tpl\"")
public class ArdsProneTpTpl {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 科室编码；空串表示全院默认 */
    private String departCode;

    private Integer tpIndex;

    private String tpLabel;

    private Integer offsetMinutes;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
