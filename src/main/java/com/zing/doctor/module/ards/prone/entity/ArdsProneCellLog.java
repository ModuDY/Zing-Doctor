package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ARDS 俯卧位记录单元格更正留痕表。
 *
 * <p>提交后不设修改时限，任何更正都保留原值、新值、修改人、时间与原因。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_prone_cell_log\"")
public class ArdsProneCellLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recordId;

    private Integer tpIndex;

    private String paramKey;

    private String oldValue;

    private String newValue;

    private String oldSource;

    private String newSource;

    private String reason;

    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
