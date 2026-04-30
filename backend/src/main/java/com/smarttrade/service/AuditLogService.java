package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smarttrade.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.Map;

public interface AuditLogService extends IService<AuditLog> {

    /**
     * 异步写入一条日志，捕获异常仅打印不外抛，避免影响业务流程
     */
    void recordAsync(AuditLog log);

    /**
     * 同步写一条简化日志（用于无法走 AOP 的场景，如登录前/登录失败）
     */
    void record(String category, String action, Long userId, String username,
                String role, String targetType, String targetId,
                String summary, Map<String, Object> details,
                boolean success, String errorMsg);

    /**
     * 管理端分页查询，支持多条件过滤
     */
    Page<AuditLog> queryPage(Integer page, Integer size,
                             String username, String category, String action,
                             String result, String targetId,
                             LocalDateTime startTime, LocalDateTime endTime);
}
