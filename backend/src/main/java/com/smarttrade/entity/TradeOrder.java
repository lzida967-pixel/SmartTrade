package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委托订单
 */
@Data
@TableName("zidatrade_trade_order")
public class TradeOrder {

    @TableId(type = IdType.INPUT)
    private String orderNo;

    private Long userId;
    private String stockCode;

    /**
     * BUY / SELL
     */
    private String direction;

    /**
     * LIMIT / MARKET
     */
    private String orderType;

    private BigDecimal entrustPrice;
    private Integer entrustQuantity;
    private BigDecimal matchPrice;
    private Integer matchQuantity;

    private BigDecimal frozenAmount;
    private BigDecimal turnoverAmount;

    /**
     * PENDING / PARTIAL / FILLED / CANCELED
     */
    private String orderStatus;

    private LocalDateTime orderTime;
    private LocalDateTime matchTime;

    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
