package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票日线行情
 */
@Data
@TableName("zidatrade_stock_daily_price")
public class StockDailyPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stockCode;
    private LocalDate tradeDate;

    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal preClosePrice;

    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private BigDecimal changePercent;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
