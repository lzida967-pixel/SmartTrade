package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.dto.PlaceOrderDTO;
import com.smarttrade.entity.TradeDeal;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.entity.UserPosition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟交易核心服务：下单、撤单、查询、撮合
 */
public interface TradeService {

    /**
     * 下单 + 立即撮合（简化版：以最新行情价立即成交）
     * 资金/持仓校验失败时直接抛 RuntimeException
     */
    TradeOrder placeOrder(Long userId, PlaceOrderDTO dto);

    /**
     * 撤单：仅 PENDING 状态可撤；解冻资金或持仓
     */
    TradeOrder cancelOrder(Long userId, String orderNo);

    /**
     * 分页查询当前用户委托单（按时间倒序）。可选状态过滤、股票代码过滤
     */
    Page<TradeOrder> listOrders(Long userId, String status, String stockCode, int page, int size);

    /**
     * 分页查询当前用户成交明细（按时间倒序）
     */
    Page<TradeDeal> listDeals(Long userId, String stockCode, int page, int size);

    /**
     * 查询当前用户所有持仓（quantity > 0），自动补充最新价、市值与浮盈亏
     */
    List<UserPosition> listPositions(Long userId);

    /**
     * 尝试以给定最新价撮合一笔 PENDING 限价单（独立事务）。
     * - 订单不存在或已被撮合/撤销 → 返回 false
     * - 行情未触达委托价 → 返回 false
     * - 成功成交 → 返回 true
     */
    boolean tryMatchOnePending(String orderNo, BigDecimal latestPrice);
}
