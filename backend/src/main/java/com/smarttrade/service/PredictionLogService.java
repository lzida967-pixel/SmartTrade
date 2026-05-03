package com.smarttrade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.dto.PredictionDTO;
import com.smarttrade.entity.AiPredictLog;
import com.smarttrade.entity.StockDailyPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * AI 预测结果落库 + 后置校验 + 统计准确率。
 *
 * <p>设计要点：
 * <ul>
 *   <li>每次成功调用 Python 预测服务后，异步落一条 {@link AiPredictLog}</li>
 *   <li>同一 (stockCode, modelVersion, targetDate) 仅保留一条，避免缓存重复打点</li>
 *   <li>{@code targetDate} 实际记录的是 K 线 as-of 日期（建模基准日），校验时查 5 个交易日之后的收盘价</li>
 *   <li>校验依赖 {@code zidatrade_stock_daily_price} 表里的真实收盘价，不引入新的定时任务</li>
 * </ul>
 */
@Slf4j
@Service
public class PredictionLogService {

    /** T+N，与 Python 端一致，保持单源事实 */
    private static final int FORWARD_DAYS = 5;
    /** 三分类阈值（±3%），与 Python 端一致 */
    private static final BigDecimal THRESHOLD = new BigDecimal("0.03");

    @Autowired
    private AiPredictLogService aiPredictLogService;

    @Autowired
    private StockDailyPriceService stockDailyPriceService;

    // ============================================================
    // 1. 落库（异步）
    // ============================================================

