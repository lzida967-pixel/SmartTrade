package com.smarttrade.controller;

import com.smarttrade.common.Result;
import com.smarttrade.service.PredictionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型实证评估接口（普通用户可访问）。
 *
 * <p>区别于 {@code /admin/prediction/*}，这一组路径供前端"模型实证回顾"页使用，
 * 仅提供只读统计，不暴露强制核对、模型管理等敏感操作。
 *
 * <ul>
 *   <li>GET /prediction/evaluation/overview?days=30  &nbsp;&nbsp;各模型整体准确率 + 3×3 混淆矩阵</li>
 *   <li>GET /prediction/evaluation/timeline?days=30  &nbsp;&nbsp;按日的三模型准确率折线</li>
 *   <li>GET /prediction/evaluation/by-stock?days=30&model=lgbm_v1&limit=20  &nbsp;&nbsp;股票命中率排行</li>
 * </ul>
 */
@RestController
@RequestMapping("/prediction/evaluation")
public class PredictionEvaluationController {

    @Autowired
    private PredictionLogService predictionLogService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        return Result.success(predictionLogService.computeOverview(days == null ? 30 : days));
    }

    @GetMapping("/timeline")
    public Result<List<Map<String, Object>>> timeline(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        return Result.success(predictionLogService.computeTimeline(days == null ? 30 : days));
    }

    @GetMapping("/by-stock")
    public Result<List<Map<String, Object>>> byStock(
            @RequestParam(value = "days", defaultValue = "30") Integer days,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return Result.success(predictionLogService.computeByStock(
                days == null ? 30 : days,
                model,
                limit == null ? 20 : limit));
    }
}
