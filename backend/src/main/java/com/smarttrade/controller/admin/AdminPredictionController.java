package com.smarttrade.controller.admin;

import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.service.PredictionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - AI 模型表现监控。
 *
 * <p>路径：
 * <ul>
 *   <li>{@code GET  /admin/prediction/stats?days=30}  —— 各模型最近 N 天准确率</li>
 *   <li>{@code POST /admin/prediction/verify}        —— 立即用最新行情核对待校验记录</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/prediction")
public class AdminPredictionController {

    @Autowired
    private PredictionLogService predictionLogService;

    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        return Result.success(predictionLogService.computeStats(days == null ? 30 : days));
    }

    @PostMapping("/verify")
    @AuditLog(category = "PREDICTION", action = "VERIFY_PREDICTION",
            targetType = "PREDICTION", target = "all",
            summary = "校验 AI 预测准确率")
    public Result<Map<String, Object>> verify() {
        int verified = predictionLogService.verifyAll();
        Map<String, Object> data = new HashMap<>();
        data.put("verifiedCount", verified);
        return Result.success(data);
    }
}
