package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.dto.AntibioticReassessmentRequest;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;
import com.zing.doctor.module.antibiotic.mapper.AntibioticReassessmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AntibioticReassessmentServiceImplTest {

    private AntibioticReassessmentMapper mapper;
    private AntibioticReassessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AntibioticReassessmentMapper.class);
        service = new AntibioticReassessmentServiceImpl(mapper, mock(IcuPatientService.class));
    }

    @Test
    @DisplayName("缺少任务或任务已处理时拒绝复评")
    void rejectsMissingOrProcessedTask() {
        assertThrows(BizException.class, () -> service.complete(null));
        AntibioticReassessmentRequest noId = new AntibioticReassessmentRequest();
        assertThrows(BizException.class, () -> service.complete(noId));
        when(mapper.selectById(7L)).thenReturn(record(AntibioticReassessmentServiceImpl.COMPLETED));
        assertThrows(BizException.class, () -> service.complete(request(7L, "医生", "CONTINUE")));
        verify(mapper, never()).updateById(any(AntibioticReassessment.class));
    }

    @Test
    @DisplayName("完成待复评任务：必须有医生和动作，并保留填写内容")
    void completesPendingTask() {
        when(mapper.selectById(7L)).thenReturn(record(AntibioticReassessmentServiceImpl.PENDING));
        AntibioticReassessmentRequest request = request(7L, " 医生 ", " CONTINUE ");
        request.setClinicalResponse(" 好转 ");
        AntibioticReassessment result = service.complete(request);
        assertEquals(AntibioticReassessmentServiceImpl.COMPLETED, result.getReviewStatus());
        assertEquals("医生", result.getDoctorName());
        assertEquals("CONTINUE", result.getDecisionAction());
        assertEquals("好转", result.getClinicalResponse());
        assertNotNull(result.getReviewTime());
        verify(mapper).updateById(result);
    }

    @Test
    @DisplayName("完成任务缺医生或复评动作时失败")
    void requiresDoctorAndAction() {
        when(mapper.selectById(7L)).thenReturn(record(AntibioticReassessmentServiceImpl.PENDING));
        assertThrows(BizException.class, () -> service.complete(request(7L, "", "CONTINUE")));
        assertThrows(BizException.class, () -> service.complete(request(7L, "医生", " ")));
        verify(mapper, never()).updateById(any(AntibioticReassessment.class));
    }

    @Test
    @DisplayName("跳过复评必须填写医生和原因")
    void skipRequiresDoctorAndReason() {
        when(mapper.selectById(7L)).thenReturn(record(AntibioticReassessmentServiceImpl.PENDING));
        AntibioticReassessmentRequest missingDoctor = request(7L, "", null);
        missingDoctor.setRemark("原因");
        assertThrows(BizException.class, () -> service.skip(missingDoctor));
        AntibioticReassessmentRequest missingReason = request(7L, "医生", null);
        assertThrows(BizException.class, () -> service.skip(missingReason));
        verify(mapper, never()).updateById(any(AntibioticReassessment.class));
    }

    @Test
    @DisplayName("跳过任务后状态和原因落库")
    void skipsPendingTask() {
        when(mapper.selectById(7L)).thenReturn(record(AntibioticReassessmentServiceImpl.PENDING));
        AntibioticReassessmentRequest request = request(7L, "医生", null);
        request.setRemark("患者转科");
        AntibioticReassessment result = service.skip(request);
        assertEquals(AntibioticReassessmentServiceImpl.SKIPPED, result.getReviewStatus());
        assertEquals("患者转科", result.getRemark());
        verify(mapper).updateById(result);
    }

    @Test
    @DisplayName("相同决策记录重复创建时复用已有待办")
    void createPendingIsIdempotentForExistingPendingTask() {
        AntibioticReassessment existing = record(AntibioticReassessmentServiceImpl.PENDING);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(existing));
        assertSame(existing, service.createPending(10L, "P1", "N1", "H1", "D1"));
        verify(mapper, never()).insert(any(AntibioticReassessment.class));
    }

    @Test
    @DisplayName("新决策创建带 48 小时到期时间的待复评任务")
    void createsPendingTask() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(mapper.insert(any(AntibioticReassessment.class))).thenAnswer(invocation -> {
            AntibioticReassessment inserted = invocation.getArgument(0);
            inserted.setId(11L);
            return 1;
        });
        AntibioticReassessment created = service.createPending(10L, "P1", "N1", "H1", "D1");
        assertEquals(10L, created.getDecisionRecordId());
        assertEquals(AntibioticReassessmentServiceImpl.PENDING, created.getReviewStatus());
        assertEquals(0, created.getVoidFlag());
        assertNotNull(created.getReviewDueTime());
        verify(mapper).insert(created);
    }

    @Test
    @DisplayName("无效决策 ID 不触发作废更新")
    void ignoresNullDecisionWhenVoiding() {
        service.voidPendingByDecision(null);
        verify(mapper, never()).update(any(AntibioticReassessment.class), any(Wrapper.class));
    }

    private AntibioticReassessment record(String status) {
        AntibioticReassessment record = new AntibioticReassessment();
        record.setId(7L);
        record.setReviewStatus(status);
        record.setVoidFlag(0);
        return record;
    }

    private AntibioticReassessmentRequest request(Long id, String doctor, String action) {
        AntibioticReassessmentRequest request = new AntibioticReassessmentRequest();
        request.setId(id);
        request.setDoctorName(doctor);
        request.setDecisionAction(action);
        return request;
    }
}
