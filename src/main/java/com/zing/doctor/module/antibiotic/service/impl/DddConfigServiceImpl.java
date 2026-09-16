package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zing.doctor.module.antibiotic.entity.DddConfig;
import com.zing.doctor.module.antibiotic.mapper.DddConfigMapper;
import com.zing.doctor.module.antibiotic.service.DddConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抗菌药物 DDD 值配置 Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DddConfigServiceImpl extends ServiceImpl<DddConfigMapper, DddConfig> implements DddConfigService {

    @Override
    public List<DddConfig> listAllActive() {
        return baseMapper.selectAllActive();
    }

    @Override
    public List<DddConfig> listByDrugClass(String drugClass) {
        return baseMapper.selectByDrugClass(drugClass);
    }

    @Override
    public List<DddConfig> listByManageLevel(String manageLevel) {
        return baseMapper.selectByManageLevel(manageLevel);
    }

    @Override
    public DddConfig matchByAdviceName(String adviceName) {
        if (adviceName == null || adviceName.trim().isEmpty()) {
            return null;
        }
        List<DddConfig> all = listAllActive();
        // 先匹配药品通用名（精确包含）
        for (DddConfig config : all) {
            if (adviceName.contains(config.getDrugName())) {
                return config;
            }
        }
        // 再匹配关键词
        for (DddConfig config : all) {
            String keywords = config.getKeywords();
            if (keywords != null && !keywords.isEmpty()) {
                for (String kw : keywords.split(",")) {
                    if (kw != null && !kw.trim().isEmpty() && adviceName.contains(kw.trim())) {
                        return config;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public BigDecimal calcDdds(String drugName, BigDecimal totalDose) {
        if (drugName == null || totalDose == null) {
            return BigDecimal.ZERO;
        }
        DddConfig config = matchByAdviceName(drugName);
        if (config == null || config.getDddValue() == null || config.getDddValue().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalDose.divide(config.getDddValue(), 4, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public List<String> listAllDrugClasses() {
        return listAllActive().stream()
                .map(DddConfig::getDrugClass)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listAllManageLevels() {
        return Arrays.asList("非限制", "限制", "特殊");
    }

    @Override
    public Map<String, Long> countByDrugClass() {
        Map<String, Long> result = new LinkedHashMap<>();
        listAllActive().stream()
                .collect(Collectors.groupingBy(DddConfig::getDrugClass, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    @Override
    public boolean addConfig(DddConfig config) {
        config.setId(null);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        return save(config);
    }

    @Override
    public boolean updateConfig(DddConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }

    @Override
    public boolean toggleStatus(Long id, Integer status) {
        DddConfig config = new DddConfig();
        config.setId(id);
        config.setStatus(status);
        config.setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }
}
