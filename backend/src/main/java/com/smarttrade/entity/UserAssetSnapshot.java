package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资产每日快照
 */
@Data
@TableName("zidatrade_user_asset_snapshot")
public class UserAssetSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private LocalDate snapshotDate;

    private BigDecimal totalAssets;
    private BigDecimal availableFunds;
    private BigDecimal marketValue;
    private BigDecimal positionProfit;
    private BigDecimal dailyProfit;

    private LocalDateTime createdAt;
}
