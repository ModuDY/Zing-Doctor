package com.zing.doctor.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归档推送流水。
 *
 * <p>记录每次归档/撤销的目标路径与对方响应，便于事后追溯
 * （成功与否、失败原因、当时推的是什么）。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_archive_log\"")
public class ArchiveLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务：SOFA / APACHE2 */
    private String biz;

    /** 评分记录 ID */
    private Long recordId;

    private String inHospitalNo;

    private String patientName;

    /** 文书编码：sofa / apache2 */
    private String docCode;

    /** 评分日期 yyyy-MM-dd */
    private String scoreDate;

    /** 按归档目录规则拼出的路径 */
    private String filePath;

    /** 调用的接口地址 */
    private String apiUrl;

    /** 操作：push=归档推送 unmark=撤销标记 */
    private String opType;

    /** 是否成功：1成功 0失败 */
    private Integer success;

    private Integer httpStatus;

    private Integer respCode;

    /** 失败原因 / 对方返回摘要 */
    private String respMessage;

    private String operator;

    private LocalDateTime createTime;
}
