package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.UserWatchlist;
import com.smarttrade.mapper.UserWatchlistMapper;
import com.smarttrade.service.UserWatchlistService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserWatchlistServiceImpl extends ServiceImpl<UserWatchlistMapper, UserWatchlist>
        implements UserWatchlistService {

    @Override
    public List<UserWatchlist> listByUser(Long userId) {
        return list(
                new LambdaQueryWrapper<UserWatchlist>()
                        .eq(UserWatchlist::getUserId, userId)
                        .orderByAsc(UserWatchlist::getSortOrder)
                        .orderByAsc(UserWatchlist::getCreatedAt)
        );
    }

    @Override
    public boolean addWatchlist(Long userId, String stockCode) {
        if (exists(userId, stockCode)) {
            return false;
        }
        UserWatchlist w = new UserWatchlist();
        w.setUserId(userId);
        w.setStockCode(stockCode);
        // 默认排序值用当前最大 sortOrder + 10，让新加入的排到末尾
        Integer maxOrder = getBaseMapper().selectList(
                new LambdaQueryWrapper<UserWatchlist>()
                        .eq(UserWatchlist::getUserId, userId)
                        .select(UserWatchlist::getSortOrder)
                        .orderByDesc(UserWatchlist::getSortOrder)
                        .last("LIMIT 1")
        ).stream().findFirst().map(UserWatchlist::getSortOrder).orElse(0);
        w.setSortOrder(maxOrder + 10);
        w.setCreatedAt(LocalDateTime.now());
        try {
            return save(w);
        } catch (DuplicateKeyException e) {
            // 并发场景下避免抛出
            return false;
        }
    }

    @Override
    public boolean removeWatchlist(Long userId, String stockCode) {
        return remove(
                new LambdaQueryWrapper<UserWatchlist>()
                        .eq(UserWatchlist::getUserId, userId)
                        .eq(UserWatchlist::getStockCode, stockCode)
        );
    }

    @Override
    public boolean exists(Long userId, String stockCode) {
        return count(
                new LambdaQueryWrapper<UserWatchlist>()
                        .eq(UserWatchlist::getUserId, userId)
                        .eq(UserWatchlist::getStockCode, stockCode)
        ) > 0;
    }
}
