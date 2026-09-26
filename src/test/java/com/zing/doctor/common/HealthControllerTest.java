package com.zing.doctor.common;

import com.zing.doctor.common.mapper.HealthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {

    private HealthMapper mapper;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        mapper = mock(HealthMapper.class);
        controller = new HealthController(mapper);
        ReflectionTestUtils.setField(controller, "icuDataProvider", "sql");
    }

    @Test
    @DisplayName("医生库、复评表和 ICU 库均正常时返回 UP")
    void returnsUpWhenAllDependenciesAreReady() {
        when(mapper.pingDoctor()).thenReturn(1);
        when(mapper.checkReassessmentTable()).thenReturn(0);
        when(mapper.pingIcu()).thenReturn(1);
        ResponseEntity<Result<Map<String, Object>>> response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().getData().get("status"));
        assertEquals("UP", response.getBody().getData().get("doctorDatabase"));
        assertEquals("READY", response.getBody().getData().get("reassessmentSchema"));
        assertEquals("UP", response.getBody().getData().get("icuDatabase"));
        assertNotNull(response.getBody().getData().get("time"));
    }

    @Test
    @DisplayName("医生库失败返回 503，并保留完整探针字段")
    void doctorDatabaseFailureReturnsDetailed503() {
        when(mapper.pingDoctor()).thenThrow(new RuntimeException("db unavailable"));
        when(mapper.pingIcu()).thenReturn(1);
        ResponseEntity<Result<Map<String, Object>>> response = controller.health();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().getCode());
        Map<String, Object> data = response.getBody().getData();
        assertEquals("DOWN", data.get("status"));
        assertEquals("DOWN", data.get("doctorDatabase"));
        assertEquals("MISSING_OR_UNAVAILABLE", data.get("reassessmentSchema"));
        assertEquals("UP", data.get("icuDatabase"));
        assertNotNull(data.get("time"));
        verify(mapper, never()).checkReassessmentTable();
    }

    @Test
    @DisplayName("复评表缺失返回 503")
    void missingReassessmentTableReturns503() {
        when(mapper.pingDoctor()).thenReturn(1);
        when(mapper.checkReassessmentTable()).thenThrow(new RuntimeException("table missing"));
        when(mapper.pingIcu()).thenReturn(1);
        ResponseEntity<Result<Map<String, Object>>> response = controller.health();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().getData().get("status"));
        assertEquals("MISSING_OR_UNAVAILABLE", response.getBody().getData().get("reassessmentSchema"));
    }

    @Test
    @DisplayName("Mock ICU provider 跳过 ICU 数据库探测")
    void mockProviderSkipsIcuProbe() {
        when(mapper.pingDoctor()).thenReturn(1);
        when(mapper.checkReassessmentTable()).thenReturn(0);
        ReflectionTestUtils.setField(controller, "icuDataProvider", "mock");
        ResponseEntity<Result<Map<String, Object>>> response = controller.health();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SKIPPED_MOCK", response.getBody().getData().get("icuDatabase"));
        verify(mapper, never()).pingIcu();
    }
}
