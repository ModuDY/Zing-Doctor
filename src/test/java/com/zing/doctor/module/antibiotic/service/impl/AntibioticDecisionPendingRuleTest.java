package com.zing.doctor.module.antibiotic.service.impl;

import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.mapper.AdviceLogMapper;
import com.zing.doctor.module.antibiotic.mapper.DecisionRecordMapper;
import com.zing.doctor.module.system.service.SysParamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「待决策」两种口径（参数 ABX_PENDING_DECISION_RULE）。
 *
 * <p>这两个口径是两套排班习惯，改错一个就会把整科患者挂成待决策、或者该盯的人漏掉：
 * <ul>
 *   <li>TODAY_NO_DECISION：当天没有决策记录就算，含从未决策的——晨间把全科过一遍；</li>
 *   <li>ADMIT_24H_NEVER：入科满 24 小时且从未决策才算——新入科先观察，
 *       否则凌晨入科的患者一进列表就挂在待决策里，夜间没人看只是噪音。</li>
 * </ul>
 */
class AntibioticDecisionPendingRuleTest {

    private IcuPatientBrief patient(LocalDateTime inDepartTime, boolean decidedToday) {
        IcuPatientBrief p = new IcuPatientBrief();
        p.setPatientId("P1");
        p.setInDepartTime(inDepartTime);
        if (decidedToday) {
            p.setDecisionStatus("accepted");
            p.setDecisionTime(LocalDateTime.now().minusHours(1));
        }
        return p;
    }

    @Test
    @DisplayName("TODAY_NO_DECISION：今天已决策的不算，其余都算（含新入科）")
    void todayRule() {
        IcuPatientBrief decided = patient(LocalDateTime.now().minusDays(3), true);
        IcuPatientBrief fresh = patient(LocalDateTime.now().minusHours(2), false);
        IcuPatientBrief old = patient(LocalDateTime.now().minusDays(5), false);
        assertFalse(pending("TODAY_NO_DECISION", decided), "今天已决策过就不该再挂待决策");
        assertTrue(pending("TODAY_NO_DECISION", fresh), "新入科当天无决策也应算待决策");
        assertTrue(pending("TODAY_NO_DECISION", old));
    }

    @Test
    @DisplayName("ADMIT_24H_NEVER：入科不满 24 小时不算，满 24 小时且从未决策才算")
    void admit24hRule() {
        IcuPatientBrief fresh = patient(LocalDateTime.now().minusHours(2), false);
        IcuPatientBrief old = patient(LocalDateTime.now().minusDays(2), false);
        IcuPatientBrief decidedOld = patient(LocalDateTime.now().minusDays(2), true);
        assertFalse(pending("ADMIT_24H_NEVER", fresh), "刚入科的患者不该立刻挂待决策");
        assertTrue(pending("ADMIT_24H_NEVER", old));
        assertFalse(pending("ADMIT_24H_NEVER", decidedOld), "已经决策过的不再提醒");
    }

    @Test
    @DisplayName("入科时间缺失：ADMIT_24H_NEVER 下不计入（宁可漏提醒，也不整科挂红）")
    void missingInDepartTime() {
        IcuPatientBrief p = patient(null, false);
        assertTrue(pending("TODAY_NO_DECISION", p));
        assertFalse(pending("ADMIT_24H_NEVER", p));
    }

    @Test
    @DisplayName("参数未配置 / 停用 → 回退默认口径 TODAY_NO_DECISION")
    void fallsBackToDefault() {
        IcuPatientBrief decided = patient(LocalDateTime.now().minusDays(3), true);
        assertFalse(pending(null, decided), "未配置参数时按默认口径，今天已决策即不算待决策");
    }

    /** 走公开入口，顺带覆盖「决策状态回填 + 待决策打标」整条链路 */
    private boolean pending(String rule, IcuPatientBrief p) {
        IcuPatientService icu = mock(IcuPatientService.class);
        when(icu.listSuspectInfections(any())).thenReturn(Collections.singletonList(p));
        AntibioticDecisionServiceImpl service =
                new AntibioticDecisionServiceImpl(icu, decisionMapperOf(), mock(AdviceLogMapper.class), paramOf(rule));
        List<IcuPatientBrief> list = service.listSuspectPatients("D001");
        return Boolean.TRUE.equals(list.get(0).getPendingDecision());
    }

    private DecisionRecordMapper decisionMapperOf() {
        DecisionRecordMapper m = mock(DecisionRecordMapper.class);
        when(m.selectList(any())).thenReturn(Collections.emptyList());
        return m;
    }

    private SysParamService paramOf(String rule) {
        SysParamService p = mock(SysParamService.class);
        when(p.value(eq(SysParamService.KEY_ABX_PENDING_RULE))).thenReturn(rule);
        return p;
    }
}
