package com.zing.doctor.module.antibiotic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zing.doctor.module.antibiotic.entity.AbxWordConfig;

import java.util.List;

/**
 * 抗菌药物识别词库配置 Service
 */
public interface AbxWordConfigService extends IService<AbxWordConfig> {

    /** 全部启用词条 */
    List<AbxWordConfig> listAllActive();

    /** 按类型取启用关键词列表（broad_spectrum / non_antibiotic），表空返回 null */
    List<String> listEnabledKeywords(String wordType);

    /** 分类列表 */
    List<String> listAllCategories();

    boolean addConfig(AbxWordConfig config);

    boolean updateConfig(AbxWordConfig config);

    boolean toggleStatus(Long id, Integer status);
}
