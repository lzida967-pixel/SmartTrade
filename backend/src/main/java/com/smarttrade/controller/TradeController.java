package com.smarttrade.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.common.Result;
import com.smarttrade.dto.PlaceOrderDTO;
import com.smarttrade.entity.TradeDeal;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.entity.UserPosition;
import com.smarttrade.service.TradeService;
import com.smarttrade.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟交易接口：下单、撤单、查询委托单/成交单/持仓
 */
@RestController
@RequestMapping("/trade")
public class TradeController {

    @Autowired
    private TradeService tradeService;

    /**
     * 下单（买入或卖出）
     */
    @PostMapping("/order")
    public Result<TradeOrder> placeOrder(@Validated @RequestBody PlaceOrderDTO dto) {
        Long userId = UserContext.getUserId();
        TradeOrder order = tradeService.placeOrder(userId, dto);
        String msg = "FILLED".equals(order.getOrderStatus()) ? "下单已成交" : "下单成功，等待撮合";
        return Result.success(order, msg);
    }

    /**
     * 撤单
     */
    @PostMapping("/order/{orderNo}/cancel")
    public Result<TradeOrder> cancelOrder(@PathVariable("orderNo") String orderNo) {
        Long userId = UserContext.getUserId();
        TradeOrder order = tradeService.cancelOrder(userId, orderNo);
        return Result.success(order, "撤单成功");
    }

    /**
     * 当前用户委托单分页查询
     */
    @GetMapping("/orders")
    public Result<Page<TradeOrder>> listOrders(@RequestParam(value = "status", required = false) String status,
                                               @RequestParam(value = "stockCode", required = false) String stockCode,
                                               @RequestParam(value = "page", defaultValue = "1") Integer page,
                                               @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(tradeService.listOrders(userId, status, stockCode, page, size));
    }

    /**
     * 当前用户成交明细分页查询
     */
    @GetMapping("/deals")
    public Result<Page<TradeDeal>> listDeals(@RequestParam(value = "stockCode", required = false) String stockCode,
                                             @RequestParam(value = "page", defaultValue = "1") Integer page,
                                             @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(tradeService.listDeals(userId, stockCode, page, size));
    }

    /**
     * 当前用户持仓列表（含最新价、市值、浮盈亏）
     */
    @GetMapping("/positions")
    public Result<List<UserPosition>> listPositions() {
        Long userId = UserContext.getUserId();
        return Result.success(tradeService.listPositions(userId));
    }
}
