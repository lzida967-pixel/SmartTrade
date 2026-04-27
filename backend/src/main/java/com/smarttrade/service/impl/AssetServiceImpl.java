package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.entity.User;
import com.smarttrade.entity.UserAssetSnapshot;
import com.smarttrade.entity.UserPosition;
import com.smarttrade.service.AssetService;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.service.UserAssetSnapshotService;
import com.smarttrade.service.UserPositionService;
import com.smarttrade.service.UserService;
import com.smarttrade.vo.StockQuoteVO;
import com.smarttrade.vo.UserAssetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产聚合实现
 *
 * 资产计算口径：
 *   总资产 = 可用资金(available_funds) + 冻结资金(frozen_funds) + 持仓市值(Σ qty × latestPrice)
 *   持仓市值 / 浮盈亏使用"实时行情"，无法获取时回落到持仓的 latest_price 历史值。
 *   当日收益由前一交易日快照差值得出。
 */
@Slf4j
@Service
public class AssetServiceImpl implements AssetService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserPositionService userPositionService;

    @Autowired
    private StockInfoService stockInfoService;

    @Autowired
    private StockMarketService stockMarketService;

    @Autowired
    private UserAssetSnapshotService userAssetSnapshotService;

    // ===================================================================
    // 当前资产
    // ===================================================================
    @Override
    public UserAssetVO buildCurrentAsset(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        List<UserPosition> positions = userPositionService.list(
                new LambdaQueryWrapper<UserPosition>()
                        .eq(UserPosition::getUserId, userId)
                        .gt(UserPosition::getQuantity, 0)
                        .orderByDesc(UserPosition::getUpdatedAt)
        );

        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal floatingProfit = BigDecimal.ZERO;

        if (!positions.isEmpty()) {
            // 一次批量拉所有持仓股的实时行情
            Map<String, StockInfo> infoMap = new HashMap<>(positions.size() * 2);
            for (StockInfo s : stockInfoService.listByIds(
                    positions.stream().map(UserPosition::getStockCode).toList())) {
                infoMap.put(s.getStockCode(), s);
            }
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
                log.warn("批量拉取资产组合行情失败: {}", e.getMessage());
            }

            for (UserPosition p : positions) {
                BigDecimal latest = null;
                StockQuoteVO q = quoteMap.get(p.getStockCode());
                if (q != null && q.getLatestPrice() != null
                        && q.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                    latest = q.getLatestPrice();
                } else if (p.getLatestPrice() != null
                        && p.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
                    // 拉行情失败时回落到持仓快照里的最近一次价格
                    latest = p.getLatestPrice();
                }
                if (latest == null) continue;

                int qty = nzInt(p.getQuantity());
                BigDecimal mv = latest.multiply(BigDecimal.valueOf(qty))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal fp = latest.subtract(nz(p.getCostPrice()))
                        .multiply(BigDecimal.valueOf(qty))
                        .setScale(2, RoundingMode.HALF_UP);
                p.setLatestPrice(latest);
                p.setMarketValue(mv);
                p.setFloatingProfit(fp);
                marketValue = marketValue.add(mv);
                floatingProfit = floatingProfit.add(fp);
            }
        }

        UserAssetVO vo = new UserAssetVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvailableFunds(nz(user.getAvailableFunds()));
        vo.setFrozenFunds(nz(user.getFrozenFunds()));
        vo.setMarketValue(marketValue);
        vo.setFloatingProfit(floatingProfit);
        vo.setTotalAssets(vo.getAvailableFunds().add(vo.getFrozenFunds()).add(marketValue));
        vo.setPositions(positions);
        return vo;
    }

    // ===================================================================
    // 快照
    // ===================================================================
    @Override
    public UserAssetSnapshot upsertTodaySnapshot(Long userId) {
        UserAssetVO asset = buildCurrentAsset(userId);
        if (asset == null) return null;
        LocalDate today = LocalDate.now();

        // 找昨日（或最近一笔）快照计算"当日收益"
        UserAssetSnapshot prev = userAssetSnapshotService.getOne(
                new LambdaQueryWrapper<UserAssetSnapshot>()
                        .eq(UserAssetSnapshot::getUserId, userId)
                        .lt(UserAssetSnapshot::getSnapshotDate, today)
                        .orderByDesc(UserAssetSnapshot::getSnapshotDate)
                        .last("LIMIT 1"),
                false
        );
        BigDecimal dailyProfit = BigDecimal.ZERO;
        if (prev != null && prev.getTotalAssets() != null) {
            dailyProfit = asset.getTotalAssets().subtract(prev.getTotalAssets())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // upsert 今日记录
        UserAssetSnapshot today_ = userAssetSnapshotService.getOne(
                new LambdaQueryWrapper<UserAssetSnapshot>()
                        .eq(UserAssetSnapshot::getUserId, userId)
                        .eq(UserAssetSnapshot::getSnapshotDate, today),
                false
        );
        if (today_ == null) {
            today_ = new UserAssetSnapshot();
            today_.setUserId(userId);
            today_.setSnapshotDate(today);
            today_.setCreatedAt(LocalDateTime.now());
        }
        today_.setTotalAssets(asset.getTotalAssets());
        today_.setAvailableFunds(asset.getAvailableFunds());
        today_.setMarketValue(asset.getMarketValue());
        today_.setPositionProfit(asset.getFloatingProfit());
        today_.setDailyProfit(dailyProfit);
        userAssetSnapshotService.saveOrUpdate(today_);
        return today_;
    }

    @Override
    public int upsertAllUsersTodaySnapshot() {
        List<User> users = userService.list();
        int success = 0;
        for (User u : users) {
            try {
                if (upsertTodaySnapshot(u.getId()) != null) {
                    success++;
                }
            } catch (Exception e) {
                log.warn("拍快照失败 user={} : {}", u.getId(), e.getMessage());
            }
        }
        return success;
    }

    @Override
    public List<UserAssetSnapshot> getCurve(Long userId, int days) {
        if (days <= 0) days = 30;
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        return userAssetSnapshotService.list(
                new LambdaQueryWrapper<UserAssetSnapshot>()
                        .eq(UserAssetSnapshot::getUserId, userId)
                        .ge(UserAssetSnapshot::getSnapshotDate, from)
                        .orderByAsc(UserAssetSnapshot::getSnapshotDate)
        );
    }

    // ===================================================================
    // 工具
    // ===================================================================
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }
}
