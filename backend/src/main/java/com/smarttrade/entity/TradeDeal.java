package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单成交明细
 */
@Data
@TableName("zidatrade_trade_deal")
public class TradeDeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private String stockCode;

    private BigDecimal dealPrice;
    private Integer dealQuantity;
    private BigDecimal dealAmount;

    private LocalDateTime dealTime;
    private LocalDateTime createdAt;
}
