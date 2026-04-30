package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.common.Result;
import com.smarttrade.entity.AuditLog;
import com.smarttrade.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询（仅 ADMIN 可访问）
 *
 * 设计原则：
 *  - 不允许任何删除/修改 endpoint，合规要求日志只能进不能出
 *  - 多条件分页过滤：用户、分类、动作、结果、时间范围、目标对象
 */
@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public Result<Page<AuditLog>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        Page<AuditLog> p = auditLogService.queryPage(page, size, username, category, action, result, targetId, startTime, endTime);
        return Result.success(p);
    }

    /**
     * 返回前端筛选下拉用的字典：分类、动作、结果
     */
    @GetMapping("/dict")
    public Result<Map<String, List<Map<String, String>>>> dict() {
        List<Map<String, String>> categories = Arrays.asList(
                opt("AUTH",         "认证 / 登录"),
                opt("TRADE",        "交易"),
                opt("ACCOUNT",      "账户"),
                opt("ADMIN_USER",   "管理-用户"),
                opt("ADMIN_STOCK",  "管理-股票"),
                opt("ADMIN_ORDER",  "管理-订单")
        );
        List<Map<String, String>> actions = Arrays.asList(
                opt("LOGIN",                "登录"),
                opt("REGISTER",             "注册"),
                opt("PLACE_ORDER",          "下单"),
                opt("CANCEL_ORDER",         "撤单"),
                opt("ADJUST_FUNDS",         "调整资金"),
                opt("CHANGE_ROLE",          "改角色"),
                opt("CHANGE_STATUS",        "改启用状态"),
                opt("RESET_PASSWORD",       "重置密码"),
                opt("ADD_STOCK",            "新增股票"),
                opt("DELETE_STOCK",         "删除股票"),
                opt("TRIGGER_SYNC_ALL",     "触发全量同步"),
                opt("TRIGGER_SYNC_MISSING", "触发缺失同步"),
                opt("FORCE_CANCEL",         "强制撤单")
        );
        List<Map<String, String>> results = Arrays.asList(
                opt("SUCCESS", "成功"),
                opt("FAILURE", "失败")
        );
        return Result.success(Map.of(
                "categories", categories,
                "actions",    actions,
                "results",    results
        ));
    }

    private static Map<String, String> opt(String value, String label) {
        return Map.of("value", value, "label", label);
    }
}
