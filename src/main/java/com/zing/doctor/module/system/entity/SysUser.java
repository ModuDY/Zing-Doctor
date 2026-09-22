package com.zing.doctor.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 直连登录账号。
 *
 * <p>与外链免登录（{@code sys_page_config} + extToken/签名）是两条独立通道：
 * 直连用户在 {@code /page/login} 用账号密码换取 JWT；外链由 ICU 系统带凭证直接进页面，不落此表。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"sys_user\"")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /** 姓名 */
    private String realName;

    /** 口令散列：pbkdf2$迭代次数$盐(Base64)$摘要(Base64) */
    private String passwordHash;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 最近一次登录成功时间 */
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
