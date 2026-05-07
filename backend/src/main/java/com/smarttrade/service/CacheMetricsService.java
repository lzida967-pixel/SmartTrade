package com.smarttrade.service;

import com.smarttrade.vo.CacheMetricVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存命中率监控服务（进程内统计，不持久化）
 *
 * <p>设计目标：
 * <ul>
 *   <li>在不侵入 Redis 操作的前提下，统计每类缓存 key 的命中 / 未命中次数</li>
 *   <li>按 key 前缀聚合（如 stock:quote:600519 与 stock:quote:000001 都计入 stock:quote:* 前缀）</li>
 *   <li>使用 {@link LongAdder} 替代 AtomicLong，高并发下分散热点</li>
 *   <li>线程安全 + O(1) 写入，监控本身不会成为系统瓶颈</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 *   metrics.recordHit("stock:quote:600519");
 *   metrics.recordMiss("stock:quote:000001");
 *   List<CacheMetricVO> snapshot = metrics.snapshot();
 * }</pre>
 *
 * <p>注意：进程重启后清零，演示压测时可以借此对比"缓存预热前后"。
 */
@Slf4j
@Service
public class CacheMetricsService {

    /** prefix → 计数器  */
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 全局起始时间，用于计算 QPS（每秒命中数） */
    private volatile LocalDateTime startedAt = LocalDateTime.now();

    /**
     * 记录一次缓存命中
     *
     * @param fullKey 完整 key，如 "stock:quote:600519"
     */
    public void recordHit(String fullKey) {
        counterFor(extractPrefix(fullKey)).hits.increment();
    }

    /**
     * 记录一次缓存未命中
     */
    public void recordMiss(String fullKey) {
        counterFor(extractPrefix(fullKey)).misses.increment();
    }

    /**
     * 取得所有前缀的统计快照（按总访问量降序）
     */
    public List<CacheMetricVO> snapshot() {
        List<CacheMetricVO> list = new ArrayList<>(counters.size());
        for (Map.Entry<String, Counter> e : counters.entrySet()) {
            Counter c = e.getValue();
            long hits = c.hits.sum();
            long misses = c.misses.sum();
            long total = hits + misses;
            double hitRate = total == 0 ? 0.0 : (hits * 1.0 / total);
            CacheMetricVO vo = new CacheMetricVO();
            vo.setPrefix(e.getKey());
            vo.setHits(hits);
            vo.setMisses(misses);
            vo.setTotal(total);
            vo.setHitRate(hitRate);
            list.add(vo);
        }
        list.sort(Comparator.comparingLong(CacheMetricVO::getTotal).reversed());
        return list;
    }

    /**
     * 全部清零（演示前可调用，方便压测对比）
     */
    public void reset() {
        counters.clear();
        startedAt = LocalDateTime.now();
        log.info("缓存命中率监控已清零");
    }

    /**
     * 起始时间（用于前端展示"统计时长"）
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    // ============== 内部辅助 ==============

    private Counter counterFor(String prefix) {
        return counters.computeIfAbsent(prefix, k -> new Counter());
    }

    /**
     * 从完整 key 提取前缀，规则：
     * <ul>
     *   <li>"stock:quote:600519"     → "stock:quote:*"</li>
     *   <li>"stock:batch:abc123"     → "stock:batch:*"</li>
     *   <li>"stock:kline:600519:120" → "stock:kline:*"</li>
     *   <li>"foo"（无冒号）          → "foo:*"</li>
     * </ul>
     * 取前两段作为聚合粒度，足以区分各类业务缓存。
     */
    static String extractPrefix(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) return "unknown:*";
        int first = fullKey.indexOf(':');
        if (first < 0) return fullKey + ":*";
        int second = fullKey.indexOf(':', first + 1);
        if (second < 0) return fullKey.substring(0, first) + ":*";
        return fullKey.substring(0, second) + ":*";
    }

    /** 单个前缀的计数槽位 */
    private static class Counter {
        final LongAdder hits = new LongAdder();
        final LongAdder misses = new LongAdder();
    }
}
