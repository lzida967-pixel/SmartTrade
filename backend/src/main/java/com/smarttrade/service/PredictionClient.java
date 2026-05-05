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

    private static final String DEFAULT_MODEL = "lgbm";
    private static final java.util.Set<String> ALLOWED_MODELS = java.util.Set.of("lgbm", "xgb", "lstm");

    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    /**
     * 单只股票预测（默认 LightGBM）。
     */
    public PredictionDTO predict(String stockCode) {
        return predict(stockCode, DEFAULT_MODEL);
    }

    /**
     * 单只股票预测，可指定模型 key（lgbm / xgb / lstm）。失败抛 IllegalStateException。
     */
    public PredictionDTO predict(String stockCode, String modelKey) {
        if (!enabled) {
            throw new IllegalStateException("预测服务已禁用");
        }
        String code = String.format("%6s", stockCode == null ? "" : stockCode.trim()).replace(' ', '0');
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("股票代码必须是 6 位数字: " + stockCode);
        }
        String model = (modelKey == null || modelKey.isBlank()) ? DEFAULT_MODEL : modelKey.toLowerCase();
        if (!ALLOWED_MODELS.contains(model)) {
            throw new IllegalArgumentException("不支持的模型: " + modelKey + "，可选: " + ALLOWED_MODELS);
        }

        // 命中缓存（按 model+code 区分）
        String cacheKey = model + ":" + code;
        CachedEntry hit = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (hit != null && (now - hit.timestamp) < cacheTtlMs) {
            return hit.value;
        }

        String url = baseUrl + "/predict/" + code + "?model=" + model;
        try {
            PredictionDTO dto = restTemplate.getForObject(url, PredictionDTO.class);
            if (dto == null) {
                throw new IllegalStateException("预测服务返回空响应");
            }
            cache.put(cacheKey, new CachedEntry(now, dto));
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
