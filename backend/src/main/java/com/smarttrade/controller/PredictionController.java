package com.smarttrade.controller;

import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.dto.PredictionDTO;
import com.smarttrade.service.PredictionClient;
import com.smarttrade.service.PredictionReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * LightGBM 实时预测网关：转发到外部 Python FastAPI 服务。
 *
 * 路径前缀：/prediction
 *   GET /prediction/lgbm/{code}             单只股票 T+5 三分类预测（?model=lgbm|xgb）
 *   GET /prediction/health                  预测服务健康状态
 */
@Slf4j
@RestController
@RequestMapping("/prediction")
public class PredictionController {

    @Autowired
    private PredictionClient predictionClient;

    @Autowired
    private PredictionReportService reportService;

    @GetMapping("/lgbm/{code}")
    @AuditLog(category = "PREDICTION", action = "PREDICT",
            targetType = "STOCK", target = "#code",
            summary = "预测 #{#code} (model=#{#model})",
            includeArgs = {"code", "model"})
    public Result<PredictionDTO> predict(
            @PathVariable("code") String code,
            @RequestParam(value = "model", required = false, defaultValue = "lgbm") String model) {
        try {
            PredictionDTO dto = predictionClient.predict(code, model);
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

    /**
     * 基于预测结果生成 AI 分析报告（SSE 流式）。
     * 事件类型：reasoning（思考）/ delta（正文）/ error / done
     *
     * 注意：此端点**不**使用 @AuditLog AOP，因为 AOP 的 finally 块会同步查 DB 写日志，
     * 阻塞 SseEmitter 被 Spring MVC 绑定到 response，导致 token 被缓冲、流式失效。
     * 审计记录改由 PredictionReportService 内部异步落库。
     */
    @GetMapping(value = "/lgbm/{code}/report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter report(
            @PathVariable("code") String code,
            @RequestParam(value = "model", required = false, defaultValue = "lgbm") String model,
            HttpServletResponse response) {
        // 阻断中间链路（Tomcat / Vite proxy / Nginx 等）的 SSE 缓冲
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setCharacterEncoding("UTF-8");

        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> log.debug("[Report] SSE 完成 code={}", code));
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.debug("[Report] SSE 异常 code={}: {}", code, t.getMessage()));

        reportService.streamReport(code, model, emitter);
        return emitter;
    }
}
