package com.zing.doctor.common;

import com.zing.doctor.common.mapper.HealthMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存活/就绪探针：无参数、无鉴权，检查医生库、复评表和 ICU 数据源，
 * 供容器 healthcheck 与现场部署验收使用。
 *
 * <p>此前 docker-compose 的 healthcheck 与 install.sh 的就绪探测都打的是
 * {@code /api/external/token} —— 那是外链签发接口，必填 pageCode，探针不带参数，
 * 于是每隔 30 秒触发一次「缺少请求参数」异常：既在日志里刷 ERROR 堆栈，
 * 又因兜底返回 500 让 {@code curl -f} 判定失败，容器被标记 unhealthy。
 * 探针应打本接口。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HealthMapper healthMapper;

    @Value("${zing.doctor.icu-data-provider:sql}")
    private String icuDataProvider;

    @GetMapping("/api/health")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean doctorDb = probe("doctor", healthMapper::pingDoctor);
        boolean reassessmentSchema = doctorDb && probe("reassessment-schema", healthMapper::checkReassessmentTable);
        boolean mockIcu = "mock".equalsIgnoreCase(icuDataProvider);
        boolean icuDb = mockIcu || probe("icu", healthMapper::pingIcu);
        boolean ready = doctorDb && reassessmentSchema && icuDb;

        data.put("status", ready ? "UP" : "DOWN");
        data.put("time", LocalDateTime.now().format(FMT));
        data.put("doctorDatabase", doctorDb ? "UP" : "DOWN");
        data.put("reassessmentSchema", reassessmentSchema ? "READY" : "MISSING_OR_UNAVAILABLE");
        data.put("icuDataProvider", icuDataProvider);
        data.put("icuDatabase", mockIcu ? "SKIPPED_MOCK" : (icuDb ? "UP" : "DOWN"));
        Result<Map<String, Object>> result = Result.ok(data);
        if (!ready) {
            // DOWN 也返回探针明细，现场自检页才能定位具体失败项。
            result.setCode(503);
            result.setMessage("服务未就绪");
        }
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    private boolean probe(String name, java.util.function.Supplier<Integer> action) {
        try {
            Integer result = action.get();
            return result != null;
        } catch (Exception e) {
            log.warn("健康检查失败：{}，原因={}", name, e.getMessage());
            return false;
        }
    }
}
