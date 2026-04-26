package com.smarttrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttrade.entity.StockDailyPrice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockDailyPriceMapper extends BaseMapper<StockDailyPrice> {

    /**
     * 批量 upsert：按 (stock_code, trade_date) 唯一键存在则更新，不存在则插入。
     * 一次 SQL 写入整批数据，避免一行一行 select + insert 造成的 N+1 与日志噪音。
     */
    @Insert({"<script>",
            "INSERT INTO zidatrade_stock_daily_price",
            "(stock_code, trade_date, open_price, close_price, high_price, low_price,",
            " pre_close_price, volume, turnover_amount, amplitude, change_percent,",
            " created_at, updated_at) VALUES",
            "<foreach collection='list' item='it' separator=','>",
            "(#{it.stockCode}, #{it.tradeDate}, #{it.openPrice}, #{it.closePrice},",
            " #{it.highPrice}, #{it.lowPrice}, #{it.preClosePrice}, #{it.volume},",
            " #{it.turnoverAmount}, #{it.amplitude}, #{it.changePercent},",
            " #{it.createdAt}, #{it.updatedAt})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE",
            " open_price       = VALUES(open_price),",
            " close_price      = VALUES(close_price),",
            " high_price       = VALUES(high_price),",
            " low_price        = VALUES(low_price),",
            " pre_close_price  = VALUES(pre_close_price),",
            " volume           = VALUES(volume),",
            " turnover_amount  = VALUES(turnover_amount),",
            " amplitude        = VALUES(amplitude),",
            " change_percent   = VALUES(change_percent),",
            " updated_at       = VALUES(updated_at)",
            "</script>"})
    int upsertBatch(@Param("list") List<StockDailyPrice> list);
}
