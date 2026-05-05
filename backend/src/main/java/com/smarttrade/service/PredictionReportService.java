package com.smarttrade.service;

import com.smarttrade.dto.PredictionDTO;
import com.smarttrade.entity.User;
import com.smarttrade.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 基于 LightGBM 预测结果生成 AI 分析报告。
 *
 * 工作流：
 *   PredictionDTO -> 格式化为专业 prompt -> 复用 AiChatService 流式调 Qwen
 *   -> 通过 SseEmitter 把生成内容流回前端
 */
@Slf4j
@Service
public class PredictionReportService {

    @Autowired
    private PredictionClient predictionClient;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    /** SSE 流式报告专用线程池，由 AsyncConfig 提供，避免每次请求新建 Executor 泄漏。 */
    @Autowired
    @Qualifier("sseStreamExecutor")
    private Executor reportExecutor;

    /**
     * 启动一次报告生成（异步）。
     * 调用方应保证 emitter 已配置好 onCompletion / onError。
     */
    public void streamReport(String stockCode, SseEmitter emitter) {
        streamReport(stockCode, "lgbm", emitter);
    }

    public void streamReport(String stockCode, String modelKey, SseEmitter emitter) {
        // 启动时立即异步落一条审计日志（不阻塞 SSE）
        recordAudit(stockCode);

        final String model = (modelKey == null || modelKey.isBlank()) ? "lgbm" : modelKey.toLowerCase();

        // 提交到共享线程池，避免阻塞 Spring MVC 返回 emitter 的线程
        reportExecutor.execute(() -> doStreamReport(stockCode, model, emitter));
    }

    private void doStreamReport(String stockCode, String modelKey, SseEmitter emitter) {
        PredictionDTO dto;
        try {
            dto = predictionClient.predict(stockCode, modelKey);
        } catch (Exception e) {
            sendError(emitter, "无法获取预测结果：" + e.getMessage());
            return;
        }

        String prompt = buildPrompt(dto);
        log.info("[Report] 为 {} 生成分析报告，prompt {} 字", stockCode, prompt.length());

        // 复用 AiChatService 的流式逻辑：把 prompt 包装成单条 user 消息
        Map<String, String> userMsg = Map.of("role", "user", "content", prompt);
        aiChatService.streamChat(List.of(userMsg), emitter);
    }

    /**
     * 并行拉 LGBM + XGB 结果，生成「分歧解读」报告。
     */
    public void streamCompareReport(String stockCode, SseEmitter emitter) {
        recordCompareAudit(stockCode);
        reportExecutor.execute(() -> doStreamCompareReport(stockCode, emitter));
    }

    private void doStreamCompareReport(String stockCode, SseEmitter emitter) {
        PredictionDTO lgbm, xgb;
        try {
            lgbm = predictionClient.predict(stockCode, "lgbm");
        } catch (Exception e) {
            sendError(emitter, "LightGBM 调用失败：" + e.getMessage());
            return;
        }
        try {
            xgb = predictionClient.predict(stockCode, "xgb");
        } catch (Exception e) {
            sendError(emitter, "XGBoost 调用失败：" + e.getMessage());
            return;
        }

        String prompt = buildComparePrompt(lgbm, xgb);
        log.info("[Compare] 为 {} 生成分歧解读，prompt {} 字", stockCode, prompt.length());

        Map<String, String> userMsg = Map.of("role", "user", "content", prompt);
        aiChatService.streamChat(List.of(userMsg), emitter);
    }

    private void recordCompareAudit(String stockCode) {
        try {
            Long userId = UserContext.getUserId();
            String username = null;
            String role = "GUEST";
            if (userId != null) {
                try {
                    User u = userService.getById(userId);
                    if (u != null) { username = u.getUsername(); role = u.getRole(); }
                } catch (Exception ignore) {}
            }
            Map<String, Object> details = new HashMap<>();
            details.put("code", stockCode);
            auditLogService.record(
                    "PREDICTION", "COMPARE_REPORT",
                    userId, username, role,
                    "STOCK", stockCode,
                    "AI 分歧解读 " + stockCode,
                    details, true, null);
        } catch (Exception e) {
            log.warn("[Compare] 审计日志写入失败: {}", e.getMessage());
        }
    }

