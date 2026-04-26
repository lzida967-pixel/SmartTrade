package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户持仓
 */
@Data
@TableName("zidatrade_user_position")
public class UserPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String stockCode;

    private Integer quantity;
    private Integer availableQuantity;
    private Integer frozenQuantity;

    private BigDecimal costPrice;
    private BigDecimal latestPrice;
    private BigDecimal marketValue;
    private BigDecimal floatingProfit;

    private LocalDate lastTradeDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
