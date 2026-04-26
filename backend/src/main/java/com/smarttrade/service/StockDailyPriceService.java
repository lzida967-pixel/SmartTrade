package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smarttrade.entity.StockDailyPrice;

import java.util.List;

public interface StockDailyPriceService extends IService<StockDailyPrice> {

    /**
     * 查询指定股票的最近 N 条日线
     */
    List<StockDailyPrice> listRecentByStock(String stockCode, int limit);

    /**
     * 批量插入或更新（按 stock_code + trade_date 唯一）
     */
    void saveOrUpdateBatchByUnique(List<StockDailyPrice> list);
}
