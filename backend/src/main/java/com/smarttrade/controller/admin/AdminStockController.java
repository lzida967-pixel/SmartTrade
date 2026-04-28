package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.common.Result;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.AiPredictLogService;
import com.smarttrade.service.StockDailyPriceService;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.TradeDealService;
import com.smarttrade.service.TradeOrderService;
import com.smarttrade.service.UserPositionService;
import com.smarttrade.task.MarketDataScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    private TradeOrderService tradeOrderService;

    @Autowired
    private UserPositionService userPositionService;

    @Autowired
    private TradeDealService tradeDealService;

    @Autowired
    private AiPredictLogService aiPredictLogService;

    /**
     * 股票池分页列表（含每只股票的日 K 数据条数，便于发现"缺数据"股票）
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param keyword 模糊匹配 stock_code / stock_name / industry_name
     * @param market  SH / SZ
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String market) {

        LambdaQueryWrapper<StockInfo> qw = new LambdaQueryWrapper<StockInfo>()
                .orderByAsc(StockInfo::getStockCode);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(w -> w.like(StockInfo::getStockCode, k)
                    .or().like(StockInfo::getStockName, k)
                    .or().like(StockInfo::getIndustryName, k));
        }
        if (market != null && !market.isBlank()) {
            qw.eq(StockInfo::getMarket, market.trim());
        }

        Page<StockInfo> p = stockInfoService.page(new Page<>(page, size), qw);
        List<StockInfo> stocks = p.getRecords();

        // 仅对本页股票一次性查日 K 条数（避免 N+1，也避免全表扫描）
        Map<String, Long> countMap = Collections.emptyMap();
        if (!stocks.isEmpty()) {
            List<String> codes = new ArrayList<>(stocks.size());
            for (StockInfo s : stocks) codes.add(s.getStockCode());
            QueryWrapper<StockDailyPrice> cqw = new QueryWrapper<>();
            cqw.select("stock_code", "COUNT(*) AS cnt")
                    .in("stock_code", codes)
                    .groupBy("stock_code");
            List<Map<String, Object>> rows = stockDailyPriceService.listMaps(cqw);
            countMap = new HashMap<>(rows.size() * 2);
            for (Map<String, Object> r : rows) {
                countMap.put(String.valueOf(r.get("stock_code")),
                        ((Number) r.get("cnt")).longValue());
            }
        }

        List<Map<String, Object>> records = new ArrayList<>(stocks.size());
        for (StockInfo s : stocks) {
            Map<String, Object> m = new HashMap<>();
            m.put("stockCode", s.getStockCode());
            m.put("stockName", s.getStockName());
            m.put("market", s.getMarket());
            m.put("industryName", s.getIndustryName());
            m.put("plateType", s.getPlateType());
            m.put("dailyPriceCount", countMap.getOrDefault(s.getStockCode(), 0L));
            records.add(m);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        return Result.success(data);
    }

    /**
     * 全局统计：总数 / 已有日 K 数据 / 缺数据 数量。
     * 单独接口避免分页时统计跟着翻页变化。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long total = stockInfoService.count();

        QueryWrapper<StockDailyPrice> qw = new QueryWrapper<>();
        qw.select("DISTINCT stock_code");
        long withData = stockDailyPriceService.listObjs(qw).size();
        long missing = Math.max(0L, total - withData);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("withData", withData);
        data.put("missing", missing);
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
     * 删除股票
     *
     * 由于 zidatrade_stock_info.stock_code 被 5 张子表外键引用，必须严格按"先子表后父表"顺序：
     *   - 安全级联：daily_price / ai_predict_log（行情衍生数据，可直接清掉）
     *   - 阻断删除：trade_order / user_position / trade_deal（涉及用户资产/资金，禁止隐式删除）
     */
    @DeleteMapping("/{stockCode}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return Result.error("股票代码不能为空");
        }
        if (stockInfoService.getById(stockCode) == null) {
            return Result.error("股票不存在");
        }

        // 1) 阻断条件：用户委托/持仓/成交
        long orderCount = tradeOrderService.count(
                new QueryWrapper<com.smarttrade.entity.TradeOrder>().eq("stock_code", stockCode));
        if (orderCount > 0) {
            return Result.error("存在 " + orderCount + " 条用户委托记录，禁止删除该股票");
        }
        long positionCount = userPositionService.count(
                new QueryWrapper<com.smarttrade.entity.UserPosition>().eq("stock_code", stockCode));
        if (positionCount > 0) {
            return Result.error("存在 " + positionCount + " 个用户持仓，禁止删除该股票");
        }
        long dealCount = tradeDealService.count(
                new QueryWrapper<com.smarttrade.entity.TradeDeal>().eq("stock_code", stockCode));
        if (dealCount > 0) {
            return Result.error("存在 " + dealCount + " 条成交明细，禁止删除该股票");
        }

        // 2) 安全级联：行情/AI 衍生数据
        stockDailyPriceService.remove(
                new LambdaQueryWrapper<StockDailyPrice>().eq(StockDailyPrice::getStockCode, stockCode));
        aiPredictLogService.remove(
                new QueryWrapper<com.smarttrade.entity.AiPredictLog>().eq("stock_code", stockCode));

        // 3) 最后再删父表
        boolean ok = stockInfoService.removeById(stockCode);
        if (!ok) {
            return Result.error("删除失败");
        }
        log.info("[管理后台] 已删除股票 {}", stockCode);
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
     * 同步进度查询（轮询）
     * { running, type, currentStock, total, processed, success, failed, startedAt, finishedAt, lastError }
     */
    @GetMapping("/sync-status")
    public Result<Map<String, Object>> syncStatus() {
        return Result.success(marketDataScheduler.getSyncStatus());
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
