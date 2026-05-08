package com.smarttrade.task;

import com.smarttrade.service.PredictionLogService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 预测真实结果自动核对任务
 *
 * 职责：
 *   1. 每个交易日 16:50（默认）对所有 is_verified=0 且已凑够 T+5 的预测记录回填真实涨跌结果。
 *      该时间点比行情同步（16:30）和资产快照（16:35）更晚，确保 daily_price 已入库。
 *   2. 应用启动后异步跑一次，及时回填上次启动期间错过的待核对记录。
 */
@Slf4j
@Component
public class PredictionVerifyScheduler {

    @Autowired
    private PredictionLogService predictionLogService;

    @Value("${smarttrade.prediction-verify.cold-start:true}")
    private boolean coldStart;

    /**
     * 每个交易日 16:50 自动核对一次。
     */
    @Scheduled(cron = "${smarttrade.prediction-verify.cron:0 50 16 * * MON-FRI}")
    public void scheduledVerify() {
        log.info("[定时] 开始核对 AI 预测准确率...");
        long start = System.currentTimeMillis();
        int verified = predictionLogService.verifyAll();
        log.info("[定时] AI 预测核对完成: 新增核对 {} 条, 耗时 {} ms",
                verified, System.currentTimeMillis() - start);
    }

    @PostConstruct
    public void initOnBoot() {
        if (!coldStart) return;
        new Thread(() -> {
            try {
                Thread.sleep(8000); // 等其它启动任务完成
                int verified = predictionLogService.verifyAll();
                if (verified > 0) {
                    log.info("[冷启动] AI 预测核对补齐 {} 条", verified);
                }
            } catch (Exception e) {
                log.warn("AI 预测核对冷启动失败: {}", e.getMessage());
            }
        }, "prediction-verify-coldstart").start();
    }
}
