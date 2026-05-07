package com.smarttrade.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 统一 Redis 缓存抽象层
 *
 * <p>设计目标：
 * <ul>
 *   <li>把序列化 / 反序列化 / 异常隔离 / 命中率埋点 全部封装在一处，业务方只关心业务</li>
 *   <li>对外提供"读穿透"语义：调用方传入 supplier，缓存未命中时自动回源 + 写回</li>
 *   <li>异常容错：Redis 故障时仍调用 supplier 拿原始数据，保证主流程不中断（Cache-Aside 退化为直查 DB）</li>
 *   <li>所有 get 操作自动通过 {@link CacheMetricsService} 记录命中 / 未命中</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   List<Stock> list = cacheService.getOrLoadList(
 *       "stock:list:all",
 *       Stock.class,
 *       Duration.ofMinutes(5),
 *       () -> stockMapper.selectList(null)
 *   );
 * }</pre>
 */
@Slf4j
@Service
public class CacheService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheMetricsService cacheMetrics;

    // ============== 单对象 ==============

    /**
     * 读穿透：先查 Redis，未命中 → supplier 回源 → 写回
     *
     * @param key   完整缓存 key（如 "user:asset:123"）
     * @param clazz 反序列化目标类
     * @param ttl   过期时间
     * @param loader 回源数据加载器（可能为 null 时不写回）
     */
    public <T> T getOrLoad(String key, Class<T> clazz, Duration ttl, Supplier<T> loader) {
        T cached = get(key, clazz);
        if (cached != null) return cached;
        T fresh = loader.get();
        if (fresh != null) {
            set(key, fresh, ttl);
        }
        return fresh;
    }

    /**
     * 仅查缓存，命中则返回，未命中返回 null（计入 metrics）
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                cacheMetrics.recordMiss(key);
                return null;
            }
            T value = MAPPER.readValue(json, clazz);
            cacheMetrics.recordHit(key);
            return value;
        } catch (Exception e) {
            log.debug("读取缓存失败 {}: {}", key, e.getMessage());
            cacheMetrics.recordMiss(key);
            return null;
        }
    }

    /**
     * 写入缓存，序列化失败 / Redis 故障不抛异常（仅写日志）
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.debug("写入缓存失败 {}: {}", key, e.getMessage());
        }
    }

    // ============== 列表 ==============

    /**
     * 读穿透 - 列表版
     */
    public <T> List<T> getOrLoadList(String key, Class<T> clazz, Duration ttl, Supplier<List<T>> loader) {
        List<T> cached = getList(key, clazz);
        if (cached != null) return cached;
        List<T> fresh = loader.get();
        if (fresh != null) {
            setList(key, fresh, ttl);
        }
        return fresh;
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                cacheMetrics.recordMiss(key);
                return null;
            }
            List<T> value = MAPPER.readValue(
                    json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
            cacheMetrics.recordHit(key);
            return value;
        } catch (Exception e) {
            log.debug("读取列表缓存失败 {}: {}", key, e.getMessage());
            cacheMetrics.recordMiss(key);
            return null;
        }
    }

    public void setList(String key, List<?> value, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.debug("写入列表缓存失败 {}: {}", key, e.getMessage());
        }
    }

    // ============== 任意泛型（如 Map / 复杂嵌套） ==============

    /**
     * 给复杂泛型用的版本，调用方传 TypeReference
     */
    public <T> T getByType(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                cacheMetrics.recordMiss(key);
                return null;
            }
            T value = MAPPER.readValue(json, typeRef);
            cacheMetrics.recordHit(key);
            return value;
        } catch (Exception e) {
            log.debug("读取缓存失败 {}: {}", key, e.getMessage());
            cacheMetrics.recordMiss(key);
            return null;
        }
    }

    public <T> T getOrLoadByType(String key, TypeReference<T> typeRef, Duration ttl, Supplier<T> loader) {
        T cached = getByType(key, typeRef);
        if (cached != null) return cached;
        T fresh = loader.get();
        if (fresh != null) {
            set(key, fresh, ttl);
        }
        return fresh;
    }

    // ============== 失效 ==============

    /**
     * 删除单个 key
     */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("删除缓存失败 {}: {}", key, e.getMessage());
        }
    }

    /**
     * 按 pattern 批量删除（写时清除场景）
     *
     * @param pattern 形如 "watchlist:codes:*"
     */
    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("按 pattern 删除缓存失败 {}: {}", pattern, e.getMessage());
        }
    }
}
