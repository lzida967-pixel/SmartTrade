package com.smarttrade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * K 线单点 VO
 */
@Data
public class KlinePointVO {
    private String tradeDate;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private BigDecimal changePercent;
    private BigDecimal changeAmount;
    private BigDecimal turnoverRate;
}
