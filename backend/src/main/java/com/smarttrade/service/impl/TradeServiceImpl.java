package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.dto.PlaceOrderDTO;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.entity.TradeDeal;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.entity.User;
import com.smarttrade.entity.UserPosition;
import com.smarttrade.service.*;
import com.smarttrade.vo.StockQuoteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟交易核心服务实现。
 *
 * 交易模型（学习项目，简化版）：
 *   1. 不区分 LIMIT/MARKET：所有订单一律以"当前最新行情价"立即全部成交。
 *      对于 LIMIT 单，仅在最新价穿过委托价（买单 latest <= entrust，卖单 latest >= entrust）
 *      时才成交，否则订单保持 PENDING，可由用户撤单。
 *   2. T+0：买入立即可卖（学习项目简化处理；后续可加每日开盘任务做 T+1 解冻）。
 *   3. 不收取任何手续费/印花税；金额按 price × qty 严格计算。
 *
 * 资金/持仓一致性：
 *   - 所有订单写入、用户资金、持仓变更全部在同一个事务里完成。
 *   - 失败抛 RuntimeException → 由 GlobalExceptionHandler 统一返回。
 */
@Slf4j
@Service
public class TradeServiceImpl implements TradeService {

    @Autowired
    private TradeOrderService tradeOrderService;

    @Autowired
    private TradeDealService tradeDealService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserPositionService userPositionService;

    @Autowired
    private StockInfoService stockInfoService;

    @Autowired
    private StockMarketService stockMarketService;

    @Autowired
    private StockDailyPriceService stockDailyPriceService;

    // ===================================================================
    // 下单
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder placeOrder(Long userId, PlaceOrderDTO dto) {
        if (userId == null) {
            throw new RuntimeException("未登录");
        }

        // ---------- 1. 基本参数 ----------
        String stockCode = dto.getStockCode() == null ? "" : dto.getStockCode().trim();
        String direction = dto.getDirection() == null ? "" : dto.getDirection().trim().toUpperCase();
        String orderType = dto.getOrderType() == null ? "MARKET" : dto.getOrderType().trim().toUpperCase();
        Integer quantity = dto.getQuantity();

        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            throw new RuntimeException("交易方向必须是 BUY 或 SELL");
        }
        if (quantity == null || quantity < 100 || quantity % 100 != 0) {
            throw new RuntimeException("委托数量必须是 100 的整数倍");
        }
        if (!"LIMIT".equals(orderType) && !"MARKET".equals(orderType)) {
            throw new RuntimeException("订单类型必须是 LIMIT 或 MARKET");
        }

        // ---------- 2. 校验股票存在 ----------
        StockInfo stock = stockInfoService.getById(stockCode);
        if (stock == null) {
            throw new RuntimeException("股票不存在: " + stockCode);
        }
        if (stock.getStatus() != null && stock.getStatus() == 0) {
            throw new RuntimeException("该股票已停牌或退市，禁止交易");
        }

        // ---------- 3. 拉取撮合基准价 ----------
        BigDecimal latestPrice = resolveMatchingPrice(stockCode, stock.getMarket());
        if (latestPrice == null || latestPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("当前无法获取该股票的实时行情，请稍后重试");
        }

