package com.smarttrade.service;

import com.smarttrade.dto.PredictionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用外部 Python FastAPI 预测服务（prediction-service）。
 *
 * 内置进程级短期缓存（默认 60 秒），避免高频请求穿透到 Python 端。
 */
@Slf4j
@Service
public class PredictionClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${smarttrade.prediction.enabled:true}")
    private boolean enabled;

    @Value("${smarttrade.prediction.base-url:http://127.0.0.1:8001}")
    private String baseUrl;

    @Value("${smarttrade.prediction.cache-ttl-ms:60000}")
    private long cacheTtlMs;

    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    /**
     * 单只股票预测。失败抛 IllegalStateException，调用方决定如何兜底。
     */
    public PredictionDTO predict(String stockCode) {
        if (!enabled) {
            throw new IllegalStateException("预测服务已禁用");
        }
        String code = String.format("%6s", stockCode == null ? "" : stockCode.trim()).replace(' ', '0');
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("股票代码必须是 6 位数字: " + stockCode);
        }

        // 命中缓存
        CachedEntry hit = cache.get(code);
        long now = System.currentTimeMillis();
        if (hit != null && (now - hit.timestamp) < cacheTtlMs) {
            return hit.value;
        }

        String url = baseUrl + "/predict/" + code;
        try {
            PredictionDTO dto = restTemplate.getForObject(url, PredictionDTO.class);
            if (dto == null) {
                throw new IllegalStateException("预测服务返回空响应");
            }
            cache.put(code, new CachedEntry(now, dto));
            return dto;
        } catch (RestClientException e) {
            log.warn("[Prediction] 调用 {} 失败: {}", url, e.getMessage());
            throw new IllegalStateException("预测服务不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 健康检查（不抛异常）。
     */
    public boolean isHealthy() {
        if (!enabled) return false;
        try {
            Map<?, ?> resp = restTemplate.getForObject(baseUrl + "/health", Map.class);
            return resp != null && "UP".equals(resp.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    private record CachedEntry(long timestamp, PredictionDTO value) {}
}
