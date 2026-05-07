package com.smarttrade.controller.admin;

import com.smarttrade.common.Result;
import com.smarttrade.service.CacheMetricsService;
import com.smarttrade.vo.CacheMetricVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 缓存命中率监控（仅 ADMIN）
 *
 * <p>提供两个能力：
 * <ul>
 *   <li>读取所有 key 前缀的实时命中率统计</li>
 *   <li>清零计数（演示压测前后对比）</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/cache")
public class AdminCacheController {

    @Autowired
    private CacheMetricsService cacheMetrics;

    /**
     * 获取缓存命中率快照
     *
     * @return 各前缀的命中 / 未命中 / 命中率，按总访问量降序
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        List<CacheMetricVO> items = cacheMetrics.snapshot();
        LocalDateTime startedAt = cacheMetrics.getStartedAt();
        long elapsedSec = ChronoUnit.SECONDS.between(startedAt, LocalDateTime.now());

        // 汇总指标
        long totalHits = items.stream().mapToLong(CacheMetricVO::getHits).sum();
        long totalMisses = items.stream().mapToLong(CacheMetricVO::getMisses).sum();
        long totalAll = totalHits + totalMisses;
        double overallHitRate = totalAll == 0 ? 0.0 : (totalHits * 1.0 / totalAll);
        double qps = elapsedSec <= 0 ? 0.0 : (totalAll * 1.0 / elapsedSec);

        return Result.success(Map.of(
                "items",          items,
                "totalHits",      totalHits,
                "totalMisses",    totalMisses,
                "totalRequests",  totalAll,
                "overallHitRate", overallHitRate,
                "qps",            qps,
                "startedAt",      startedAt.toString(),
                "elapsedSeconds", elapsedSec
        ));
    }

    /**
     * 清零计数器（用于演示压测前重置基线）
     */
    @PostMapping("/metrics/reset")
    public Result<Void> reset() {
        cacheMetrics.reset();
        return Result.success();
    }
}
