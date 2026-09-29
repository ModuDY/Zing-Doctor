package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.entity.AbxPkpdKnowledge;

import java.util.List;

/**
 * PK/PD 抗菌药物知识库 Service。
 */
public interface AbxPkpdKnowledgeService {

    List<AbxPkpdKnowledge> listAll();

    AbxPkpdKnowledge getById(Long id);

    boolean save(AbxPkpdKnowledge entity);

    boolean update(AbxPkpdKnowledge entity);

    boolean removeById(Long id);

    /** 匹配药物（优先数据库，未命中返回 null） */
    AbxPkpdKnowledge match(String drugName);
}
