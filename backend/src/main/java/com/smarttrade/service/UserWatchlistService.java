package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smarttrade.entity.UserWatchlist;

import java.util.List;

public interface UserWatchlistService extends IService<UserWatchlist> {

    /**
     * 列出某用户的自选股（按 sortOrder 升序、相同则按 createdAt 升序）
     */
    List<UserWatchlist> listByUser(Long userId);

    /**
     * 添加自选股，已存在则忽略，返回是否新增成功
     */
    boolean addWatchlist(Long userId, String stockCode);

    /**
     * 移除自选股，返回是否删除了一行
     */
    boolean removeWatchlist(Long userId, String stockCode);

    /**
     * 是否已收藏
     */
    boolean exists(Long userId, String stockCode);
}