    /** 异步记审计，不阻塞主流程。失败仅打印日志。 */
    private void recordAudit(String stockCode) {
        try {
            Long userId = UserContext.getUserId();
            String username = null;
            String role = "GUEST";
            if (userId != null) {
                try {
                    User u = userService.getById(userId);
                    if (u != null) {
                        username = u.getUsername();
                        role = u.getRole();
                    }
                } catch (Exception ignore) {}
            }
            Map<String, Object> details = new HashMap<>();
            details.put("code", stockCode);
            auditLogService.record(
                    "PREDICTION", "LGBM_REPORT",
                    userId, username, role,
                    "STOCK", stockCode,
                    "AI 分析报告 " + stockCode,
                    details, true, null);
        } catch (Exception e) {
            log.warn("[Report] 审计日志写入失败: {}", e.getMessage());
        }
    }

    /**
     * 把预测结果格式化成给大模型的专业 prompt。
     */
    private String buildPrompt(PredictionDTO dto) {
        StringBuilder sb = new StringBuilder();
        String modelDisplay = modelDisplayName(dto.getModelVersion());
        sb.append("你是一名资深的 A 股量化分析师。下面是机器学习模型（")
          .append(modelDisplay).append(" 三分类）")
          .append("对某只股票未来 ").append(dto.getForwardDays())
          .append(" 个交易日涨跌幅区间的预测结果，请你结合这些数据撰写一份**专业、结构化**的中文分析报告。\n\n");

        sb.append("# 输入数据\n\n");
        sb.append("- 股票代码：`").append(dto.getCode()).append("`\n");
        sb.append("- 基准日期：").append(dto.getAsOfDate()).append("（最新一根 K 线）\n");
        sb.append("- 模型版本：").append(dto.getModelVersion()).append("\n");
        sb.append("- 预测周期：T+").append(dto.getForwardDays()).append(" 个交易日\n");
        sb.append("- 分类阈值：±")
          .append(String.format(Locale.US, "%.0f", dto.getThreshold() * 100))
          .append("%（涨幅超过则看多，跌幅超过则看空，否则震荡）\n\n");

        sb.append("## 三类概率\n");
        Map<String, Double> p = dto.getProba();
        sb.append("| 类别 | 含义 | 概率 |\n|---|---|---|\n");
        sb.append("| 看多 | 涨幅 > +").append(pct0(dto.getThreshold())).append("% | ")
          .append(pct1(p.getOrDefault("bullish", 0.0))).append("% |\n");
        sb.append("| 震荡 | 在 ±").append(pct0(dto.getThreshold())).append("% 内 | ")
          .append(pct1(p.getOrDefault("neutral", 0.0))).append("% |\n");
        sb.append("| 看空 | 跌幅 > -").append(pct0(dto.getThreshold())).append("% | ")
          .append(pct1(p.getOrDefault("bearish", 0.0))).append("% |\n\n");

        sb.append("## 模型预测结论\n");
        sb.append("**").append(dto.getLabelName())
          .append("**，置信度 ").append(pct1(dto.getConfidence())).append("%（随机基线 33.3%）\n\n");

        sb.append("## Top 关键特征当前值\n\n");
        sb.append("| 特征 | 含义 | 当前值 | 重要性 |\n|---|---|---|---|\n");
        if (dto.getTopFeatures() != null) {
            for (PredictionDTO.FeatureContrib f : dto.getTopFeatures()) {
                sb.append("| `").append(f.getName()).append("` | ")
                  .append(featureMeaning(f.getName())).append(" | ")
                  .append(formatFeatureValue(f.getName(), f.getValue())).append(" | ")
                  .append(f.getImportance()).append(" |\n");
            }
        }
        sb.append("\n");

        sb.append("# 报告要求\n\n");
        sb.append("请按以下 7 个小节结构输出报告，使用 Markdown 格式（标题用 `##`），全程中文。")
          .append("**重点是对模型输出做专业解读，不是简单复述数字**。\n\n");

        sb.append("## 1. 模型预测结果解读 ⭐（这一节最重要，必须详细）\n");
        sb.append("请从以下 4 个角度分别解读：\n\n");
        sb.append("**① 概率分布解读**\n");
        sb.append("- 三类概率的相对强弱说明了什么？是单边明确还是分歧较大？\n");
        sb.append("- 看多与看空的差值是否显著？震荡概率高不高？\n\n");
        sb.append("**② 置信度判断**\n");
        sb.append("- 当前置信度（")
          .append(pct1(dto.getConfidence())).append("%）属于高 / 中 / 低？\n");
        sb.append("- 距离随机基线（33.3%）多远？是否值得据此操作？\n\n");
        sb.append("**③ 特征驱动因素**（关键）\n");
        sb.append("- 总体说明哪 2-3 个特征对当前 ")
          .append(dto.getLabelName()).append(" 结论贡献最大，后续第 2 节会逐个展开。\n");
        sb.append("- 是否存在**互相矛盾**的信号？（如动量看空但波动率收敛暗示底部）\n\n");
        sb.append("**④ 模型局限提示**\n");
        sb.append("- 该置信度水平下模型的历史准确率大约多少？\n");
        sb.append("- 哪些场景模型可能失效？（如政策黑天鹅、财报突变）\n\n");

        sb.append("## 2. 关键特征逐个解读 ⭐（必须详细）\n");
        sb.append("请**逐个**解释上方 Top 特征表中的**每一个**特征，使用二级标题 `### 特征名` 逐个展开，每个特征说明以下 4 点：\n\n");
        sb.append("1. **含义**：这个指标是什么，怎么计算的（一句话）\n");
        sb.append("2. **当前读数**：结合表中的值说明现在处于什么状态（如超卖/超买/中性、市场偏高/偏低等）\n");
        sb.append("3. **传递什么信号**：这个数值在交易语境下意味着什么（如上涨动量衰竭、估值沫中周期高位等）\n");
        sb.append("4. **对本次结论的贡献**：该特征是支持还是反对模型给出的「")
          .append(dto.getLabelName()).append("」判断？贡献强还是弱？\n\n");
        sb.append("要求：语言接地气，避免只复述公式；目标是**让不了解该指标的用户也能看懂**。\n\n");

        sb.append("## 3. 综合判断\n");
        sb.append("基于上方解读，用 2-3 句话给出你**作为分析师**对此模型结论的态度：")
          .append("赞同 / 中立 / 谨慎，并简述理由。\n\n");

        sb.append("## 4. 技术面解读\n");
        sb.append("结合波动率、动量（ret_60d）、均线偏离、MACD/KDJ 等特征，")
          .append("分析当前所处的技术形态（趋势 / 整理 / 反转）。\n\n");

        sb.append("## 5. 估值分析\n");
        sb.append("基于 PE-TTM / PB / PS 评估当前估值水平偏高、合理还是低估。")
          .append("如果 Top 特征里没有这些字段，本节可以一句话带过或跳过。\n\n");

        sb.append("## 6. 主要风险\n");
        sb.append("列出 3-4 个潜在风险点，每点一句话。需结合模型输出（如低置信度本身就是风险）。\n\n");

        sb.append("## 7. 操作建议\n");
        sb.append("给出具体建议（仓位 / 止损位 / 关注信号），强度需与置信度匹配：")
          .append("高置信可激进、低置信仅观察。**结尾必须明确写「以上仅供参考，不构成投资建议」**。\n\n");

        sb.append("---\n");
        sb.append("**写作要求**：\n");
        sb.append("- 每节简洁有力，不要堆砌空话\n");
        sb.append("- 必须基于上方表格的具体数据做判断，**不要泛泛而谈**\n");
        sb.append("- 第 1 、第 2 节是核心，加起来至少占整篇报告的 60% 篇幅\n");
        sb.append("- 数字可以引用，但要给出**解读**，不要只复述\n");

        return sb.toString();
    }

