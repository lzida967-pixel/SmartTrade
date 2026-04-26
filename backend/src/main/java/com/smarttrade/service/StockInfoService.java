package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smarttrade.entity.StockInfo;

public interface StockInfoService extends IService<StockInfo> {
    void initMockStocks();
}
