package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 预测日志
 */
@Data
@TableName("zidatrade_ai_predict_log")
public class AiPredictLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stockCode;
    private LocalDate targetDate;

    private BigDecimal predictPrice;
    private BigDecimal predictHighPrice;
    private BigDecimal predictLowPrice;

    private BigDecimal confidenceScore;
    /**
     * BUY / HOLD / SELL
     */
    private String predictSignal;

    private BigDecimal actualPrice;
    private Integer isVerified;

    private String modelVersion;
    private String sourceType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
