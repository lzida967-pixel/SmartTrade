package com.smarttrade.vo;

import com.smarttrade.entity.UserPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户资产聚合 VO
 */
@Data
public class UserAssetVO {

    private Long userId;
    private String username;
    private String nickname;

    /** 总资产 = 可用资金 + 冻结资金 + 持仓市值 */
    private BigDecimal totalAssets;
    private BigDecimal availableFunds;
    private BigDecimal frozenFunds;

    /** 当前持仓市值 */
    private BigDecimal marketValue;
    /** 浮动盈亏 */
    private BigDecimal floatingProfit;

    /** 持仓列表 */
    private List<UserPosition> positions;
}
