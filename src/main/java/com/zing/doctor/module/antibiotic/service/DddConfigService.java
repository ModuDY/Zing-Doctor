package com.zing.doctor.module.antibiotic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zing.doctor.module.antibiotic.entity.DddConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 抗菌药物 DDD 值配置 Service。
 */
public interface DddConfigService extends IService<DddConfig> {

    /** 查询所有启用的 DDD 配置 */
    List<DddConfig> listAllActive();

    /** 按药物分类查询 */
    List<DddConfig> listByDrugClass(String drugClass);

    /** 按管理级别查询 */
    List<DddConfig> listByManageLevel(String manageLevel);

    /** 根据医嘱名称匹配 DDD 配置（模糊匹配关键词） */
    DddConfig matchByAdviceName(String adviceName);

    /** 计算某药品的 DDDs = 总剂量 / DDD值 */
    BigDecimal calcDdds(String drugName, BigDecimal totalDose);

    /** 获取所有药物分类列表 */
    List<String> listAllDrugClasses();

    /** 获取所有管理级别列表 */
    List<String> listAllManageLevels();

    /** 按分类分组统计数量 */
    Map<String, Long> countByDrugClass();

    /** 新增 DDD 配置 */
    boolean addConfig(DddConfig config);

    /** 修改 DDD 配置 */
    boolean updateConfig(DddConfig config);

    /** 停用/启用 DDD 配置 */
    boolean toggleStatus(Long id, Integer status);
}
