package com.zing.doctor.module.system.service;

import com.zing.doctor.module.system.entity.SysParam;

import java.util.List;

/**
 * 系统参数服务（参数设置页面）。
 */
public interface SysParamService {

    /** 归档接口地址的 param_key（SOFA / APACHE II 共用） */
    String KEY_ARCHIVE_API_URL = "ARCHIVE_API_URL";

    /** 归档目录规则的 param_key（含 #in_hospital_no# 等占位符） */
    String KEY_ARCHIVE_DIR = "ARCHIVE_DIR";

    /** 外链工号自动注册总开关：1 开 / 0 关（默认关） */
    String KEY_AUTO_REGISTER_ENABLED = "AUTO_REGISTER_ENABLED";

    /** 自动注册账号的初始口令来源：WORK_NO=口令同工号 / FIXED=固定初始口令 */
    String KEY_AUTO_REGISTER_PASSWORD_RULE = "AUTO_REGISTER_PASSWORD_RULE";

    /** 上面取值为 FIXED 时使用的固定初始口令 */
    String KEY_AUTO_REGISTER_FIXED_PASSWORD = "AUTO_REGISTER_FIXED_PASSWORD";

    /** 质控每日批算的回溯天数（默认 3 天，见 QualityDailyTask） */
    String KEY_QUALITY_BACKFILL_DAYS = "QUALITY_BACKFILL_DAYS";

    /** 疑似感染列表「待决策」判定规则（见 AntibioticDecisionServiceImpl） */
    String KEY_ABX_PENDING_RULE = "ABX_PENDING_DECISION_RULE";

    /** 待决策规则取值一：当天没有决策记录即计入（含从未决策） */
    String ABX_PENDING_TODAY = "TODAY_NO_DECISION";

    /** 待决策规则取值二：入科满 24 小时且从未做过抗感染决策才计入 */
    String ABX_PENDING_ADMIT_24H = "ADMIT_24H_NEVER";

    /** 自动注册口令规则的两种取值 */
    String PWD_RULE_WORK_NO = "WORK_NO";
    String PWD_RULE_FIXED = "FIXED";

    /**
     * 参数列表；paramGroup 为空返回全部。
     */
    List<SysParam> list(String paramGroup);

    /**
     * 保存（id 为空新增，否则更新）。
     */
    boolean save(SysParam param);

    /**
     * 删除。
     */
    boolean delete(Long id);

    /**
     * 取归档接口地址；未配置或已停用返回 null。
     */
    String getArchiveApiUrl();

    /**
     * 取归档目录规则（含 #占位符#）；未配置或已停用返回 null。
     */
    String getArchiveDir();

    /**
     * 取任意启用中的参数值（通用入口，带缓存）。
     *
     * <p>新模块要读参数统一走这里，不必再各写一个查询方法。
     *
     * @return 未配置、已停用、空串均返回 null
     */
    String value(String paramKey);

    /**
     * 取开关型参数：值等于 1 / true / on / yes（忽略大小写）视为开。
     *
     * @param defaultValue 参数未配置时的取值
     */
    boolean bool(String paramKey, boolean defaultValue);

    /** 外链工号自动注册是否已开启（默认关） */
    boolean isAutoRegisterEnabled();

    /** 自动注册口令规则：WORK_NO / FIXED，未配置时按 WORK_NO */
    String getRegisterPasswordRule();

    /** 固定初始口令；为空时应按「口令同工号」处理 */
    String getRegisterFixedPassword();
}
