package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抗菌药物字典（HIS 药品字典同步副本）。
 *
 * <p>数据来源：ICU 库 {@code zing_icu_db_prod.config_drug} 中 {@code is_antibiotics = '1'} 的药品，
 * 由 {@code AbxDrugSyncTask} 夜间定时同步（{@code AbxDrugDictService#syncFromIcu}）。
 *
 * <p>用途：为 {@code AbxDrugRecognizer} 提供「药品名 / 简称 / 通用名」精确匹配，
 * 使抗菌药识别不再只依赖关键词包含匹配，显著降低漏判（新药、商品名、复方名）。
 *
 * <p>注意本表**只是 HIS 字典的镜像**，不应手工修改业务字段；唯一例外是 {@code status}
 * （HIS 取消抗菌药标记后由同步任务置 0，不物理删除，保留历史可追溯）。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_abx_drug_dict\"")
public class AbxDrugDict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** HIS 药品编码（唯一键，增量比对依据） */
    private String drugCode;

    /** 药品名称（含商品名，如 盐酸克林霉素胶囊(特丽仙)） */
    private String drugName;

    /** 药品简称 */
    private String drugShortName;

    /** 通用名（无商品名） */
    private String drugNormalName;

    /** 拼音码 */
    private String drugPinyin;

    /** 规格 */
    private String spec;

    /** 单次剂量 */
    private String dose;

    /** 剂量单位 */
    private String unitCode;

    /** 生产厂家 */
    private String drugFactoryName;

    /** HIS 抗菌药标记（同步源固定为 '1'） */
    private String isAntibiotics;

    /** HIS 抗菌药标识色 */
    private String antibioticsColor;

    /** 最近一次同步写入时间（内容无变化时不刷新） */
    private LocalDateTime syncTime;

    /** 状态：1 在库（HIS 仍标记为抗菌药） 0 已失效（HIS 已取消抗菌药标记） */
    private Integer status;

    /** 删除标记：0 正常 */
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
