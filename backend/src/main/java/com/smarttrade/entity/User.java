package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户表实体类
 */
@Data
@TableName("zidatrade_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String role; // 角色，例如：USER, ADMIN

    private String riskLevel;

    /**
     * 总资产
     */
    private BigDecimal totalAssets;

    /**
     * 可用资金
     */
    private BigDecimal availableFunds;

    /**
     * 冻结资金 (挂单但未成交等情况使用)
     */
    private BigDecimal frozenFunds;

    /**
     * 状态(1:正常, 0:禁用)
     */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}