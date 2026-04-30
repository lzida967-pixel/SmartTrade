package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrade.entity.AuditLog;
import com.smarttrade.mapper.AuditLogMapper;
import com.smarttrade.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @Async("auditLogExecutor")
    public void recordAsync(AuditLog auditLog) {
        try {
            if (auditLog.getCreatedAt() == null) {
                auditLog.setCreatedAt(LocalDateTime.now());
            }
            // 字段长度防御
            auditLog.setUserAgent(truncate(auditLog.getUserAgent(), 255));
            auditLog.setSummary(truncate(auditLog.getSummary(), 255));
            auditLog.setDetailsJson(truncate(auditLog.getDetailsJson(), 2000));
            auditLog.setErrorMsg(truncate(auditLog.getErrorMsg(), 500));
            save(auditLog);
        } catch (Exception e) {
            // 审计写库失败不影响主业务，仅打印
            log.error("[Audit] 写入审计日志失败: action={}, err={}",
                    auditLog.getAction(), e.getMessage(), e);
        }
    }

    @Override
    public void record(String category, String action, Long userId, String username,
                       String role, String targetType, String targetId,
                       String summary, Map<String, Object> details,
                       boolean success, String errorMsg) {
        AuditLog log = new AuditLog();
        log.setCategory(category);
        log.setAction(action);
        log.setUserId(userId);
        log.setUsername(username);
        log.setRole(role);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSummary(summary);
        log.setDetailsJson(toJson(details));
        log.setResult(success ? "SUCCESS" : "FAILURE");
        log.setErrorMsg(errorMsg);
        recordAsync(log);
    }

    @Override
    public Page<AuditLog> queryPage(Integer page, Integer size,
                                    String username, String category, String action,
                                    String result, String targetId,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            qw.like(AuditLog::getUsername, username.trim());
        }
        if (category != null && !category.isBlank()) {
            qw.eq(AuditLog::getCategory, category);
        }
        if (action != null && !action.isBlank()) {
            qw.eq(AuditLog::getAction, action);
        }
        if (result != null && !result.isBlank()) {
            qw.eq(AuditLog::getResult, result);
        }
        if (targetId != null && !targetId.isBlank()) {
            qw.eq(AuditLog::getTargetId, targetId.trim());
        }
        if (startTime != null) {
            qw.ge(AuditLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            qw.le(AuditLog::getCreatedAt, endTime);
        }
        qw.orderByDesc(AuditLog::getCreatedAt);
        return page(new Page<>(page == null ? 1 : page, size == null ? 20 : size), qw);
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(details);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
