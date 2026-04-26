package com.smarttrade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.common.Result;
import com.smarttrade.entity.AiPredictLog;
import com.smarttrade.service.AiPredictLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 预测结果查询接口
 *
 * 提供三种典型查询场景：
 *   1. 单只股票最新预测：           GET /ai/predict/latest/{code}
 *   2. 单只股票历史预测回溯：       GET /ai/predict/history/{code}?limit=30
 *   3. 按日期 / 信号检索预测列表： GET /ai/predict/list?date=2026-04-26&signal=BUY&page=1&size=20
 *
 * 同时预留写入接口供外部 Python/FastAPI 模型服务回调：
 *   POST /ai/predict
 */
@RestController
@RequestMapping("/ai/predict")
public class AiPredictController {

    @Autowired
    private AiPredictLogService aiPredictLogService;

    /**
     * 查询某只股票最新一条预测
     */
    @GetMapping("/latest/{code}")
    public Result<AiPredictLog> latest(@PathVariable("code") String code) {
        AiPredictLog log = aiPredictLogService.getOne(
                new LambdaQueryWrapper<AiPredictLog>()
                        .eq(AiPredictLog::getStockCode, code)
                        .orderByDesc(AiPredictLog::getTargetDate)
                        .orderByDesc(AiPredictLog::getId)
                        .last("LIMIT 1"),
                false
        );
        return Result.success(log);
    }

    /**
     * 查询某只股票的历史预测（按目标日期倒序），默认 30 条
     */
    @GetMapping("/history/{code}")
    public Result<List<AiPredictLog>> history(@PathVariable("code") String code,
                                              @RequestParam(value = "limit", defaultValue = "30") Integer limit) {
        if (limit == null || limit <= 0 || limit > 500) {
            limit = 30;
        }
        Page<AiPredictLog> page = new Page<>(1, limit);
        List<AiPredictLog> list = aiPredictLogService.page(
                page,
                new LambdaQueryWrapper<AiPredictLog>()
                        .eq(AiPredictLog::getStockCode, code)
                        .orderByDesc(AiPredictLog::getTargetDate)
                        .orderByDesc(AiPredictLog::getId)
        ).getRecords();
        return Result.success(list);
    }

    /**
     * 按日期 / 预测信号检索预测列表，分页
     */
    @GetMapping("/list")
    public Result<Page<AiPredictLog>> list(@RequestParam(value = "date", required = false) String date,
                                           @RequestParam(value = "signal", required = false) String signal,
                                           @RequestParam(value = "stockCode", required = false) String stockCode,
                                           @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                           @RequestParam(value = "size", defaultValue = "20") Integer pageSize) {
        Page<AiPredictLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiPredictLog> q = new LambdaQueryWrapper<AiPredictLog>()
                .orderByDesc(AiPredictLog::getTargetDate)
                .orderByDesc(AiPredictLog::getId);
        if (date != null && !date.isBlank()) {
            q.eq(AiPredictLog::getTargetDate, LocalDate.parse(date.trim()));
        }
        if (signal != null && !signal.isBlank()) {
            q.eq(AiPredictLog::getPredictSignal, signal.trim().toUpperCase());
        }
        if (stockCode != null && !stockCode.isBlank()) {
            q.eq(AiPredictLog::getStockCode, stockCode.trim());
        }
        return Result.success(aiPredictLogService.page(page, q));
    }

    /**
     * 写入一条预测结果（供外部预测服务回调，便于联调）
     */
    @PostMapping
    public Result<AiPredictLog> create(@RequestBody AiPredictLog body) {
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(LocalDateTime.now());
        }
        body.setUpdatedAt(LocalDateTime.now());
        if (body.getIsVerified() == null) {
            body.setIsVerified(0);
        }
        if (body.getSourceType() == null || body.getSourceType().isBlank()) {
            body.setSourceType("API");
        }
        aiPredictLogService.save(body);
        return Result.success(body);
    }
}
