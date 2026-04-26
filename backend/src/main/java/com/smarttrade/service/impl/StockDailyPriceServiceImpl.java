package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.StockDailyPrice;
import com.smarttrade.mapper.StockDailyPriceMapper;
import com.smarttrade.service.StockDailyPriceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class StockDailyPriceServiceImpl extends ServiceImpl<StockDailyPriceMapper, StockDailyPrice>
        implements StockDailyPriceService {

    @Override
    public List<StockDailyPrice> listRecentByStock(String stockCode, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        Page<StockDailyPrice> page = new Page<>(1, limit);
        LambdaQueryWrapper<StockDailyPrice> wrapper = new LambdaQueryWrapper<StockDailyPrice>()
                .eq(StockDailyPrice::getStockCode, stockCode)
                .orderByDesc(StockDailyPrice::getTradeDate);
        return this.page(page, wrapper).getRecords();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateBatchByUnique(List<StockDailyPrice> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 使用 MySQL 原生 INSERT ... ON DUPLICATE KEY UPDATE 一次写入整批，
        // 替代逐行 select + insert，避免 N+1 与海量 SQL 日志。
        // 防御：一次 SQL 不要太大，按 500 行分块。
        final int chunk = 500;
        for (int i = 0; i < list.size(); i += chunk) {
            int to = Math.min(i + chunk, list.size());
            this.baseMapper.upsertBatch(list.subList(i, to));
        }
    }
}
