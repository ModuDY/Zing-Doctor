package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.antibiotic.entity.MdroConfig;
import com.zing.doctor.module.antibiotic.mapper.MdroConfigMapper;
import com.zing.doctor.module.antibiotic.service.MdroConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 第四维度：细菌培养监测配置管理 Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdroConfigServiceImpl implements MdroConfigService {

    private final MdroConfigMapper mdroConfigMapper;

    @Override
    public List<MdroConfig> listAllActive() {
        return mdroConfigMapper.selectList(
                new LambdaQueryWrapper<MdroConfig>()
                        .eq(MdroConfig::getStatus, 1)
                        .orderByAsc(MdroConfig::getConfigType)
                        .orderByAsc(MdroConfig::getBacteriaName));
    }

    @Override
    public List<MdroConfig> listByConfigType(String configType) {
        return mdroConfigMapper.selectList(
                new LambdaQueryWrapper<MdroConfig>()
                        .eq(MdroConfig::getConfigType, configType)
                        .eq(MdroConfig::getStatus, 1)
                        .orderByAsc(MdroConfig::getBacteriaName));
    }

    @Override
    public List<MdroConfig> listByBacteriaClass(String bacteriaClass) {
        return mdroConfigMapper.selectList(
                new LambdaQueryWrapper<MdroConfig>()
                        .eq(MdroConfig::getConfigType, "bacteria_class")
                        .eq(MdroConfig::getBacteriaClass, bacteriaClass)
                        .eq(MdroConfig::getStatus, 1)
                        .orderByAsc(MdroConfig::getBacteriaName));
    }

    @Override
    public List<MdroConfig> listHighRiskBacteria() {
        return mdroConfigMapper.selectList(
                new LambdaQueryWrapper<MdroConfig>()
                        .eq(MdroConfig::getConfigType, "high_risk")
                        .eq(MdroConfig::getStatus, 1)
                        .orderByAsc(MdroConfig::getBacteriaName));
    }

    @Override
    public List<String> listAllBacteriaClasses() {
        List<MdroConfig> configs = mdroConfigMapper.selectList(
                new LambdaQueryWrapper<MdroConfig>()
                        .eq(MdroConfig::getConfigType, "bacteria_class")
                        .eq(MdroConfig::getStatus, 1)
                        .select(MdroConfig::getBacteriaClass)
                        .groupBy(MdroConfig::getBacteriaClass));
        return configs.stream()
                .map(MdroConfig::getBacteriaClass)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean addConfig(MdroConfig config) {
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        if (config.getIsHighRisk() == null) {
            config.setIsHighRisk(0);
        }
        return mdroConfigMapper.insert(config) > 0;
    }

    @Override
    public boolean updateConfig(MdroConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return mdroConfigMapper.updateById(config) > 0;
    }

    @Override
    public boolean toggleStatus(Long id, Integer status) {
        MdroConfig config = new MdroConfig();
        config.setId(id);
        config.setStatus(status);
        config.setUpdateTime(LocalDateTime.now());
        return mdroConfigMapper.updateById(config) > 0;
    }

    @Override
    public String matchBacteriaClass(String bacteriaName) {
        if (bacteriaName == null || bacteriaName.trim().isEmpty()) {
            return "other";
        }
        String name = bacteriaName.trim();
        List<MdroConfig> configs = listByConfigType("bacteria_class");
        for (MdroConfig config : configs) {
            // 先精确匹配细菌名称
            if (name.equals(config.getBacteriaName())) {
                return config.getBacteriaClass();
            }
            // 再按关键词模糊匹配
            if (config.getKeywords() != null && !config.getKeywords().isEmpty()) {
                String[] keywords = config.getKeywords().split(",");
                for (String keyword : keywords) {
                    if (keyword != null && !keyword.trim().isEmpty()
                            && name.contains(keyword.trim())) {
                        return config.getBacteriaClass();
                    }
                }
            }
        }
        return "other";
    }

    @Override
    public boolean isHighRiskBacteria(String bacteriaName) {
        if (bacteriaName == null || bacteriaName.trim().isEmpty()) {
            return false;
        }
        String name = bacteriaName.trim();
        List<MdroConfig> highRiskList = listHighRiskBacteria();
        for (MdroConfig config : highRiskList) {
            // 精确匹配
            if (name.equals(config.getBacteriaName())) {
                return true;
            }
            // 关键词模糊匹配
            if (config.getKeywords() != null && !config.getKeywords().isEmpty()) {
                String[] keywords = config.getKeywords().split(",");
                for (String keyword : keywords) {
                    if (keyword != null && !keyword.trim().isEmpty()
                            && name.contains(keyword.trim())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
