package com.zing.doctor.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存活探针：无参数、无鉴权、不查库，供容器 healthcheck 与部署脚本就绪探测使用。
 *
 * <p>此前 docker-compose 的 healthcheck 与 install.sh 的就绪探测都打的是
 * {@code /api/external/token} —— 那是外链签发接口，必填 pageCode，探针不带参数，
 * 于是每隔 30 秒触发一次「缺少请求参数」异常：既在日志里刷 ERROR 堆栈，
 * 又因兜底返回 500 让 {@code curl -f} 判定失败，容器被标记 unhealthy。
 * 探针应打本接口。
 */
@RestController
public class HealthController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", LocalDateTime.now().format(FMT));
        return Result.ok(data);
    }
}
