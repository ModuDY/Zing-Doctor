package com.zing.doctor.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class BuildInfoControllerTest {

    @Test
    @DisplayName("返回构建信息及运行环境信息")
    void returnsInjectedBuildMetadata() {
        BuildInfoController controller = new BuildInfoController();
        setField(controller, "buildVersion", "1.2.3");
        setField(controller, "gitCommit", "abc1234");
        setField(controller, "buildTime", "2026-09-26T10:00:00Z");
        setField(controller, "activeProfile", "prod");
        setField(controller, "icuDataProvider", "sql");
        Map<String, Object> data = controller.buildInfo().getData();
        assertEquals("zing-doctor", data.get("application"));
        assertEquals("1.2.3", data.get("version"));
        assertEquals("abc1234", data.get("gitCommit"));
        assertEquals("2026-09-26T10:00:00Z", data.get("buildTime"));
        assertEquals("prod", data.get("activeProfile"));
        assertEquals("sql", data.get("icuDataProvider"));
        assertNotNull(data.get("serverTime"));
    }

    @Test
    @DisplayName("未注入构建元数据时明确返回 unknown，未配置 profile 时返回 default")
    void defaultsAreHonestWhenMetadataIsUnknown() {
        BuildInfoController controller = new BuildInfoController();
        setField(controller, "buildVersion", "unknown");
        setField(controller, "gitCommit", "unknown");
        setField(controller, "buildTime", "unknown");
        setField(controller, "activeProfile", " ");
        setField(controller, "icuDataProvider", "mock");
        Map<String, Object> data = controller.buildInfo().getData();
        assertEquals("unknown", data.get("version"));
        assertEquals("unknown", data.get("gitCommit"));
        assertEquals("unknown", data.get("buildTime"));
        assertEquals("default", data.get("activeProfile"));
        assertEquals("mock", data.get("icuDataProvider"));
    }
}