    /**
     * 并行 LGBM + XGB 两份预测结果 → 分歧解读 prompt。
     */
    private String buildComparePrompt(PredictionDTO a, PredictionDTO b) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深的 A 股量化分析师。")
          .append("下面是两个不同的机器学习模型（LightGBM 与 XGBoost）对同一只股票、同一基准日、同一目标周期的 T+")
          .append(a.getForwardDays())
          .append(" 三分类预测结果。请你基于两者的差异，")
          .append("撰写一份**专业、结构化**的「模型分歧解读」中文报告。\n\n");

        sb.append("# 输入数据\n\n");
        sb.append("- 股票代码：`").append(a.getCode()).append("`\n");
        sb.append("- 基准日期：").append(a.getAsOfDate()).append("\n");
        sb.append("- 预测周期：T+").append(a.getForwardDays()).append(" 个交易日\n");
        sb.append("- 分类阈值：±").append(pct0(a.getThreshold())).append("%\n\n");

        // 概率并排表
        sb.append("## 两模型三类概率并排\n\n");
        Map<String, Double> pa = a.getProba();
        Map<String, Double> pb = b.getProba();
        sb.append("| 类别 | LightGBM | XGBoost | 差值 |\n|---|---|---|---|\n");
        appendProbaRow(sb, "看多", pa.getOrDefault("bullish", 0.0), pb.getOrDefault("bullish", 0.0));
        appendProbaRow(sb, "震荡", pa.getOrDefault("neutral", 0.0), pb.getOrDefault("neutral", 0.0));
        appendProbaRow(sb, "看空", pa.getOrDefault("bearish", 0.0), pb.getOrDefault("bearish", 0.0));
        sb.append("\n");

