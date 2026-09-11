package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.entity.MdroConfig;

import java.util.List;

/**
 * 第四维度：细菌培养监测配置管理 Service。
 */
public interface MdroConfigService {

    /** 查询所有启用的配置 */
    List<MdroConfig> listAllActive();

    /** 按配置类型查询 */
    List<MdroConfig> listByConfigType(String configType);

    /** 按细菌分类查询 */
    List<MdroConfig> listByBacteriaClass(String bacteriaClass);

    /** 查询高风险细菌列表 */
    List<MdroConfig> listHighRiskBacteria();

    /** 查询所有细菌分类 */
    List<String> listAllBacteriaClasses();

    /** 新增配置 */
    boolean addConfig(MdroConfig config);

    /** 修改配置 */
    boolean updateConfig(MdroConfig config);

    /** 切换状态 */
    boolean toggleStatus(Long id, Integer status);

    /**
     * 根据细菌名称匹配分类（革兰阳性/阴性/真菌/其他）。
     * 匹配规则：按配置表中的 keywords 关键词模糊匹配。
     */
    String matchBacteriaClass(String bacteriaName);

    /**
     * 判断细菌是否为高风险细菌。
     */
    boolean isHighRiskBacteria(String bacteriaName);
}
