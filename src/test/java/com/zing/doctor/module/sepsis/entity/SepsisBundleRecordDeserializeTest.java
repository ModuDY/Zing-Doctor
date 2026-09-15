package com.zing.doctor.module.sepsis.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 脓毒症集束化治疗「保存」请求体反序列化回归测试。
 *
 * <p>受保护的行为：前端 {@code SepsisBundle.vue} 保存时把「记录时间」当业务字段提交
 * （{@code createTime}，医生可回改、用于补录过去的评估），格式统一为
 * {@code yyyy-MM-dd HH:mm:ss}（空格分隔）。
 *
 * <p>而 Jackson 对 {@code LocalDateTime} 默认只认 ISO（{@code 2026-09-16T14:30:00}），
 * 实体漏标 {@code @JsonFormat} 时，请求体会在进入 Controller 之前反序列化失败，
 * 表现为 400「提交的数据格式有误」—— Controller 内的 try-catch 完全拦不到，
 * 且报错文案看不出是时间字段的问题。此处锁定该契约，防止注解被误删后问题复发。
 */
class SepsisBundleRecordDeserializeTest {

    /** 与 Spring Boot 默认的 LocalDateTime 处理一致；未知字段报错保持开启，顺带校验字段名未漂移。 */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 完整复刻前端 onSave() 的载荷形状。
     */
    private static final String PAYLOAD = "{"
            + "\"id\":null,"
            + "\"patientId\":\"P001\","
            + "\"inHospitalNo\":\"600123\","
            + "\"patientName\":\"张三\","
            + "\"departCode\":\"ICU\","
            + "\"createTime\":\"2026-09-16 14:30:00\","
            + "\"diagnosisTime\":\"2026-09-16 10:00:00\","
            + "\"inDepartTime\":\"2026-09-15 08:00:00\","
            + "\"bundle1hCompleted\":1,"
            + "\"bundle3hCompleted\":0,"
            + "\"bundle6hCompleted\":0,"
            + "\"bundle1hData\":\"{\\\"lactateMeasured\\\":true}\","
            + "\"bundle3hData\":\"{\\\"lactateMeasured\\\":false}\","
            + "\"bundle6hData\":\"{\\\"vasopressor\\\":false}\","
            + "\"infectionSite\":\"肺部\","
            + "\"pathogen\":\"肺炎克雷伯菌\","
            + "\"antibiotic\":\"美罗培南\","
            + "\"fluidReason\":null"
            + "}";

    @Test
    @DisplayName("保存载荷：空格分隔的 yyyy-MM-dd HH:mm:ss 时间字段能被正确反序列化")
    void deserializeFrontendSavePayload() throws Exception {
        SepsisBundleRecord record = mapper.readValue(PAYLOAD, SepsisBundleRecord.class);

        assertEquals(LocalDateTime.of(2026, 9, 16, 14, 30, 0), record.getCreateTime(),
                "createTime（记录时间）是业务字段，医生可回改，必须能被前端格式解析");
        assertEquals(LocalDateTime.of(2026, 9, 16, 10, 0, 0), record.getDiagnosisTime());
        assertEquals(LocalDateTime.of(2026, 9, 15, 8, 0, 0), record.getInDepartTime());
    }

    @Test
    @DisplayName("保存载荷：集束化完成标记与勾选枚举值原样入库")
    void deserializeBundleFlags() throws Exception {
        SepsisBundleRecord record = mapper.readValue(PAYLOAD, SepsisBundleRecord.class);

        assertEquals(Integer.valueOf(1), record.getBundle1hCompleted());
        assertEquals(Integer.valueOf(0), record.getBundle3hCompleted());
        assertEquals(Integer.valueOf(0), record.getBundle6hCompleted());
        // 三块内容走 JSON 字符串；感染部位/致病菌/抗菌药物是「、」拼接的枚举值
        assertEquals("肺部", record.getInfectionSite());
        assertEquals("肺炎克雷伯菌", record.getPathogen());
        assertEquals("美罗培南", record.getAntibiotic());
        assertEquals("{\"lactateMeasured\":true}", record.getBundle1hData());
    }

    @Test
    @DisplayName("更新载荷：updateTime 同样按前端格式解析（编辑保存走 /update）")
    void deserializeUpdateTime() throws Exception {
        String json = "{\"id\":1,\"createTime\":\"2026-09-16 14:30:00\",\"updateTime\":\"2026-09-16 15:00:00\"}";

        SepsisBundleRecord record = mapper.readValue(json, SepsisBundleRecord.class);

        assertEquals(LocalDateTime.of(2026, 9, 16, 14, 30, 0), record.getCreateTime());
        assertEquals(LocalDateTime.of(2026, 9, 16, 15, 0, 0), record.getUpdateTime());
    }
}
