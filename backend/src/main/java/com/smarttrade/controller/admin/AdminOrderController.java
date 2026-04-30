package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.entity.User;
import com.smarttrade.service.TradeOrderService;
import com.smarttrade.service.TradeService;
import com.smarttrade.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 订单监控
 */
@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    @Autowired
    private TradeOrderService tradeOrderService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private UserService userService;

    /**
     * 全用户订单分页查询
     * 返回结构里附加 username，便于前端展示
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction) {

        LambdaQueryWrapper<TradeOrder> qw = new LambdaQueryWrapper<TradeOrder>()
                .orderByDesc(TradeOrder::getOrderTime);
        if (userId != null) qw.eq(TradeOrder::getUserId, userId);
        if (stockCode != null && !stockCode.isBlank()) qw.eq(TradeOrder::getStockCode, stockCode);
        if (status != null && !status.isBlank()) qw.eq(TradeOrder::getOrderStatus, status);
        if (direction != null && !direction.isBlank()) qw.eq(TradeOrder::getDirection, direction);

        Page<TradeOrder> p = tradeOrderService.page(new Page<>(page, size), qw);

        // 批量查 user 拼名字
        Map<Long, String> userNameMap = new HashMap<>();
        if (!p.getRecords().isEmpty()) {
            List<Long> uids = p.getRecords().stream().map(TradeOrder::getUserId).distinct().toList();
            for (User u : userService.listByIds(uids)) {
                userNameMap.put(u.getId(),
                        u.getNickname() != null && !u.getNickname().isBlank()
                                ? u.getNickname() : u.getUsername());
            }
        }
        List<Map<String, Object>> records = p.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("userId", o.getUserId());
            m.put("username", userNameMap.getOrDefault(o.getUserId(), "user_" + o.getUserId()));
            m.put("stockCode", o.getStockCode());
            m.put("direction", o.getDirection());
            m.put("orderType", o.getOrderType());
            m.put("entrustPrice", o.getEntrustPrice());
            m.put("entrustQuantity", o.getEntrustQuantity());
            m.put("matchQuantity", o.getMatchQuantity());
            m.put("turnoverAmount", o.getTurnoverAmount());
            m.put("orderStatus", o.getOrderStatus());
            m.put("orderTime", o.getOrderTime());
            m.put("matchTime", o.getMatchTime());
            return m;
        }).toList();

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", records);
        resp.put("total", p.getTotal());
        resp.put("current", p.getCurrent());
        resp.put("size", p.getSize());
        return Result.success(resp);
    }

    /**
     * 强制撤单（无视所属 user）
     */
    @PostMapping("/{orderNo}/force-cancel")
    @AuditLog(category = "ADMIN_ORDER", action = "FORCE_CANCEL",
            targetType = "ORDER", target = "#orderNo",
            summary = "管理员强制撤单 #{#orderNo}",
            includeArgs = {"orderNo"})
    public Result<TradeOrder> forceCancel(@PathVariable String orderNo) {
        TradeOrder order = tradeOrderService.getById(orderNo);
        if (order == null) return Result.error("订单不存在");
        if (!"PENDING".equals(order.getOrderStatus())) {
            return Result.error("仅待成交订单可撤销，当前状态: " + order.getOrderStatus());
        }
        // 复用现有逻辑，传该订单的真实 userId
        TradeOrder result = tradeService.cancelOrder(order.getUserId(), orderNo);
        return Result.success(result, "已强制撤单");
    }
}