        // 结论
        sb.append("## 两模型结论\n\n");
        sb.append("- **LightGBM**：").append(a.getLabelName())
          .append("，置信度 ").append(pct1(a.getConfidence())).append("%\n");
        sb.append("- **XGBoost**：").append(b.getLabelName())
          .append("，置信度 ").append(pct1(b.getConfidence())).append("%\n");
        boolean sameLabel = a.getLabel() != null && a.getLabel().equals(b.getLabel());
        sb.append("- **标签一致性**：").append(sameLabel ? "✅ 方向一致" : "❌ 方向不一致").append("\n");
        double confGap = Math.abs(a.getConfidence() - b.getConfidence());
        sb.append("- **置信度差**：").append(pct1(confGap)).append("%\n\n");

        // Top 特征：共同 vs 独有
        java.util.Set<String> fa = new java.util.LinkedHashSet<>();
        java.util.Set<String> fb = new java.util.LinkedHashSet<>();
        if (a.getTopFeatures() != null) a.getTopFeatures().forEach(f -> fa.add(f.getName()));
        if (b.getTopFeatures() != null) b.getTopFeatures().forEach(f -> fb.add(f.getName()));
        java.util.Set<String> shared = new java.util.LinkedHashSet<>(fa); shared.retainAll(fb);
        java.util.Set<String> onlyA  = new java.util.LinkedHashSet<>(fa); onlyA.removeAll(fb);
        java.util.Set<String> onlyB  = new java.util.LinkedHashSet<>(fb); onlyB.removeAll(fa);

