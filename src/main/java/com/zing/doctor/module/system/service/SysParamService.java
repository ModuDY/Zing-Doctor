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
}
