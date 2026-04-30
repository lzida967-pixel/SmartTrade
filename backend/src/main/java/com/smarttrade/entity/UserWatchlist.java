package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自选股
 */
@Data
@TableName("zidatrade_user_watchlist")
public class UserWatchlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String stockCode;

    private Integer sortOrder;
    private String remark;

    private LocalDateTime createdAt;
}
