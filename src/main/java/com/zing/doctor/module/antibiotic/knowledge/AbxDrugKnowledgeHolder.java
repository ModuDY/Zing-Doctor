package com.zing.doctor.module.antibiotic.knowledge;

import com.zing.doctor.module.antibiotic.entity.AbxPkpdKnowledge;
import com.zing.doctor.module.antibiotic.service.AbxPkpdKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 把数据库版 PK/PD 知识库 Service 注入到枚举 AbxDrugKnowledge 的静态 holder，
 * 使 AbxDrugKnowledge.match() 能优先查数据库（界面化配置），未命中再 fallback 到内置枚举。
 *
 * <p>枚举本身不能被 Spring 管理，故用这个 @Component 做桥接。
 */
@Slf4j
@Component
public class AbxDrugKnowledgeHolder {

    private static AbxPkpdKnowledgeService service;

    private final AbxPkpdKnowledgeService abxPkpdKnowledgeService;

    public AbxDrugKnowledgeHolder(AbxPkpdKnowledgeService abxPkpdKnowledgeService) {
        this.abxPkpdKnowledgeService = abxPkpdKnowledgeService;
    }

    @PostConstruct
    public void init() {
        service = abxPkpdKnowledgeService;
        log.info("[PKPD知识库] 数据库匹配源已挂载，界面配置优先于内置枚举");
    }

    /** 供枚举调用：查数据库，未命中返回 null */
    public static AbxPkpdKnowledge matchFromDb(String drugName) {
        if (service == null) return null;
        try {
            return service.match(drugName);
        } catch (Exception e) {
            log.warn("[PKPD知识库] 数据库匹配异常，回退内置枚举: {}", e.getMessage());
            return null;
        }
    }
}
