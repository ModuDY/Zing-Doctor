package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抗菌药物识别词库配置表：
 * 脓毒症集束化治疗中"广谱抗菌药白名单（broad_spectrum）"与"非抗菌药黑名单（non_antibiotic）"，
 * 支持后台页面新增/修改/停用，表空时回退代码内置默认。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_abx_word_config\"")
public class AbxWordConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 词类型：broad_spectrum 广谱抗菌药白名单 / non_antibiotic 非抗菌药黑名单 */
    private String wordType;

    /** 匹配关键词 */
    private String keyword;

    /** 分组（如：电解质/抗组胺/广谱抗菌药） */
    private String category;

    /** 备注 */
    private String remark;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