        // ---------- 4. 委托价 ----------
        BigDecimal entrustPrice;
        if ("LIMIT".equals(orderType)) {
            if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("限价单必须提供委托价");
            }
            entrustPrice = dto.getPrice().setScale(2, RoundingMode.HALF_UP);
        } else {
            entrustPrice = latestPrice;
        }

        // ---------- 5. 创建订单（PENDING）----------
        TradeOrder order = new TradeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStockCode(stockCode);
        order.setDirection(direction);
        order.setOrderType(orderType);
        order.setEntrustPrice(entrustPrice);
        order.setEntrustQuantity(quantity);
        order.setMatchQuantity(0);
        order.setFrozenAmount(BigDecimal.ZERO);
        order.setTurnoverAmount(BigDecimal.ZERO);
        order.setOrderStatus("PENDING");
        order.setOrderTime(LocalDateTime.now());
        order.setRemark(dto.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // ---------- 6. 资金/持仓冻结 ----------
        if ("BUY".equals(direction)) {
            BigDecimal needAmount = entrustPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            User user = userService.getById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            BigDecimal avail = nz(user.getAvailableFunds());
            if (avail.compareTo(needAmount) < 0) {
                throw new RuntimeException(String.format(
                        "可用资金不足，下单需要 ¥%s，可用 ¥%s", needAmount, avail));
            }
            // 冻结资金
            user.setAvailableFunds(avail.subtract(needAmount));
            user.setFrozenFunds(nz(user.getFrozenFunds()).add(needAmount));
            user.setUpdatedAt(LocalDateTime.now());
            userService.updateById(user);
            order.setFrozenAmount(needAmount);
        } else {
            // SELL：检查可卖持仓
            UserPosition position = getPosition(userId, stockCode);
            int avail = position == null ? 0 : nzInt(position.getAvailableQuantity());
            if (avail < quantity) {
                throw new RuntimeException(String.format(
                        "可卖持仓不足，下单需要 %d 股，可卖 %d 股", quantity, avail));
            }
            // 冻结持仓
            position.setAvailableQuantity(avail - quantity);
            position.setFrozenQuantity(nzInt(position.getFrozenQuantity()) + quantity);
            position.setUpdatedAt(LocalDateTime.now());
            userPositionService.updateById(position);
        }

        // 写入订单
        tradeOrderService.save(order);

        // ---------- 7. 立即撮合 ----------
        boolean matched = matchOrder(order, latestPrice);
        if (matched) {
            // matchOrder 内部已更新订单为 FILLED 并提交资金/持仓变更
            order = tradeOrderService.getById(order.getOrderNo());
        } else {
            log.info("订单 {} 已挂单 PENDING，等待价格触发或用户撤单", order.getOrderNo());
        }
        return order;
    }

    // ===================================================================
    // 撮合（简化版：以 latestPrice 一次全量成交）
    // ===================================================================
    private boolean matchOrder(TradeOrder order, BigDecimal latestPrice) {
        // 限价单的撮合条件：买单 latest <= entrust；卖单 latest >= entrust
        if ("LIMIT".equals(order.getOrderType())) {
            if ("BUY".equals(order.getDirection())) {
                if (latestPrice.compareTo(order.getEntrustPrice()) > 0) {
                    return false;
                }
            } else {
                if (latestPrice.compareTo(order.getEntrustPrice()) < 0) {
                    return false;
                }
            }
        }

        BigDecimal dealPrice = latestPrice.setScale(2, RoundingMode.HALF_UP);
        int dealQty = order.getEntrustQuantity();
        BigDecimal dealAmount = dealPrice.multiply(BigDecimal.valueOf(dealQty))
                .setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();

        // 1. 写成交明细
        TradeDeal deal = new TradeDeal();
        deal.setOrderNo(order.getOrderNo());
        deal.setUserId(order.getUserId());
        deal.setStockCode(order.getStockCode());
        deal.setDealPrice(dealPrice);
        deal.setDealQuantity(dealQty);
        deal.setDealAmount(dealAmount);
        deal.setDealTime(now);
        deal.setCreatedAt(now);
        tradeDealService.save(deal);

        // 2. 更新用户资金 / 持仓
        if ("BUY".equals(order.getDirection())) {
            // 买入：解冻所有冻结资金，差额（少扣的部分）退回 available
            User user = userService.getById(order.getUserId());
            BigDecimal frozen = nz(user.getFrozenFunds());
            BigDecimal frozenForOrder = nz(order.getFrozenAmount());

            // 真实成交金额可能小于冻结金额（限价单按低于委托价成交时）
            BigDecimal refund = frozenForOrder.subtract(dealAmount);
            user.setFrozenFunds(frozen.subtract(frozenForOrder));
            if (refund.compareTo(BigDecimal.ZERO) > 0) {
                user.setAvailableFunds(nz(user.getAvailableFunds()).add(refund));
            }
            user.setUpdatedAt(now);
            userService.updateById(user);

            // 更新或新增持仓（T+0 简化：买入即可卖）
            UserPosition pos = getPosition(order.getUserId(), order.getStockCode());
            if (pos == null) {
                pos = new UserPosition();
                pos.setUserId(order.getUserId());
                pos.setStockCode(order.getStockCode());
                pos.setQuantity(dealQty);
                pos.setAvailableQuantity(dealQty);
                pos.setFrozenQuantity(0);
                pos.setCostPrice(dealPrice);
                pos.setLatestPrice(dealPrice);
                pos.setMarketValue(dealAmount);
                pos.setFloatingProfit(BigDecimal.ZERO);
                pos.setLastTradeDate(LocalDate.now());
                pos.setCreatedAt(now);
                pos.setUpdatedAt(now);
                userPositionService.save(pos);
            } else {
                int oldQty = nzInt(pos.getQuantity());
                BigDecimal oldCost = nz(pos.getCostPrice());
                int newQty = oldQty + dealQty;
                BigDecimal newCost = oldCost.multiply(BigDecimal.valueOf(oldQty))
                        .add(dealAmount)
                        .divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP);
                pos.setQuantity(newQty);
                pos.setAvailableQuantity(nzInt(pos.getAvailableQuantity()) + dealQty);
                pos.setCostPrice(newCost);
                pos.setLatestPrice(dealPrice);
                pos.setMarketValue(dealPrice.multiply(BigDecimal.valueOf(newQty))
                        .setScale(2, RoundingMode.HALF_UP));
                pos.setFloatingProfit(dealPrice.subtract(newCost)
                        .multiply(BigDecimal.valueOf(newQty))
                        .setScale(2, RoundingMode.HALF_UP));
                pos.setLastTradeDate(LocalDate.now());
                pos.setUpdatedAt(now);
                userPositionService.updateById(pos);
            }
        } else {
            // 卖出：减冻结持仓，加现金
            UserPosition pos = getPosition(order.getUserId(), order.getStockCode());
            if (pos == null) {
                throw new RuntimeException("持仓异常，撮合失败");
            }
            int newQty = nzInt(pos.getQuantity()) - dealQty;
            int newFrozen = Math.max(0, nzInt(pos.getFrozenQuantity()) - dealQty);
            pos.setQuantity(newQty);
            pos.setFrozenQuantity(newFrozen);
            // available 已在下单时扣减，此处不动
            pos.setLatestPrice(dealPrice);
            if (newQty > 0) {
                pos.setMarketValue(dealPrice.multiply(BigDecimal.valueOf(newQty))
                        .setScale(2, RoundingMode.HALF_UP));
                pos.setFloatingProfit(dealPrice.subtract(nz(pos.getCostPrice()))
                        .multiply(BigDecimal.valueOf(newQty))
                        .setScale(2, RoundingMode.HALF_UP));
            } else {
                pos.setMarketValue(BigDecimal.ZERO);
                pos.setFloatingProfit(BigDecimal.ZERO);
                pos.setCostPrice(BigDecimal.ZERO);
            }
            pos.setLastTradeDate(LocalDate.now());
            pos.setUpdatedAt(now);
            userPositionService.updateById(pos);

            // 卖出收入入账
            User user = userService.getById(order.getUserId());
            user.setAvailableFunds(nz(user.getAvailableFunds()).add(dealAmount));
            user.setUpdatedAt(now);
            userService.updateById(user);
        }

        // 3. 更新订单为 FILLED
        order.setMatchPrice(dealPrice);
        order.setMatchQuantity(dealQty);
        order.setTurnoverAmount(dealAmount);
        order.setMatchTime(now);
        order.setOrderStatus("FILLED");
        order.setUpdatedAt(now);
        tradeOrderService.updateById(order);
        return true;
    }

    // ===================================================================
    // 限价单后台撮合
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryMatchOnePending(String orderNo, BigDecimal latestPrice) {
        if (orderNo == null || latestPrice == null
                || latestPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        TradeOrder order = tradeOrderService.getById(orderNo);
        if (order == null || !"PENDING".equals(order.getOrderStatus())) {
            return false;
        }
        try {
            return matchOrder(order, latestPrice);
        } catch (Exception e) {
            log.warn("撮合 PENDING 单 {} 失败: {}", orderNo, e.getMessage());
            return false;
        }
    }

    // ===================================================================
    // 撤单
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder cancelOrder(Long userId, String orderNo) {
        TradeOrder order = tradeOrderService.getById(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权撤销该订单");
        }
        if (!"PENDING".equals(order.getOrderStatus())) {
            throw new RuntimeException("订单当前状态不可撤销: " + order.getOrderStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if ("BUY".equals(order.getDirection())) {
            // 解冻资金
            User user = userService.getById(userId);
            BigDecimal frozen = nz(order.getFrozenAmount());
            user.setFrozenFunds(nz(user.getFrozenFunds()).subtract(frozen));
            user.setAvailableFunds(nz(user.getAvailableFunds()).add(frozen));
            user.setUpdatedAt(now);
            userService.updateById(user);
        } else {
            // 解冻持仓
            UserPosition pos = getPosition(userId, order.getStockCode());
            if (pos != null) {
                int qty = order.getEntrustQuantity();
                pos.setFrozenQuantity(Math.max(0, nzInt(pos.getFrozenQuantity()) - qty));
                pos.setAvailableQuantity(nzInt(pos.getAvailableQuantity()) + qty);
                pos.setUpdatedAt(now);
                userPositionService.updateById(pos);
            }
        }

        order.setOrderStatus("CANCELED");
        order.setUpdatedAt(now);
        tradeOrderService.updateById(order);
        return order;
    }

    // ===================================================================
    // 查询
    // ===================================================================
    @Override
    public Page<TradeOrder> listOrders(Long userId, String status, String stockCode, int page, int size) {
        Page<TradeOrder> p = new Page<>(page, size);
        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .orderByDesc(TradeOrder::getOrderTime);
        if (status != null && !status.isBlank()) {
            q.eq(TradeOrder::getOrderStatus, status.trim().toUpperCase());
        }
        if (stockCode != null && !stockCode.isBlank()) {
            q.eq(TradeOrder::getStockCode, stockCode.trim());
        }
        return tradeOrderService.page(p, q);
    }

    @Override
    public Page<TradeDeal> listDeals(Long userId, String stockCode, int page, int size) {
        Page<TradeDeal> p = new Page<>(page, size);
        LambdaQueryWrapper<TradeDeal> q = new LambdaQueryWrapper<TradeDeal>()
                .eq(TradeDeal::getUserId, userId)
                .orderByDesc(TradeDeal::getDealTime);
        if (stockCode != null && !stockCode.isBlank()) {
            q.eq(TradeDeal::getStockCode, stockCode.trim());
        }
        return tradeDealService.page(p, q);
    }

    @Override
    public List<UserPosition> listPositions(Long userId) {
        List<UserPosition> positions = userPositionService.list(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .gt(UserPosition::getQuantity, 0)
                        .orderByDesc(UserPosition::getUpdatedAt)
        );
        if (positions.isEmpty()) {
            return positions;
        }

        // 1. 批量查 stock_info 拿 market（避免 N 次单查）
        List<String> codes = positions.stream().map(UserPosition::getStockCode).toList();
        List<StockInfo> stocks = stockInfoService.listByIds(codes);
        Map<String, StockInfo> infoMap = new HashMap<>(stocks.size() * 2);
        for (StockInfo s : stocks) infoMap.put(s.getStockCode(), s);

        // 2. 一次东方财富 ulist 批量接口拉所有持仓股票最新价
        Map<String, StockQuoteVO> quoteMap = new HashMap<>(positions.size() * 2);
        try {
            List<StockInfo> orderedStocks = new ArrayList<>(positions.size());
            for (UserPosition p : positions) {
                StockInfo info = infoMap.get(p.getStockCode());
                if (info != null) orderedStocks.add(info);
            }
            List<StockQuoteVO> quotes = stockMarketService.batchFetchQuoteForStocks(orderedStocks);
            for (StockQuoteVO q : quotes) {
                if (q != null && q.getStockCode() != null) {
                    quoteMap.put(q.getStockCode(), q);
                }
            }
        } catch (Exception e) {
            log.warn("批量拉取持仓行情失败，将逐只回退: {}", e.getMessage());
        }

        // 3. 注入 latestPrice / 市值 / 浮盈亏；批量未覆盖的股票回退单只接口（含新浪兜底）
        for (UserPosition p : positions) {
            BigDecimal latest = null;
            StockQuoteVO q = quoteMap.get(p.getStockCode());
            if (q != null && q.getLatestPrice() != null
                    && q.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                latest = q.getLatestPrice();
            } else {
                try {
                    StockInfo info = infoMap.get(p.getStockCode());
                    StockQuoteVO single = stockMarketService.fetchRealtimeQuote(
                            p.getStockCode(), info != null ? info.getMarket() : null);
                    if (single != null && single.getLatestPrice() != null
                            && single.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                        latest = single.getLatestPrice();
                    }
                } catch (Exception e) {
                    log.debug("回退刷新持仓 {} 行情失败: {}", p.getStockCode(), e.getMessage());
                }
            }
            if (latest != null) {
                int qty = nzInt(p.getQuantity());
                p.setLatestPrice(latest);
                p.setMarketValue(latest.multiply(BigDecimal.valueOf(qty))
                        .setScale(2, RoundingMode.HALF_UP));
                p.setFloatingProfit(latest.subtract(nz(p.getCostPrice()))
                        .multiply(BigDecimal.valueOf(qty))
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        return positions;
    }

    // ===================================================================
    // 辅助
    // ===================================================================

    /**
     * 撮合基准价获取：优先实时行情，失败回落到 DB 最新一根日 K 的收盘价。
     * 这样即使外部行情接口偶发抖动（HTTP/1.1 header parser received no bytes 等），
     * 也不会导致下单整体失败。
     */
    private BigDecimal resolveMatchingPrice(String stockCode, String market) {
        try {
            StockQuoteVO quote = stockMarketService.fetchRealtimeQuote(stockCode, market);
            if (quote != null && quote.getLatestPrice() != null
                    && quote.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                return quote.getLatestPrice();
            }
        } catch (Exception e) {
            log.warn("实时行情异常，回落到 DB 最新日线: {}", e.getMessage());
        }
        // 回落：取 DB 最新一根日 K 的收盘价
        List<StockDailyPrice> recent = stockDailyPriceService.listRecentByStock(stockCode, 1);
        if (recent != null && !recent.isEmpty()) {
            BigDecimal close = recent.get(0).getClosePrice();
            if (close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                log.info("[撮合兜底] 股票 {} 使用日线收盘价 {} 作为基准", stockCode, close);
                return close;
            }
        }
        return null;
    }

    private UserPosition getPosition(Long userId, String stockCode) {
        return userPositionService.getOne(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .eq(UserPosition::getStockCode, stockCode),
                false
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 32 位订单号：14 位时间戳 + 8 位 UUID hex
     */
    private static String generateOrderNo() {
        String ts = String.format("%014d", System.currentTimeMillis());
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return ts + uid;
    }
}
