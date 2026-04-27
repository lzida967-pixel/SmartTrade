package com.smarttrade.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.service.TradeOrderService;
import com.smarttrade.service.TradeService;
import com.smarttrade.vo.StockQuoteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限价单后台自动撮合任务
 *
 * 每 N 秒扫一次所有 PENDING 限价单，按股票分组拉取最新价，对穿过委托价的订单触发撮合。
 *
 * 注意：
 *   - 市价单已在下单时立即成交，不会进入 PENDING 状态
 *   - 限价单仅当行情触达时才成交（买单 latest <= entrust，卖单 latest >= entrust）
 *   - 每个订单的撮合走 TradeService.tryMatchOnePending（独立事务，失败不影响其他订单）
 */
@Slf4j
@Component
public class PendingOrderScheduler {

    @Autowired
    private TradeOrderService tradeOrderService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private StockMarketService stockMarketService;

    @Autowired
    private StockInfoService stockInfoService;

    @Value("${smarttrade.pending-match.enabled:true}")
    private boolean enabled;

    /**
     * 每 30 秒扫一次（可通过配置覆盖）
     */
    @Scheduled(fixedDelayString = "${smarttrade.pending-match.fixed-delay-ms:30000}",
               initialDelayString = "${smarttrade.pending-match.initial-delay-ms:15000}")
    public void scanAndMatch() {
        if (!enabled) return;
        try {
            int filled = matchPendingOrders();
            if (filled > 0) {
                log.info("[限价撮合] 本轮成交 {} 笔", filled);
            }
        } catch (Exception e) {
            log.warn("限价单撮合任务异常: {}", e.getMessage());
        }
    }

    /**
     * 扫描所有 PENDING 限价单并尝试撮合，返回成交数量
     */
    private int matchPendingOrders() {
        List<TradeOrder> pending = tradeOrderService.list(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderStatus, "PENDING")
                        .eq(TradeOrder::getOrderType, "LIMIT")
        );
        if (pending.isEmpty()) return 0;

        // 按股票分组，每只股票只拉一次行情
        Map<String, List<TradeOrder>> byStock = new HashMap<>();
        for (TradeOrder o : pending) {
            byStock.computeIfAbsent(o.getStockCode(), k -> new ArrayList<>()).add(o);
        }

        // 一次批量查 stock_info 拿 market
        List<StockInfo> stocks = stockInfoService.listByIds(byStock.keySet());
        Map<String, StockInfo> infoMap = new HashMap<>(stocks.size() * 2);
        for (StockInfo s : stocks) infoMap.put(s.getStockCode(), s);

        int filled = 0;
        for (Map.Entry<String, List<TradeOrder>> entry : byStock.entrySet()) {
            String stockCode = entry.getKey();
            StockInfo info = infoMap.get(stockCode);
            BigDecimal latestPrice = null;
            try {
                StockQuoteVO quote = stockMarketService.fetchRealtimeQuote(
                        stockCode, info != null ? info.getMarket() : null);
                if (quote != null && quote.getLatestPrice() != null
                        && quote.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                    latestPrice = quote.getLatestPrice();
                }
            } catch (Exception e) {
                log.debug("拉取 {} 实时行情失败，跳过本轮: {}", stockCode, e.getMessage());
            }
            if (latestPrice == null) continue;

            for (TradeOrder o : entry.getValue()) {
                try {
                    if (tradeService.tryMatchOnePending(o.getOrderNo(), latestPrice)) {
                        filled++;
                        log.info("[限价撮合] 成交: orderNo={} stock={} dir={} entrust={} latest={}",
                                o.getOrderNo(), stockCode, o.getDirection(),
                                o.getEntrustPrice(), latestPrice);
                    }
                } catch (Exception e) {
                    log.warn("撮合订单 {} 异常: {}", o.getOrderNo(), e.getMessage());
                }
            }
        }
        return filled;
    }
}
