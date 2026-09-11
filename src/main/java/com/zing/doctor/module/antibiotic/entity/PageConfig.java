package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面注册表：每个可外链访问的功能页面对应一行。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_page_config\"")
public class PageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 页面编码（外链 URL 中的 pageCode，唯一） */
    private String pageCode;

    /** 页面名称 */
    private String pageName;

    /** 前端路由路径，如 /page/abx-patient-list */
    private String frontendPath;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
