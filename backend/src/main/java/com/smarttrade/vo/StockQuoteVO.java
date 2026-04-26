package com.smarttrade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 股票实时行情 VO
 */
@Data
public class StockQuoteVO {
    private String stockCode;
    private String stockName;
    private String market;
    private String plateType;
    private String industryName;

    /** 最新价 */
    private BigDecimal latestPrice;
    /** 今开 */
    private BigDecimal openPrice;
    /** 最高 */
    private BigDecimal highPrice;
    /** 最低 */
    private BigDecimal lowPrice;
    /** 昨收 */
    private BigDecimal preClosePrice;
    /** 涨跌额 */
    private BigDecimal changeAmount;
    /** 涨跌幅(%) */
    private BigDecimal changePercent;
    /** 成交量(手) */
    private Long volume;
    /** 成交额(元) */
    private BigDecimal turnoverAmount;
    /** 振幅(%) */
    private BigDecimal amplitude;
    /** 换手率(%) */
    private BigDecimal turnoverRate;
}
