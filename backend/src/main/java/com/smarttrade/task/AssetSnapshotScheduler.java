package com.smarttrade.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.entity.UserAssetSnapshot;
import com.smarttrade.service.AssetService;
import com.smarttrade.service.UserAssetSnapshotService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 用户资产每日快照任务
 *
 * 职责：
 *   1. 每个交易日 16:30 后（默认 16:35）给所有用户拍当日快照（依赖 16:30 的日 K 同步先跑完）。
 *   2. 应用启动后，如果当前用户库里有人但今天的快照表为空，立即跑一次（确保启动当天就有曲线点）。
 */
@Slf4j
@Component
public class AssetSnapshotScheduler {

    @Autowired
    private AssetService assetService;

    @Autowired
    private UserAssetSnapshotService userAssetSnapshotService;

    @Value("${smarttrade.asset-snapshot.cold-start:true}")
    private boolean coldStart;

    /**
     * 每日 16:35（默认）拍一次快照。比行情同步任务（16:30）晚 5 分钟，确保最新价已落库。
     */
    @Scheduled(cron = "${smarttrade.asset-snapshot.cron:0 35 16 * * MON-FRI}")
    public void scheduledSnapshot() {
        log.info("[定时] 开始拍用户资产快照...");
        long start = System.currentTimeMillis();
        int success = assetService.upsertAllUsersTodaySnapshot();
        log.info("[定时] 资产快照完成: 成功 {} 个用户, 耗时 {} ms",
                success, System.currentTimeMillis() - start);
    }

    /**
     * 启动后检查今日是否已有快照，没有则异步跑一次
     */
    @PostConstruct
    public void initOnBoot() {
        if (!coldStart) return;
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 等其它启动任务完成
                long todayCount = userAssetSnapshotService.count(
                        new LambdaQueryWrapper<UserAssetSnapshot>()
                                .eq(UserAssetSnapshot::getSnapshotDate, LocalDate.now())
                );
                if (todayCount > 0) {
                    log.info("今日资产快照已存在 {} 条，跳过冷启动", todayCount);
                    return;
                }
                log.info("[冷启动] 今日资产快照为空，立即拍一次...");
                int success = assetService.upsertAllUsersTodaySnapshot();
                log.info("[冷启动] 资产快照完成: 成功 {} 个用户", success);
            } catch (Exception e) {
                log.warn("资产快照冷启动失败: {}", e.getMessage());
            }
        }, "asset-snapshot-coldstart").start();
    }
}
