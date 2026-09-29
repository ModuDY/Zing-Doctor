package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zing.doctor.module.antibiotic.entity.AbxWordConfig;
import com.zing.doctor.module.antibiotic.mapper.AbxWordConfigMapper;
import com.zing.doctor.module.antibiotic.service.AbxWordConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 抗菌药物识别词库配置 Service 实现。
 */
@Slf4j
@Service
public class AbxWordConfigServiceImpl extends ServiceImpl<AbxWordConfigMapper, AbxWordConfig> implements AbxWordConfigService {

    @Override
    public List<AbxWordConfig> listAllActive() {
        LambdaQueryWrapper<AbxWordConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AbxWordConfig::getStatus, 1)
                .orderByAsc(AbxWordConfig::getWordType)
                .orderByAsc(AbxWordConfig::getCategory)
                .orderByAsc(AbxWordConfig::getKeyword);
        return list(wrapper);
    }

    @Override
    public List<String> listEnabledKeywords(String wordType) {
        LambdaQueryWrapper<AbxWordConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AbxWordConfig::getWordType, wordType)
                .eq(AbxWordConfig::getStatus, 1);
        List<AbxWordConfig> list = list(wrapper);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream()
                .map(AbxWordConfig::getKeyword)
                .filter(kw -> kw != null && !kw.trim().isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listAllCategories() {
        return listAllActive().stream()
                .map(AbxWordConfig::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public boolean addConfig(AbxWordConfig config) {
        config.setId(null);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        return save(config);
    }

    @Override
    public boolean updateConfig(AbxWordConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }

    @Override
    public boolean toggleStatus(Long id, Integer status) {
        AbxWordConfig config = new AbxWordConfig();
        config.setId(id);
        config.setStatus(status);
        config.setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }
}
