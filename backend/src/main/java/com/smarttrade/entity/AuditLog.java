package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 */
@Data
@TableName("zidatrade_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String role;

    /** AUTH / TRADE / ACCOUNT / ADMIN_USER / ADMIN_STOCK / ADMIN_ORDER */
    private String category;
    /** LOGIN / PLACE_ORDER / ... */
    private String action;

    private String targetType;
    private String targetId;

    private String summary;
    private String detailsJson;

    /** SUCCESS / FAILURE */
    private String result;
    private String errorMsg;

    private String ip;
    private String userAgent;
    private Integer costMs;

    private LocalDateTime createdAt;
}
