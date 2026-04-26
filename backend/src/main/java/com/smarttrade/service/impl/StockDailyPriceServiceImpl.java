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
        for (StockDailyPrice item : list) {
            StockDailyPrice exists = this.getOne(
                    new LambdaQueryWrapper<StockDailyPrice>()
                            .eq(StockDailyPrice::getStockCode, item.getStockCode())
                            .eq(StockDailyPrice::getTradeDate, item.getTradeDate()),
                    false
            );
            if (exists == null) {
                this.save(item);
            } else {
                item.setId(exists.getId());
                this.updateById(item);
            }
        }
    }
}