    /** 异步保存一条预测记录。失败仅打日志，不影响主流程。 */
    public void saveAsync(PredictionDTO dto) {
        if (dto == null || dto.getCode() == null || dto.getAsOfDate() == null) {
            return;
        }
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "predict-log-save-" + dto.getCode());
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            try {
                doSave(dto);
            } catch (Exception e) {
                log.warn("[PredictLog] 落库失败 code={} err={}", dto.getCode(), e.getMessage());
            }
        });
    }

    private void doSave(PredictionDTO dto) {
        String code = dto.getCode();
        LocalDate asOfDate = dto.getAsOfDate();
        String modelVersion = dto.getModelVersion();

        // 去重：同一 (code, model, asOfDate) 只留一条
        Long exists = aiPredictLogService.getBaseMapper().selectCount(
                new LambdaQueryWrapper<AiPredictLog>()
                        .eq(AiPredictLog::getStockCode, code)
                        .eq(AiPredictLog::getModelVersion, modelVersion)
                        .eq(AiPredictLog::getTargetDate, asOfDate));
        if (exists != null && exists > 0) {
            return;
        }

        // 取 as-of 日期当天（或最近一个交易日）的收盘价，作为 predictPrice 基准
        BigDecimal asOfClose = findCloseOnOrBefore(code, asOfDate);
        if (asOfClose == null) {
            log.debug("[PredictLog] 无 {} {} 的收盘价，跳过落库", code, asOfDate);
            return;
        }

        AiPredictLog row = new AiPredictLog();
        row.setStockCode(code);
        row.setTargetDate(asOfDate);  // 复用为 as-of 日期，校验时查未来 5 个交易日
        row.setPredictPrice(asOfClose);
        row.setPredictSignal(labelToSignal(dto.getLabel()));
        row.setConfidenceScore(dto.getConfidence() == null
                ? null : BigDecimal.valueOf(dto.getConfidence()).setScale(4, RoundingMode.HALF_UP));
        row.setIsVerified(0);
        row.setModelVersion(modelVersion);
        row.setSourceType("API");
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        aiPredictLogService.save(row);
    }

    // ============================================================
    // 2. 手动校验：把已到期的预测的 actualPrice 填上
    // ============================================================

    /**
     * 校验所有未核对（is_verified=0）且 K 线已凑够 T+5 的预测记录。
     * @return 本次成功核对的条数
     */
    public int verifyAll() {
        List<AiPredictLog> pending = aiPredictLogService.list(
                new LambdaQueryWrapper<AiPredictLog>()
                        .eq(AiPredictLog::getIsVerified, 0)
                        .orderByAsc(AiPredictLog::getTargetDate));
        if (pending == null || pending.isEmpty()) return 0;

        int ok = 0;
        for (AiPredictLog row : pending) {
            try {
                if (verifyOne(row)) ok++;
            } catch (Exception e) {
                log.warn("[PredictLog] 校验失败 id={} err={}", row.getId(), e.getMessage());
            }
        }
        log.info("[PredictLog] 校验完成: {} / {} 条", ok, pending.size());
        return ok;
    }

    /** 单条校验。成功填了 actualPrice 返回 true。 */
    private boolean verifyOne(AiPredictLog row) {
        // 找 as-of 日之后第 N 个交易日的收盘价
        StockDailyPrice future = findNthTradingDayAfter(
                row.getStockCode(), row.getTargetDate(), FORWARD_DAYS);
        if (future == null || future.getClosePrice() == null) {
            return false;  // 还没到期，跳过
        }
        row.setActualPrice(future.getClosePrice());
        row.setIsVerified(1);
        row.setUpdatedAt(LocalDateTime.now());
        aiPredictLogService.updateById(row);
        return true;
    }

    // ============================================================
    // 3. 统计：近 N 天的准确率（按模型分组）
    // ============================================================

    /**
     * 返回每个模型版本的准确率统计。
     * accuracy = 实际三分类与预测三分类一致的比例。
     */
    public List<Map<String, Object>> computeStats(int days) {
        LocalDate since = LocalDate.now().minusDays(Math.max(days, 1));
        List<AiPredictLog> rows = aiPredictLogService.list(
                new LambdaQueryWrapper<AiPredictLog>()
                        .eq(AiPredictLog::getIsVerified, 1)
                        .ge(AiPredictLog::getTargetDate, since));

        // 按模型分桶
        Map<String, int[]> bucket = new HashMap<>();  // key -> [correct, total]
        for (AiPredictLog r : rows) {
            if (r.getActualPrice() == null || r.getPredictPrice() == null
                    || r.getPredictPrice().signum() <= 0) continue;
            String mv = r.getModelVersion() == null ? "unknown" : r.getModelVersion();
            int[] arr = bucket.computeIfAbsent(mv, k -> new int[]{0, 0});
            arr[1]++;
            if (isPredictionCorrect(r)) arr[0]++;
        }

        List<Map<String, Object>> out = new ArrayList<>();
        // 也输出未验证的总数和待验证的总数，让前端展示更全面
        Long totalUnverified = aiPredictLogService.getBaseMapper().selectCount(
                new LambdaQueryWrapper<AiPredictLog>().eq(AiPredictLog::getIsVerified, 0));

        for (Map.Entry<String, int[]> e : bucket.entrySet()) {
            int correct = e.getValue()[0];
            int total = e.getValue()[1];
            double acc = total == 0 ? 0.0 : (correct * 1.0 / total);
            Map<String, Object> m = new HashMap<>();
            m.put("modelVersion", e.getKey());
            m.put("modelName", modelDisplayName(e.getKey()));
            m.put("verifiedCount", total);
            m.put("correctCount", correct);
            m.put("accuracy", BigDecimal.valueOf(acc).setScale(4, RoundingMode.HALF_UP));
            m.put("pendingCount", totalUnverified);
            m.put("days", days);
            out.add(m);
        }
        // 保证至少有 lgbm/xgb 占位（哪怕 0 条），UI 不闪烁
        for (String k : new String[]{"lgbm_v1", "xgb_v1"}) {
            if (bucket.containsKey(k)) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("modelVersion", k);
            m.put("modelName", modelDisplayName(k));
            m.put("verifiedCount", 0);
            m.put("correctCount", 0);
            m.put("accuracy", BigDecimal.ZERO);
            m.put("pendingCount", totalUnverified);
            m.put("days", days);
            out.add(m);
        }
        out.sort((a, b) -> ((String) a.get("modelVersion")).compareTo((String) b.get("modelVersion")));
        return out;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /** 该预测是否正确：实际三分类 == 预测三分类 */
    private boolean isPredictionCorrect(AiPredictLog r) {
        BigDecimal ret = r.getActualPrice().subtract(r.getPredictPrice())
                .divide(r.getPredictPrice(), 6, RoundingMode.HALF_UP);
        int actualLabel;
        if (ret.compareTo(THRESHOLD) > 0) actualLabel = 0;
        else if (ret.compareTo(THRESHOLD.negate()) < 0) actualLabel = 2;
        else actualLabel = 1;

        Integer predLabel = signalToLabel(r.getPredictSignal());
        return predLabel != null && predLabel == actualLabel;
    }

    /** 找该股票 <= 指定日期的最近一根日 K 收盘价。 */
    private BigDecimal findCloseOnOrBefore(String stockCode, LocalDate date) {
        List<StockDailyPrice> rows = stockDailyPriceService.list(
                new LambdaQueryWrapper<StockDailyPrice>()
                        .eq(StockDailyPrice::getStockCode, stockCode)
                        .le(StockDailyPrice::getTradeDate, date)
                        .orderByDesc(StockDailyPrice::getTradeDate)
                        .last("LIMIT 1"));
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0).getClosePrice();
    }

    /** 找该股票在指定日期之后的第 N 个交易日（按表中存在的交易日计数）。 */
    private StockDailyPrice findNthTradingDayAfter(String stockCode, LocalDate after, int n) {
        List<StockDailyPrice> rows = stockDailyPriceService.list(
                new LambdaQueryWrapper<StockDailyPrice>()
                        .eq(StockDailyPrice::getStockCode, stockCode)
                        .gt(StockDailyPrice::getTradeDate, after)
                        .orderByAsc(StockDailyPrice::getTradeDate)
                        .last("LIMIT " + n));
        if (rows == null || rows.size() < n) return null;
        return rows.get(n - 1);
    }

    private static String labelToSignal(Integer label) {
        if (label == null) return "HOLD";
        return switch (label) {
            case 0 -> "BUY";
            case 2 -> "SELL";
            default -> "HOLD";
        };
    }

    private static Integer signalToLabel(String signal) {
        if (signal == null) return null;
        return switch (signal.toUpperCase()) {
            case "BUY" -> 0;
            case "SELL" -> 2;
            case "HOLD" -> 1;
            default -> null;
        };
    }

    private static String modelDisplayName(String version) {
        if (version == null) return "未知";
        String v = version.toLowerCase();
        if (v.startsWith("xgb")) return "XGBoost";
        if (v.startsWith("lgbm") || v.startsWith("lgb")) return "LightGBM";
        return version;
    }
}
