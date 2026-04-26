package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("zidatrade_stock_info")
public class StockInfo {
    @TableId
    private String stockCode;
    private String market;
    private String stockName;
    private String plateType;
    private String industryName;
    private LocalDate listingDate;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}