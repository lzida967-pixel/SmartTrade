package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smarttrade.common.Result;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.StockDailyPriceService;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.task.MarketDataScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 股票池管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/stocks")
public class AdminStockController {

    @Autowired
    private StockInfoService stockInfoService;

    @Autowired
    private StockDailyPriceService stockDailyPriceService;

    @Autowired
    private MarketDataScheduler marketDataScheduler;

    /**
     * 股票池列表（含每只股票的日 K 数据条数，便于发现"缺数据"股票）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<StockInfo> stocks = stockInfoService.list(
                new LambdaQueryWrapper<StockInfo>().orderByAsc(StockInfo::getStockCode)
        );

        // 一次查所有股票的日 K 行数（避免 N+1）
        // SELECT stock_code, COUNT(*) FROM stock_daily_price GROUP BY stock_code
        QueryWrapper<StockDailyPrice> qw = new QueryWrapper<>();
        qw.select("stock_code", "COUNT(*) AS cnt").groupBy("stock_code");
        List<Map<String, Object>> rows = stockDailyPriceService.listMaps(qw);
        Map<String, Long> countMap = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> r : rows) {
            countMap.put(String.valueOf(r.get("stock_code")),
                    ((Number) r.get("cnt")).longValue());
        }

        List<Map<String, Object>> data = stocks.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("stockCode", s.getStockCode());
            m.put("stockName", s.getStockName());
            m.put("market", s.getMarket());
            m.put("industryName", s.getIndustryName());
            m.put("plateType", s.getPlateType());
            m.put("dailyPriceCount", countMap.getOrDefault(s.getStockCode(), 0L));
            return m;
        }).toList();

        return Result.success(data);
    }

    /**
     * 新增股票
     */
    @PostMapping
    public Result<StockInfo> add(@RequestBody StockInfo stock) {
        if (stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return Result.error("股票代码不能为空");
        }
        if (stockInfoService.getById(stock.getStockCode()) != null) {
            return Result.error("股票代码已存在");
        }
        stock.setCreatedAt(LocalDateTime.now());
        stockInfoService.save(stock);
        return Result.success(stock, "已加入股票池");
    }

    /**
     * 删除股票（同时清掉它在 daily_price 中的数据）
     */
    @DeleteMapping("/{stockCode}")
    public Result<Void> delete(@PathVariable String stockCode) {
        stockInfoService.removeById(stockCode);
        stockDailyPriceService.remove(
                new LambdaQueryWrapper<StockDailyPrice>().eq(StockDailyPrice::getStockCode, stockCode)
        );
        return Result.success(null, "已删除");
    }

    /**
     * 触发全量日 K 同步（异步执行，立即返回）
     */
    @PostMapping("/sync")
    public Result<Void> syncAll() {
        new Thread(() -> {
            try {
                int success = marketDataScheduler.syncAllStocks();
                log.info("[管理后台] 手动全量同步完成: {} 支", success);
            } catch (Exception e) {
                log.error("[管理后台] 手动全量同步失败", e);
            }
        }, "admin-sync-all").start();
        return Result.success(null, "全量同步已启动，请稍后查看日志");
    }

    /**
     * 触发缺失股票同步（异步）
     */
    @PostMapping("/sync-missing")
    public Result<Void> syncMissing() {
        new Thread(() -> {
            try {
                int filled = marketDataScheduler.syncMissingStocks();
                log.info("[管理后台] 手动补齐完成: 新增 {} 支日线数据", filled);
            } catch (Exception e) {
                log.error("[管理后台] 手动补齐失败", e);
            }
        }, "admin-sync-missing").start();
        return Result.success(null, "补齐任务已启动");
    }
}
