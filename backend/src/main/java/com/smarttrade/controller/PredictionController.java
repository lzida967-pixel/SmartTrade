package com.smarttrade.controller;

import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.dto.PredictionDTO;
import com.smarttrade.service.PredictionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LightGBM 实时预测网关：转发到外部 Python FastAPI 服务。
 *
 * 路径前缀：/prediction
 *   GET /prediction/lgbm/{code}   单只股票 T+5 三分类预测
 *   GET /prediction/health        预测服务健康状态
 */
@RestController
@RequestMapping("/prediction")
public class PredictionController {

    @Autowired
    private PredictionClient predictionClient;

    @GetMapping("/lgbm/{code}")
    @AuditLog(category = "PREDICTION", action = "LGBM_PREDICT",
            targetType = "STOCK", target = "#code",
            summary = "LightGBM 预测 #{#code}",
            includeArgs = {"code"})
    public Result<PredictionDTO> predict(@PathVariable("code") String code) {
        try {
            PredictionDTO dto = predictionClient.predict(code);
            return Result.success(dto);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(503, e.getMessage());
        }
    }

    @GetMapping("/health")
    public Result<Boolean> health() {
        return Result.success(predictionClient.isHealthy());
    }
}
