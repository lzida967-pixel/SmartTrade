package com.smarttrade.controller;

import com.smarttrade.common.Result;
import com.smarttrade.entity.UserWatchlist;
import com.smarttrade.service.UserWatchlistService;
import com.smarttrade.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自选股 / 收藏夹
 */
@RestController
@RequestMapping("/watchlist")
public class WatchlistController {

    @Autowired
    private UserWatchlistService watchlistService;

    /**
     * 当前用户的自选股代码列表（轻量版，给前端做 hashmap 用）
     */
    @GetMapping("/codes")
    public Result<List<String>> codes() {
        Long userId = UserContext.getUserId();
        List<String> codes = watchlistService.listByUser(userId).stream()
                .map(UserWatchlist::getStockCode)
                .toList();
        return Result.success(codes);
    }

    /**
     * 当前用户的自选股完整列表（含 sortOrder / createdAt / remark）
     */
    @GetMapping("/list")
    public Result<List<UserWatchlist>> list() {
        Long userId = UserContext.getUserId();
        return Result.success(watchlistService.listByUser(userId));
    }

    /**
     * 添加自选股
     */
    @PostMapping("/{stockCode}")
    public Result<Boolean> add(@PathVariable("stockCode") String stockCode) {
        Long userId = UserContext.getUserId();
        boolean added = watchlistService.addWatchlist(userId, stockCode);
        return Result.success(added, added ? "已加入自选" : "已在自选中");
    }

    /**
     * 移除自选股
     */
    @DeleteMapping("/{stockCode}")
    public Result<Boolean> remove(@PathVariable("stockCode") String stockCode) {
        Long userId = UserContext.getUserId();
        boolean removed = watchlistService.removeWatchlist(userId, stockCode);
        return Result.success(removed, removed ? "已移除" : "未在自选中");
    }
}
