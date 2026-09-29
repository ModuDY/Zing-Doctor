package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质控配置变更历史。
 *
 * <p>每次保存配置留一份完整 JSON 快照，因此任何一版口径都能原样还原。
 * 这是「口径可回溯」在配置侧的另一半：结果表记录 {@code expressionVersion}，
 * 本表记录该版本对应的配置内容，两者合起来才能回答「这个数当时是怎么算出来的」。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_def_history\"")
public class QualityDefHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** METRIC 指标 / FACT 事实层 */
    private String defType;
    /** 指标编号或事实层名 */
    private String defKey;
    private Integer exprVersion;
    /** CREATE / UPDATE / DISABLE / ROLLBACK */
    private String changeType;
    /** 变更后的完整 JSON 快照 */
    private String snapshot;
    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
