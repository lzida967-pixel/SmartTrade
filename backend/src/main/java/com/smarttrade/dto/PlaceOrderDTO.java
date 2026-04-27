package com.smarttrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单请求 DTO
 */
@Data
public class PlaceOrderDTO {

    /**
     * 股票代码
     */
    @NotBlank(message = "股票代码不能为空")
    private String stockCode;

    /**
     * 方向: BUY / SELL
     */
    @NotBlank(message = "交易方向不能为空")
    private String direction;

    /**
     * 类型: LIMIT(限价) / MARKET(市价)
     * 简化版：均按"最新价立即成交"撮合
     */
    private String orderType;

    /**
     * 委托价（限价单必填；市价单可不填，按最新价成交）
     */
    private BigDecimal price;

    /**
     * 委托数量（必须 100 的整数倍）
     */
    @NotNull(message = "委托数量不能为空")
    @Min(value = 100, message = "委托数量至少 100 股")
    private Integer quantity;

    /**
     * 可选备注
     */
    private String remark;
}
