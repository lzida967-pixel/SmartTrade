package com.smarttrade.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.StockDailyPriceService;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.vo.KlinePointVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行情数据补充任务
 *
 * 职责：
 *   1. 每个交易日收盘后（默认 16:30，周一至周五），拉取股票池中所有股票的最近 N 根日 K，
 *      写入 zidatrade_stock_daily_price，供 AI 模型训练 / 回测 / K 线展示使用。
 *   2. 应用启动后若该表为空，立即跑一次冷启动初始化（可关闭）。
 *
 * 数据源：StockMarketService.fetchDailyKline（东方财富 主源 + 新浪 兜底）。
 */
@Slf4j
@Component
public class MarketDataScheduler {

    @Autowired
    private StockInfoService stockInfoService;

    @Autowired
    private StockDailyPriceService stockDailyPriceService;

    @Autowired
    private StockMarketService stockMarketService;

    /**
     * 单只股票每次同步的 K 线根数（默认 250 ≈ 一年）
     */
    @Value("${smarttrade.daily-price.sync-limit:250}")
    private int syncLimit;

    /**
     * 是否在应用启动时自动跑一次（仅当表为空时）
     */
    @Value("${smarttrade.daily-price.cold-start:true}")
    private boolean coldStart;

    /**
     * 单只股票之间的请求间隔（毫秒），避免对外部数据源造成压力
     */
    @Value("${smarttrade.daily-price.request-interval-ms:120}")
    private long requestIntervalMs;

    /**
     * 每个交易日 16:30 同步一次
     */
    @Scheduled(cron = "${smarttrade.daily-price.cron:0 30 16 * * MON-FRI}")
    public void scheduledSync() {
        log.info("[定时] 开始同步股票池日线行情...");
        long start = System.currentTimeMillis();
        int success = syncAllStocks();
        log.info("[定时] 同步完成: 成功 {} 支, 耗时 {} ms", success, System.currentTimeMillis() - start);
    }

    /**
     * 应用启动后异步同步缺失的股票数据：
     *   - 表为空 → 全量同步股票池
     *   - 否则 → 只同步 stock_info 中存在但 stock_daily_price 没有任何记录的股票（差集补齐）
     */
    @PostConstruct
    public void initOnBoot() {
        if (!coldStart) return;
        new Thread(() -> {
            try {
                Thread.sleep(3000); // 等待应用完全 Ready
                long count = stockDailyPriceService.count();
                if (count == 0) {
                    log.info("[冷启动] 日线表为空，开始全量同步...");
                    int success = syncAllStocks();
                    log.info("[冷启动] 全量同步完成: 成功 {} 支", success);
                } else {
                    int filled = syncMissingStocks();
                    if (filled > 0) {
                        log.info("[冷启动] 缺数据股票补齐完成: 新增 {} 支日线数据", filled);
                    } else {
                        log.info("[冷启动] 股票池日线数据完整，无需补齐");
                    }
                }
            } catch (Exception e) {
                log.error("冷启动同步失败", e);
            }
        }, "daily-price-coldstart").start();
    }

    /**
     * 同步所有 stock_info 中存在但 stock_daily_price 表里没有任何记录的股票
     */
    public int syncMissingStocks() {
        List<StockInfo> stocks = stockInfoService.list();
        if (stocks == null || stocks.isEmpty()) return 0;

        // 一次查出 daily_price 表里已有的所有 stock_code（去重）
        List<Object> existed = stockDailyPriceService.listObjs(
                new QueryWrapper<StockDailyPrice>().select("DISTINCT stock_code")
        );
        Set<String> existSet = existed.stream()
                .map(String::valueOf)
                .collect(Collectors.toCollection(HashSet::new));

        List<StockInfo> missing = stocks.stream()
                .filter(s -> !existSet.contains(s.getStockCode()))
                .toList();
        if (missing.isEmpty()) return 0;

        log.info("[补齐] 检测到 {} 支股票缺少日线数据: {}", missing.size(),
                missing.stream().map(StockInfo::getStockCode).limit(20).toList());
        return syncStockList(missing);
    }

    /**
     * 拉取所有股票池股票的日 K 并入库
     */
    public int syncAllStocks() {
        List<StockInfo> stocks = stockInfoService.list();
        if (stocks == null || stocks.isEmpty()) {
            log.warn("股票池为空，无法同步日线行情");
            return 0;
        }
        return syncStockList(stocks);
    }

    /**
     * 同步给定股票列表的日 K 数据
     */
    private int syncStockList(List<StockInfo> stocks) {
        int total = stocks.size();
        int success = 0;
        int idx = 0;
        for (StockInfo stock : stocks) {
            idx++;
            try {
                List<KlinePointVO> klines = stockMarketService.fetchDailyKline(
                        stock.getStockCode(), stock.getMarket(), syncLimit);
                if (klines == null || klines.isEmpty()) {
                    continue;
                }
                List<StockDailyPrice> rows = new ArrayList<>(klines.size());
                LocalDateTime now = LocalDateTime.now();
                for (KlinePointVO k : klines) {
                    if (k.getTradeDate() == null) continue;
                    StockDailyPrice row = new StockDailyPrice();
                    row.setStockCode(stock.getStockCode());
                    try {
                        row.setTradeDate(LocalDate.parse(k.getTradeDate()));
                    } catch (Exception ex) {
                        continue;
                    }
                    row.setOpenPrice(k.getOpenPrice());
                    row.setClosePrice(k.getClosePrice());
                    row.setHighPrice(k.getHighPrice());
                    row.setLowPrice(k.getLowPrice());
                    // 历史数据中没有直接的"前收"，以涨跌额倒推（close - changeAmount）
                    if (k.getClosePrice() != null && k.getChangeAmount() != null) {
                        row.setPreClosePrice(k.getClosePrice().subtract(k.getChangeAmount()));
                    }
                    row.setVolume(k.getVolume());
                    row.setTurnoverAmount(k.getTurnoverAmount());
                    row.setAmplitude(k.getAmplitude());
                    row.setChangePercent(k.getChangePercent());
                    row.setCreatedAt(now);
                    row.setUpdatedAt(now);
                    rows.add(row);
                }
                if (!rows.isEmpty()) {
                    stockDailyPriceService.saveOrUpdateBatchByUnique(rows);
                    success++;
                }
                // 每 20 支输出一次进度，避免刷屏
                if (idx % 20 == 0 || idx == total) {
                    log.info("[同步进度] {}/{} 已同步 {} 支", idx, total, success);
                }
                if (requestIntervalMs > 0) {
                    Thread.sleep(requestIntervalMs);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("同步股票 {} 日线失败: {}", stock.getStockCode(), e.getMessage());
            }
        }
        return success;
    }
}