        sb.append("## Top 特征对比\n\n");
        sb.append("- **两模型共同看重**（").append(shared.size()).append(" 项）：");
        sb.append(shared.isEmpty() ? "无" : String.join("、", shared.stream().map(n -> "`" + n + "`").toList()));
        sb.append("\n- **仅 LightGBM 看重**：");
        sb.append(onlyA.isEmpty() ? "无" : String.join("、", onlyA.stream().map(n -> "`" + n + "`").toList()));
        sb.append("\n- **仅 XGBoost 看重**：");
        sb.append(onlyB.isEmpty() ? "无" : String.join("、", onlyB.stream().map(n -> "`" + n + "`").toList()));
        sb.append("\n\n");

        // 共同特征当前值（两边相同，用 a 的值即可）
        if (!shared.isEmpty() && a.getTopFeatures() != null) {
            sb.append("### 共同特征当前值\n\n");
            sb.append("| 特征 | 含义 | 当前值 |\n|---|---|---|\n");
            for (PredictionDTO.FeatureContrib f : a.getTopFeatures()) {
                if (shared.contains(f.getName())) {
                    sb.append("| `").append(f.getName()).append("` | ")
                      .append(featureMeaning(f.getName())).append(" | ")
                      .append(formatFeatureValue(f.getName(), f.getValue())).append(" |\n");
                }
            }
            sb.append("\n");
        }

        sb.append("# 报告要求\n\n");
        sb.append("请按以下 5 个小节输出报告，使用 Markdown（`##` 标题），全程中文，**不要复述输入表格数字**，重在**专业解读**。\n\n");

        sb.append("## 1. 分歧定性\n");
        sb.append("用一句话判断当前属于：**强共识 / 方向一致 / 温和分歧 / 显著分歧**，并简述依据（结合标签一致性、置信度差、概率分布相似度）。\n\n");

        sb.append("## 2. 分歧原因分析 ⭐（重点，至少 200 字）\n");
        sb.append("- 两个模型的原理差异：LightGBM 基于直方图 + leaf-wise，XGBoost 基于预排序 + level-wise，对特征的敏感度不同\n");
        sb.append("- 结合**共同特征**与**独有特征**，解释为什么两模型可能看法不一：\n");
        sb.append("    - 共同特征读数暗示了什么？\n");
        sb.append("    - 各自独有的特征说明它们关注了什么对方没关注的信息？\n");
        sb.append("- 当前市场环境（震荡、趋势、高波动）下，哪个模型更容易误判？\n\n");

        sb.append("## 3. 该听谁的？\n");
        sb.append("明确给出一个倾向性判断（**倾向 LightGBM / 倾向 XGBoost / 建议观望**），并说明理由：\n");
        sb.append("- 置信度高的不一定更对，结合特征分布合理性判断\n");
        sb.append("- 如果两者分歧太大，直接建议观望，不要强行跟单\n\n");

        sb.append("## 4. 操作建议\n");
        sb.append("针对「").append(sameLabel ? "方向一致但强度不同" : "方向冲突")
          .append("」的场景，给出具体仓位建议（重仓 / 轻仓 / 观望 / 分批）、止损位参考、需要关注的后续信号。\n\n");

        sb.append("## 5. 风险提示\n");
        sb.append("2-3 句话，结尾必须包含「以上仅供参考，不构成投资建议」。\n");

