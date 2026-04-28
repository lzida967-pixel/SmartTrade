package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smarttrade.common.Result;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.entity.User;
import com.smarttrade.service.StockDailyPriceService;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.TradeOrderService;
import com.smarttrade.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - 仪表盘聚合接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired private UserService userService;
    @Autowired private StockInfoService stockInfoService;
    @Autowired private StockDailyPriceService stockDailyPriceService;
    @Autowired private TradeOrderService tradeOrderService;

    /**
     * 仪表盘综合数据：用户/股票/订单/资金 概览 + 近 7 日订单趋势 + 最新 10 单 + 缺数据股票 Top 10
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        // ============== 用户 ==============
        long userTotal = userService.count();
        long adminCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        long activeUserCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        BigDecimal availableFundsSum = userService.list().stream()
                .map(u -> u.getAvailableFunds() == null ? BigDecimal.ZERO : u.getAvailableFunds())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> userBlock = new HashMap<>();
        userBlock.put("total", userTotal);
        userBlock.put("admin", adminCount);
        userBlock.put("active", activeUserCount);
        userBlock.put("availableFundsSum", availableFundsSum);
        data.put("user", userBlock);

        // ============== 股票池 ==============
        long stockTotal = stockInfoService.count();
        Set<String> withData = new HashSet<>();
        List<Object> codes = stockDailyPriceService.listObjs(
                new QueryWrapper<StockDailyPrice>().select("DISTINCT stock_code"));
        for (Object o : codes) withData.add(String.valueOf(o));
        long missing = Math.max(0L, stockTotal - withData.size());

        Map<String, Object> stockBlock = new HashMap<>();
        stockBlock.put("total", stockTotal);
        stockBlock.put("withData", withData.size());
        stockBlock.put("missing", missing);
        data.put("stock", stockBlock);

        // 缺数据股票 Top 10
        List<StockInfo> missingStocks = stockInfoService.list().stream()
                .filter(s -> !withData.contains(s.getStockCode()))
                .limit(10)
                .toList();
        List<Map<String, Object>> missingList = new ArrayList<>();
        for (StockInfo s : missingStocks) {
            Map<String, Object> m = new HashMap<>();
            m.put("stockCode", s.getStockCode());
            m.put("stockName", s.getStockName());
            m.put("market", s.getMarket());
            m.put("industryName", s.getIndustryName());
            missingList.add(m);
        }
        data.put("missingStocks", missingList);

        // ============== 订单 ==============
        long orderTotal = tradeOrderService.count();
        long pending = tradeOrderService.count(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderStatus, "PENDING"));
        long filled = tradeOrderService.count(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderStatus, "FILLED"));
        long canceled = tradeOrderService.count(
                new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderStatus, "CANCELED"));

        // 今日订单
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayOrderCount = tradeOrderService.count(
                new LambdaQueryWrapper<TradeOrder>().ge(TradeOrder::getOrderTime, todayStart));

        Map<String, Object> orderBlock = new HashMap<>();
        orderBlock.put("total", orderTotal);
        orderBlock.put("pending", pending);
        orderBlock.put("filled", filled);
        orderBlock.put("canceled", canceled);
        orderBlock.put("today", todayOrderCount);
        data.put("order", orderBlock);

        // 近 7 日订单数量趋势（按天聚合，缺天补 0）
        QueryWrapper<TradeOrder> trendQw = new QueryWrapper<>();
        trendQw.select("DATE(order_time) AS d", "COUNT(*) AS c")
                .ge("order_time", LocalDate.now().minusDays(6).atStartOfDay())
                .groupBy("DATE(order_time)")
                .orderByAsc("d");
        List<Map<String, Object>> trendRows = tradeOrderService.listMaps(trendQw);
        Map<String, Long> dayMap = new HashMap<>();
        for (Map<String, Object> r : trendRows) {
            Object d = r.get("d");
            String key = d == null ? "" : d.toString().substring(0, 10);
            dayMap.put(key, ((Number) r.get("c")).longValue());
        }
        List<Map<String, Object>> trend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> p = new HashMap<>();
            p.put("date", day.toString());
            p.put("count", dayMap.getOrDefault(day.toString(), 0L));
            trend.add(p);
        }
        data.put("orderTrend", trend);

        // 最近 10 单
        List<TradeOrder> recent = tradeOrderService.list(
                new LambdaQueryWrapper<TradeOrder>()
                        .orderByDesc(TradeOrder::getOrderTime)
                        .last("LIMIT 10"));
        data.put("recentOrders", recent);

        return Result.success(data);
    }
}
