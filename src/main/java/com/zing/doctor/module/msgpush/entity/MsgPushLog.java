package com.zing.doctor.module.msgpush.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息推送审计（msg_push_log）。
 *
 * <p>每次发送都留痕，含演练模式：发了什么、谁发的、网关回了什么、耗时多少。
 * 预警类消息一旦发错人/发错内容，这就是唯一可追的依据。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"msg_push_log\"")
public class MsgPushLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 场景：test=测试发送 / alert=预警推送 */
    private String scene;

    /** 预警类型代码（测试发送为空） */
    private String ruleCode;

    private String patientId;
    private String patientName;
    private String bedNo;
    private String deptCode;

    /** 接收号码 */
    private String mobile;

    /** 短信内容 */
    private String content;

    /** 实际调用的接口地址 */
    private String url;

    private Integer httpStatus;

    /** 1 成功 0 失败 */
    private Integer success;

    /** 1 演练（未真正发出） */
    private Integer dryRun;

    /** 实际发出的报文 */
    private String requestBody;

    /** 返回内容 */
    private String respBody;

    private String errorMsg;

    private Integer durationMs;

    /** 操作人 */
    private String operator;

    private LocalDateTime createTime;
}