        return sb.toString();
    }

    private static void appendProbaRow(StringBuilder sb, String name, double a, double b) {
        sb.append("| ").append(name).append(" | ")
          .append(pct1(a)).append("% | ")
          .append(pct1(b)).append("% | ")
          .append(String.format(Locale.US, "%+.1f", (a - b) * 100)).append("% |\n");
    }

    // ------------ 工具函数 ------------

    /** 将模型版本字符串映射为可读名称，如 lgbm_v1 -> LightGBM。 */
    private static String modelDisplayName(String version) {
        if (version == null) return "LightGBM";
        String v = version.toLowerCase();
        if (v.startsWith("xgb")) return "XGBoost";
        if (v.startsWith("lgbm") || v.startsWith("lgb")) return "LightGBM";
        if (v.startsWith("lstm")) return "LSTM";
        return version;
    }

    private static String pct0(double v) {
        return String.format(Locale.US, "%.0f", v * 100);
    }

    private static String pct1(double v) {
        return String.format(Locale.US, "%.1f", v * 100);
    }

    /** 给大模型看的中文释义。 */
    private static String featureMeaning(String name) {
        return switch (name) {
            case "ret_1d" -> "近 1 日涨跌幅";
            case "ret_5d" -> "近 5 日累计涨跌幅";
            case "ret_10d" -> "近 10 日累计涨跌幅";
            case "ret_20d" -> "近 20 日累计涨跌幅";
            case "ret_60d" -> "近 60 日累计涨跌幅（中期动量）";
            case "close_ma5" -> "收盘价相对 5 日均线偏离";
            case "close_ma10" -> "收盘价相对 10 日均线偏离";
            case "close_ma20" -> "收盘价相对 20 日均线偏离";
            case "close_ma60" -> "收盘价相对 60 日均线偏离";
            case "ma5_ma20" -> "5 日均线相对 20 日均线发散度";
            case "ma10_ma60" -> "10 日均线相对 60 日均线发散度";
            case "vol_5d" -> "近 5 日收益率波动率";
            case "vol_20d" -> "近 20 日收益率波动率";
            case "vol_60d" -> "近 60 日收益率波动率（长期波动）";
            case "atr_pct" -> "14 日真实波幅 / 收盘价";
            case "rsi_6" -> "RSI(6) 强弱指标";
            case "rsi_14" -> "RSI(14) 强弱指标";
            case "macd" -> "MACD 主线";
            case "macd_signal" -> "MACD 信号线";
            case "macd_diff" -> "MACD 柱（差值）";
            case "kdj_k", "kdj_d", "kdj_j" -> "KDJ " + name.substring(4).toUpperCase() + " 值";
            case "bb_pos" -> "布林带相对位置（0=下轨，1=上轨）";
            case "bb_width" -> "布林带带宽 / 中轨";
            case "vol_ratio_5" -> "当日成交量 / 5 日均量（量比）";
            case "vol_ratio_20" -> "当日成交量 / 20 日均量";
            case "turnover_rate_ma5" -> "5 日平均换手率";
            case "up_streak" -> "连涨天数";
            case "dist_high_20" -> "收盘价相对 20 日最高价距离";
            case "dist_low_20" -> "收盘价相对 20 日最低价距离";
            case "pe_ttm" -> "PE-TTM 动态市盈率";
            case "pb_mrq" -> "PB 市净率";
            case "ps_ttm" -> "PS-TTM 市销率";
            case "log_amount" -> "成交额对数（log1p）";
            default -> "—";
        };
    }

    private static String formatFeatureValue(String name, Double value) {
        if (value == null) return "—";
        // 比率类（小数转百分比）
        switch (name) {
            case "ret_1d", "ret_5d", "ret_10d", "ret_20d", "ret_60d",
                 "close_ma5", "close_ma10", "close_ma20", "close_ma60",
                 "ma5_ma20", "ma10_ma60",
                 "vol_5d", "vol_20d", "vol_60d", "atr_pct",
                 "dist_high_20", "dist_low_20", "bb_pos", "bb_width":
                return String.format(Locale.US, "%.2f%%", value * 100);
            case "turnover_rate_ma5":
                return String.format(Locale.US, "%.2f%%", value);
            case "vol_ratio_5", "vol_ratio_20":
                return String.format(Locale.US, "%.2fx", value);
            case "up_streak":
                return String.valueOf(Math.round(value));
            default:
                return String.format(Locale.US, "%.2f", value);
        }
    }

    private static void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(msg));
        } catch (IOException ignore) {}
        emitter.complete();
    }
}
