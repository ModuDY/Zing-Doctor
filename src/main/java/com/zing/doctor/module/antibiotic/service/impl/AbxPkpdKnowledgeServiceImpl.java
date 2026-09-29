package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.antibiotic.entity.AbxPkpdKnowledge;
import com.zing.doctor.module.antibiotic.mapper.AbxPkpdKnowledgeMapper;
import com.zing.doctor.module.antibiotic.service.AbxPkpdKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PK/PD 抗菌药物知识库 Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbxPkpdKnowledgeServiceImpl implements AbxPkpdKnowledgeService {

    private final AbxPkpdKnowledgeMapper mapper;

    @Override
    public List<AbxPkpdKnowledge> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<AbxPkpdKnowledge>()
                .eq(AbxPkpdKnowledge::getStatus, 1)
                .orderByAsc(AbxPkpdKnowledge::getDrugName));
    }

    @Override
    public AbxPkpdKnowledge getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public boolean save(AbxPkpdKnowledge entity) {
        if (entity.getStatus() == null) entity.setStatus(1);
        return mapper.insert(entity) > 0;
    }

    @Override
    public boolean update(AbxPkpdKnowledge entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 匹配药物：精确 → contains → 模糊（字符重合度≥60%）。
     * 与内置枚举 AbxDrugKnowledge.match 的规则保持一致。
     */
    @Override
    public AbxPkpdKnowledge match(String drugName) {
        if (drugName == null || drugName.trim().isEmpty()) return null;
        String name = drugName.trim();
        List<AbxPkpdKnowledge> all = listAll();
        if (all == null || all.isEmpty()) return null;

        // 1. 精确匹配
        for (AbxPkpdKnowledge k : all) {
            if (name.equals(k.getDrugName())) return k;
        }
        // 2. contains 匹配
        for (AbxPkpdKnowledge k : all) {
            if (name.contains(k.getDrugName()) || k.getDrugName().contains(name)) return k;
        }
        // 3. 模糊匹配（字符重合度≥60%）
        for (AbxPkpdKnowledge k : all) {
            String kn = k.getDrugName();
            if (kn.length() < 2) continue;
            int matchCount = 0;
            for (char c : kn.toCharArray()) {
                if (name.indexOf(c) >= 0) matchCount++;
            }
            if (matchCount >= kn.length() * 0.6) return k;
        }
        return null;
    }
}
