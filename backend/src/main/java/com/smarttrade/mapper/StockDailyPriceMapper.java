package com.smarttrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttrade.entity.StockDailyPrice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockDailyPriceMapper extends BaseMapper<StockDailyPrice> {
}
