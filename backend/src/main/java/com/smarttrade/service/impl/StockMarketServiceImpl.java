package com.smarttrade.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.vo.KlinePointVO;
import com.smarttrade.vo.StockQuoteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富免费行情接口实现
 *
 * 实时行情:
 *   https://push2.eastmoney.com/api/qt/stock/get?secid={secid}
 * 日 K 线:
 *   https://push2his.eastmoney.com/api/qt/stock/kline/get?secid={secid}&klt=101&fqt=1&...
 *
 * secid 规则:
 *   沪市 -> 1.{code}
 *   深市/创业板/科创板(深圳侧) -> 0.{code}
 */
@Slf4j
@Service
public class StockMarketServiceImpl implements StockMarketService {

    private static final String QUOTE_URL =
            "https://push2.eastmoney.com/api/qt/stock/get?secid=%s&fields=f43,f44,f45,f46,f47,f48,f57,f58,f60,f168,f169,f170,f171";

    private static final String KLINE_URL =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%s&klt=101&fqt=1" +
                    "&end=20500101&lmt=%d" +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";

    /**
     * 沪深主流市场股票列表：
     *   m:1+t:2  沪市A股
     *   m:1+t:23 科创板
     *   m:0+t:6  深市A股
     *   m:0+t:80 创业板
     * 按成交额(f6)倒序，取活跃个股。
     *   f12 代码, f13 市场(0=深 1=沪), f14 名称, f100 行业
     */
    private static final String LIST_URL =
            "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=%d&po=1&np=1" +
                    "&fltt=2&invt=2&fid=f6" +
                    "&fs=m:1+t:2,m:1+t:23,m:0+t:6,m:0+t:80" +
                    "&fields=f12,f13,f14,f100";

    /**
     * 批量行情查询：
     *   f12 代码, f13 市场, f14 名称
     *   f2 最新价, f3 涨跌幅, f4 涨跌额
     *   f5 成交量(手), f6 成交额(元)
     *   f8 换手率, f15 最高, f16 最低, f17 今开, f18 昨收
     */
    private static final String ULIST_URL =
            "https://push2.eastmoney.com/api/qt/ulist.np/get?secids=%s" +
                    "&fields=f2,f3,f4,f5,f6,f8,f12,f13,f14,f15,f16,f17,f18";

    /**
     * 新浪财经 K 线接口（K 线兜底）。
     * 返回示例: var a=[{day:"2024-01-02",open:"...",high:"...",low:"...",close:"...",volume:"..."},...];
     * symbol 形如 sh600519 / sz000001（小写市场前缀）
     */
    private static final String SINA_KLINE_URL =
            "https://quotes.sina.cn/cn/api/jsonp_v2.php/var=/CN_MarketDataService.getKLineData" +
                    "?symbol=%s&scale=240&ma=no&datalen=%d";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 行情数据 Redis 缓存配置
     *   - 实时行情 30s：A 股最小 tick 不会比这更新得快，30s 命中率高且数据新鲜度可接受
     *   - 日 K 5min：日 K 一天才更新，盘中 5 分钟刷新一次足够
     */
    private static final Duration QUOTE_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration KLINE_CACHE_TTL = Duration.ofMinutes(5);

    private static final String KEY_QUOTE_SINGLE = "stock:quote:";   // + code
    private static final String KEY_QUOTE_BATCH  = "stock:batch:";   // + md5(sortedCodes)
    private static final String KEY_KLINE        = "stock:kline:";   // + code:limit

    /**
     * 东财批量行情接口的简易熔断：失败时设置为"下次允许重试时刻"。
     * 在此之前，batchFetchQuoteForStocks 会跳过东财直接走新浪兜底，
     * 避免每个请求都浪费 4 次重试（用户感受慢 1~2 秒）。
     */
    private static final long EASTMONEY_COOLDOWN_MS = 60_000L;
    private volatile long eastmoneyResumeAt = 0L;

    @Override
    public StockQuoteVO fetchRealtimeQuote(String stockCode, String market) {
        // 0. 先查 Redis 缓存
        String cacheKey = KEY_QUOTE_SINGLE + stockCode;
        StockQuoteVO cached = cacheGet(cacheKey, StockQuoteVO.class);
        if (cached != null) return cached;

        // 1. 优先东方财富，偶发抖动重试 1 次
        StockQuoteVO vo = fetchRealtimeQuoteFromEastmoney(stockCode, market);
        if (vo != null && vo.getLatestPrice() != null
                && vo.getLatestPrice().compareTo(BigDecimal.ZERO) > 0) {
            cacheSet(cacheKey, vo, QUOTE_CACHE_TTL);
            return vo;
        }
        // 2. 兜底新浪财经
        StockQuoteVO sinaVo = fetchRealtimeQuoteFromSina(stockCode, market);
        if (sinaVo != null) {
            log.info("[实时行情兜底] 股票 {} 使用新浪行情: latestPrice={}", stockCode, sinaVo.getLatestPrice());
            cacheSet(cacheKey, sinaVo, QUOTE_CACHE_TTL);
            return sinaVo;
        }
        return null;
    }

    /**
     * 东方财富实时行情（主源），含 1 次重试
     */
    private StockQuoteVO fetchRealtimeQuoteFromEastmoney(String stockCode, String market) {
        String secid = buildSecid(stockCode, market);
        if (secid == null) {
            return null;
        }
        Exception lastEx = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String url = String.format(QUOTE_URL, secid);
                String body = restTemplate.getForObject(url, String.class);
                if (body == null || body.isEmpty()) {
                    return null;
                }
                JsonNode root = MAPPER.readTree(body);
                JsonNode data = root.path("data");
                if (data.isMissingNode() || data.isNull()) {
                    return null;
                }
                return parseQuote(stockCode, market, data);
            } catch (Exception e) {
                lastEx = e;
                if (attempt < 2) {
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.warn("东方财富实时行情失败 {}(已重试): {}", stockCode,
                lastEx == null ? "unknown" : lastEx.getMessage());
        return null;
    }

    /**
     * 新浪财经实时行情（兜底）
     *
     * URL: https://hq.sinajs.cn/list=sh600519
     * 返回示例: var hq_str_sh600519="贵州茅台,1685.000,1690.000,1685.500,1700.000,1660.500,...";
     * 字段: 0=名称, 1=今开, 2=昨收, 3=当前价, 4=最高, 5=最低, 6=买一, 7=卖一,
     *       8=成交量(股), 9=成交额(元), ..., 30=日期, 31=时间
     *
     * 注意：必须带 Referer=https://finance.sina.com.cn/，否则返回空
     */
    private static final Pattern SINA_QUOTE_PATTERN =
            Pattern.compile("hq_str_[a-z]+\\d+=\"([^\"]+)\"");

    private StockQuoteVO fetchRealtimeQuoteFromSina(String stockCode, String market) {
        String prefix = sinaPrefix(stockCode, market);
        if (prefix == null) return null;
        try {
            String url = "https://hq.sinajs.cn/list=" + prefix + stockCode;
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.REFERER, "https://finance.sina.com.cn/");
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = resp.getBody();
            if (body == null || body.isEmpty()) return null;
            Matcher m = SINA_QUOTE_PATTERN.matcher(body);
            if (!m.find()) return null;
            String[] fields = m.group(1).split(",");
            if (fields.length < 6) return null;

            BigDecimal openPrice = toBigDecimal(fields[1]);
            BigDecimal preClose = toBigDecimal(fields[2]);
            BigDecimal latest = toBigDecimal(fields[3]);
            BigDecimal high = toBigDecimal(fields[4]);
            BigDecimal low = toBigDecimal(fields[5]);

            if (latest == null || latest.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            StockQuoteVO vo = new StockQuoteVO();
            vo.setStockCode(stockCode);
            vo.setMarket(market);
            vo.setLatestPrice(latest);
            vo.setOpenPrice(openPrice);
            vo.setPreClosePrice(preClose);
            vo.setHighPrice(high);
            vo.setLowPrice(low);
            // 涨跌额 / 涨跌幅
            if (preClose != null && preClose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal changeAmount = latest.subtract(preClose).setScale(3, RoundingMode.HALF_UP);
                BigDecimal changePct = changeAmount.multiply(BigDecimal.valueOf(100))
                        .divide(preClose, 4, RoundingMode.HALF_UP);
                vo.setChangeAmount(changeAmount);
                vo.setChangePercent(changePct);
            }
            // 成交量、成交额
            if (fields.length > 9) {
                try { vo.setVolume(Long.parseLong(fields[8].trim())); } catch (Exception ignored) {}
                vo.setTurnoverAmount(toBigDecimal(fields[9]));
            }
            return vo;
        } catch (Exception e) {
            log.warn("新浪实时行情失败 {}: {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 根据股票代码 + market 字段推断新浪 prefix
     */
    private String sinaPrefix(String stockCode, String market) {
        if (market != null) {
            String mk = market.trim().toUpperCase();
            if ("SH".equals(mk)) return "sh";
            if ("SZ".equals(mk)) return "sz";
            if ("BJ".equals(mk)) return "bj";
        }
        if (stockCode == null || stockCode.isEmpty()) return null;
        char c = stockCode.charAt(0);
        if (c == '6' || c == '5' || c == '9') return "sh";
        if (c == '0' || c == '3' || c == '2') return "sz";
        if (c == '4' || c == '8') return "bj";
        return null;
    }

    @Override
    public List<StockQuoteVO> batchFetchRealtimeQuote(List<String> stockCodes, List<String> markets) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<StockQuoteVO> result = new ArrayList<>(stockCodes.size());
        for (int i = 0; i < stockCodes.size(); i++) {
            String code = stockCodes.get(i);
            String market = markets != null && i < markets.size() ? markets.get(i) : null;
            result.add(fetchRealtimeQuote(code, market));
        }
        return result;
    }

    @Override
    public List<KlinePointVO> fetchDailyKline(String stockCode, String market, int limit) {
        String secid = buildSecid(stockCode, market);
        if (secid == null) {
            return Collections.emptyList();
        }
        if (limit <= 0) {
            limit = 120;
        }
        // 0. 先查 Redis 缓存
        String klineCacheKey = KEY_KLINE + stockCode + ":" + limit;
        List<KlinePointVO> cachedKline = cacheGetList(klineCacheKey, KlinePointVO.class);
        if (cachedKline != null) return cachedKline;
        // 主源：东方财富
        try {
            String url = String.format(KLINE_URL, secid, limit);
            String body = restTemplate.getForObject(url, String.class);
            if (body != null && !body.isEmpty()) {
                JsonNode klines = MAPPER.readTree(body).path("data").path("klines");
                if (klines.isArray() && !klines.isEmpty()) {
                    List<KlinePointVO> list = new ArrayList<>(klines.size());
                    for (JsonNode node : klines) {
                        String[] arr = node.asText().split(",");
                        if (arr.length < 11) continue;
                        KlinePointVO vo = new KlinePointVO();
                        vo.setTradeDate(arr[0]);
                        vo.setOpenPrice(toBigDecimal(arr[1]));
                        vo.setClosePrice(toBigDecimal(arr[2]));
                        vo.setHighPrice(toBigDecimal(arr[3]));
                        vo.setLowPrice(toBigDecimal(arr[4]));
                        vo.setVolume(toLong(arr[5]));
                        vo.setTurnoverAmount(toBigDecimal(arr[6]));
                        vo.setAmplitude(toBigDecimal(arr[7]));
                        vo.setChangePercent(toBigDecimal(arr[8]));
                        vo.setChangeAmount(toBigDecimal(arr[9]));
                        vo.setTurnoverRate(toBigDecimal(arr[10]));
                        list.add(vo);
                    }
                    cacheSetList(klineCacheKey, list, KLINE_CACHE_TTL);
                    return list;
                }
            }
        } catch (Exception e) {
            log.warn("东方财富 K 线失败({}): {}，降级到新浪", stockCode, e.getMessage());
        }
        // 兜底：新浪财经
        List<KlinePointVO> sinaList = fetchDailyKlineFromSina(stockCode, market, limit);
        if (sinaList != null && !sinaList.isEmpty()) {
            cacheSetList(klineCacheKey, sinaList, KLINE_CACHE_TTL);
        }
        return sinaList;
    }

    /**
     * 新浪财经 K 线兜底
     */
    private List<KlinePointVO> fetchDailyKlineFromSina(String stockCode, String market, int limit) {
        String mk = market == null ? "" : market.trim().toUpperCase();
        String prefix;
        if ("SH".equals(mk)) {
            prefix = "sh";
        } else if ("SZ".equals(mk)) {
            prefix = "sz";
        } else {
            char c = stockCode.charAt(0);
            if (c == '6' || c == '5' || c == '9') prefix = "sh";
            else if (c == '0' || c == '3' || c == '2') prefix = "sz";
            else return Collections.emptyList();
        }
        try {
            String url = String.format(SINA_KLINE_URL, prefix + stockCode, limit);
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return Collections.emptyList();
            }
            int start = body.indexOf('[');
            int end = body.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return Collections.emptyList();
            }
            JsonNode arr = MAPPER.readTree(body.substring(start, end + 1));
            if (!arr.isArray()) {
                return Collections.emptyList();
            }
            List<KlinePointVO> list = new ArrayList<>(arr.size());
            for (JsonNode node : arr) {
                KlinePointVO vo = new KlinePointVO();
                vo.setTradeDate(text(node, "day"));
                vo.setOpenPrice(toBigDecimal(text(node, "open")));
                vo.setHighPrice(toBigDecimal(text(node, "high")));
                vo.setLowPrice(toBigDecimal(text(node, "low")));
                vo.setClosePrice(toBigDecimal(text(node, "close")));
                vo.setVolume(toLong(text(node, "volume")));
                list.add(vo);
            }
            return list;
        } catch (Exception e) {
            log.warn("新浪 K 线兜底失败 {}: {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<StockQuoteVO> batchFetchQuoteForStocks(List<StockInfo> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return Collections.emptyList();
        }
        // 0. 先查批量缓存：key = md5(sortedCodes)，TTL 30s
        String cacheKey = KEY_QUOTE_BATCH + md5OfStockCodes(stocks);
        List<StockQuoteVO> cached = cacheGetList(cacheKey, StockQuoteVO.class);
        if (cached != null) return cached;

        StringBuilder sb = new StringBuilder();
        for (StockInfo info : stocks) {
            String secid = buildSecid(info.getStockCode(), info.getMarket());
            if (secid == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(secid);
        }
        if (sb.length() == 0) {
            return Collections.emptyList();
        }
        // 熔断：东财近期失败过 → 在冷却时间内直接走新浪，避免每次都浪费 4 次重试
        long now = System.currentTimeMillis();
        if (now < eastmoneyResumeAt) {
            log.debug("东财处于熔断冷却期（剩余 {}ms），直接走新浪行情", eastmoneyResumeAt - now);
            List<StockQuoteVO> sinaResult = fetchBatchRealtimeQuotesFromSina(stocks);
            if (sinaResult != null && !sinaResult.isEmpty()) {
                cacheSetList(cacheKey, sinaResult, QUOTE_CACHE_TTL);
                return sinaResult;
            }
            return buildFallbackList(stocks);
        }

        // 重试 2 次：东财 push2 偶发 keep-alive 抖动，瞬态网络错误下重试一次基本就能成功
        String url = String.format(ULIST_URL, sb.toString());
        String body = null;
        Exception lastErr = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                body = restTemplate.getForObject(url, String.class);
                if (body != null && !body.isEmpty()) {
                    lastErr = null;
                    break;
                }
            } catch (Exception e) {
                lastErr = e;
                log.warn("批量行情第 {} 次拉取失败: {}", attempt, e.getMessage());
                if (attempt < 2) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }
        if (body == null || body.isEmpty()) {
            // 触发熔断：60 秒内不再尝试东财，直接走新浪
            eastmoneyResumeAt = System.currentTimeMillis() + EASTMONEY_COOLDOWN_MS;
            if (lastErr != null) {
                log.warn("东财批量行情失败，进入 {}s 熔断冷却期，切换到新浪行情: {}",
                        EASTMONEY_COOLDOWN_MS / 1000, lastErr.getMessage());
            }
            List<StockQuoteVO> sinaResult = fetchBatchRealtimeQuotesFromSina(stocks);
            if (sinaResult != null && !sinaResult.isEmpty()) {
                cacheSetList(cacheKey, sinaResult, QUOTE_CACHE_TTL);
                return sinaResult;
            }
            log.warn("新浪行情也失败，已降级输出基础信息");
            return buildFallbackList(stocks);
        }
        // 东财成功：清除熔断标记
        if (eastmoneyResumeAt != 0L) {
            eastmoneyResumeAt = 0L;
            log.info("东财批量行情恢复正常，解除熔断");
        }
        try {
            JsonNode diff = MAPPER.readTree(body).path("data").path("diff");
            if (!diff.isArray()) {
                return buildFallbackList(stocks);
            }
            // 用 map 加速根据代码合并
            java.util.Map<String, StockQuoteVO> quoteMap = new java.util.HashMap<>(diff.size() * 2);
            for (JsonNode node : diff) {
                StockQuoteVO vo = new StockQuoteVO();
                vo.setStockCode(text(node, "f12"));
                vo.setStockName(text(node, "f14"));
                int mk = node.path("f13").asInt(-1);
                vo.setMarket(mk == 1 ? "SH" : mk == 0 ? "SZ" : null);
                vo.setLatestPrice(divideBy100(node, "f2"));
                vo.setChangePercent(divideBy100(node, "f3"));
                vo.setChangeAmount(divideBy100(node, "f4"));
                vo.setHighPrice(divideBy100(node, "f15"));
                vo.setLowPrice(divideBy100(node, "f16"));
                vo.setOpenPrice(divideBy100(node, "f17"));
                vo.setPreClosePrice(divideBy100(node, "f18"));
                vo.setTurnoverRate(divideBy100(node, "f8"));
                if (node.path("f5").isNumber()) {
                    vo.setVolume(node.path("f5").asLong());
                }
                if (node.path("f6").isNumber()) {
                    vo.setTurnoverAmount(BigDecimal.valueOf(node.path("f6").asDouble()));
                }
                if (vo.getStockCode() != null) {
                    quoteMap.put(vo.getStockCode(), vo);
                }
            }
            // 按入参顺序输出，保持稳定
            List<StockQuoteVO> result = new ArrayList<>(stocks.size());
            for (StockInfo info : stocks) {
                StockQuoteVO vo = quoteMap.get(info.getStockCode());
                if (vo == null) {
                    vo = new StockQuoteVO();
                    vo.setStockCode(info.getStockCode());
                    vo.setStockName(info.getStockName());
                    vo.setMarket(info.getMarket());
                }
                result.add(vo);
            }
            // 写入批量缓存
            cacheSetList(cacheKey, result, QUOTE_CACHE_TTL);
            return result;
        } catch (Exception e) {
            log.warn("批量行情解析失败，已降级输出基础信息: {}", e.getMessage());
            return buildFallbackList(stocks);
        }
    }

    /**
     * 新浪批量行情兜底：当东财 push2 被风控/拒绝服务时启用。
     *   接口：https://hq.sinajs.cn/list=sh600519,sz000001,...
     *   返回多行 var hq_str_xxx="...";
     *   字段索引参见 fetchRealtimeQuoteFromSina。
     *
     * 注意：
     *   - 单次股票数过多会被截断，按 50 支一批分批调用
     *   - 必须带 Referer，否则返回空
     *   - 任一批次失败仍返回已成功批次的合并结果（部分降级总比全无）
     */
    private List<StockQuoteVO> fetchBatchRealtimeQuotesFromSina(List<StockInfo> stocks) {
        if (stocks == null || stocks.isEmpty()) return Collections.emptyList();
        final int batchSize = 50;
        java.util.Map<String, StockQuoteVO> quoteMap = new java.util.HashMap<>(stocks.size() * 2);

        for (int from = 0; from < stocks.size(); from += batchSize) {
            int to = Math.min(from + batchSize, stocks.size());
            List<StockInfo> batch = stocks.subList(from, to);

            // 构造 list= 参数：sh600519,sz000001,...
            StringBuilder listParam = new StringBuilder();
            // 同时记录每个 prefix+code 对应的原始 stockInfo
            java.util.Map<String, StockInfo> keyMap = new java.util.HashMap<>(batch.size() * 2);
            for (StockInfo info : batch) {
                String prefix = sinaPrefix(info.getStockCode(), info.getMarket());
                if (prefix == null) continue;
                String key = prefix + info.getStockCode();
                keyMap.put(key, info);
                if (listParam.length() > 0) listParam.append(',');
                listParam.append(key);
            }
            if (listParam.length() == 0) continue;

            try {
                String url = "https://hq.sinajs.cn/list=" + listParam;
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.REFERER, "https://finance.sina.com.cn/");
                ResponseEntity<String> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                String body = resp.getBody();
                if (body == null || body.isEmpty()) continue;

                // 逐行解析：var hq_str_sh600519="贵州茅台,..."
                for (String line : body.split("\n")) {
                    Matcher mLine = Pattern.compile("hq_str_([a-z]+\\d+)=\"([^\"]*)\"").matcher(line);
                    if (!mLine.find()) continue;
                    String key = mLine.group(1);
                    String content = mLine.group(2);
                    if (content == null || content.isEmpty()) continue;
                    StockInfo info = keyMap.get(key);
                    if (info == null) continue;
                    StockQuoteVO vo = parseSinaQuoteContent(content, info);
                    if (vo != null) quoteMap.put(info.getStockCode(), vo);
                }
            } catch (Exception e) {
                log.warn("新浪批量行情第 {}-{} 批拉取失败: {}", from, to, e.getMessage());
            }
        }

        // 按入参顺序输出，缺失的填基础信息
        List<StockQuoteVO> result = new ArrayList<>(stocks.size());
        for (StockInfo info : stocks) {
            StockQuoteVO vo = quoteMap.get(info.getStockCode());
            if (vo == null) {
                vo = new StockQuoteVO();
                vo.setStockCode(info.getStockCode());
                vo.setStockName(info.getStockName());
                vo.setMarket(info.getMarket());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 把新浪 hq_str 的 CSV 内容解析为 StockQuoteVO
     */
    private StockQuoteVO parseSinaQuoteContent(String content, StockInfo info) {
        String[] fields = content.split(",");
        if (fields.length < 6) return null;

        BigDecimal openPrice = toBigDecimal(fields[1]);
        BigDecimal preClose = toBigDecimal(fields[2]);
        BigDecimal latest = toBigDecimal(fields[3]);
        BigDecimal high = toBigDecimal(fields[4]);
        BigDecimal low = toBigDecimal(fields[5]);

        if (latest == null || latest.compareTo(BigDecimal.ZERO) <= 0) {
            // 价格为 0 通常说明停牌或未开盘，仍返回基础信息以便前端展示
            StockQuoteVO empty = new StockQuoteVO();
            empty.setStockCode(info.getStockCode());
            empty.setStockName(info.getStockName());
            empty.setMarket(info.getMarket());
            return empty;
        }

        StockQuoteVO vo = new StockQuoteVO();
        vo.setStockCode(info.getStockCode());
        vo.setStockName(fields[0] != null && !fields[0].isEmpty() ? fields[0] : info.getStockName());
        vo.setMarket(info.getMarket());
        vo.setLatestPrice(latest);
        vo.setOpenPrice(openPrice);
        vo.setPreClosePrice(preClose);
        vo.setHighPrice(high);
        vo.setLowPrice(low);

        if (preClose != null && preClose.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal changeAmount = latest.subtract(preClose).setScale(3, RoundingMode.HALF_UP);
            BigDecimal changePct = changeAmount.multiply(BigDecimal.valueOf(100))
                    .divide(preClose, 4, RoundingMode.HALF_UP);
            vo.setChangeAmount(changeAmount);
            vo.setChangePercent(changePct);
        }
        if (fields.length > 9) {
            try { vo.setVolume(Long.parseLong(fields[8].trim())); } catch (Exception ignored) {}
            vo.setTurnoverAmount(toBigDecimal(fields[9]));
        }
        return vo;
    }

    /**
     * 行情接口异常时的降级输出：仅保留 stock_info 中的代码/名字/市场，价格相关字段为 null。
     * 这样前端表格不会突然变空，对用户更友好。
     */
    private List<StockQuoteVO> buildFallbackList(List<StockInfo> stocks) {
        List<StockQuoteVO> result = new ArrayList<>(stocks.size());
        for (StockInfo info : stocks) {
            StockQuoteVO vo = new StockQuoteVO();
            vo.setStockCode(info.getStockCode());
            vo.setStockName(info.getStockName());
            vo.setMarket(info.getMarket());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<StockInfo> fetchStockList(int pageSize) {
        if (pageSize <= 0) {
            pageSize = 100;
        }
        try {
            String url = String.format(LIST_URL, pageSize);
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return Collections.emptyList();
            }
            JsonNode diff = MAPPER.readTree(body).path("data").path("diff");
            if (!diff.isArray() || diff.isEmpty()) {
                return Collections.emptyList();
            }
            List<StockInfo> list = new ArrayList<>(diff.size());
            for (JsonNode node : diff) {
                String code = text(node, "f12");
                if (code == null || code.isEmpty()) {
                    continue;
                }
                StockInfo info = new StockInfo();
                info.setStockCode(code);
                info.setStockName(text(node, "f14"));
                int marketFlag = node.path("f13").asInt(-1);
                info.setMarket(marketFlag == 1 ? "SH" : marketFlag == 0 ? "SZ" : null);
                info.setIndustryName(text(node, "f100"));
                info.setPlateType(detectPlate(code));
                info.setStatus(1);
                list.add(info);
            }
            return list;
        } catch (Exception e) {
            log.warn("拉取股票列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String detectPlate(String code) {
        if (code == null || code.isEmpty()) return null;
        char c = code.charAt(0);
        if (c == '6') {
            return code.startsWith("688") ? "科创板" : "主板";
        }
        if (c == '0') return "主板";
        if (c == '3') return "创业板";
        return "其他";
    }

    /**
     * 根据股票代码和市场标识构造东方财富 secid
     */
    private String buildSecid(String stockCode, String market) {
        if (stockCode == null || stockCode.isEmpty()) {
            return null;
        }
        String code = stockCode.trim();
        String mk = market == null ? "" : market.trim().toUpperCase();
        if ("SH".equals(mk)) {
            return "1." + code;
        }
        if ("SZ".equals(mk)) {
            return "0." + code;
        }
        // 根据数字代码自动判断 A 股市场
        char first = code.charAt(0);
        if (first == '6' || first == '5' || first == '9') {
            return "1." + code;
        }
        if (first == '0' || first == '3' || first == '2') {
            return "0." + code;
        }
        return null;
    }

    private StockQuoteVO parseQuote(String code, String market, JsonNode data) {
        StockQuoteVO vo = new StockQuoteVO();
        vo.setStockCode(code);
        vo.setMarket(market);
        vo.setStockName(text(data, "f58"));

        vo.setLatestPrice(divideBy100(data, "f43"));
        vo.setHighPrice(divideBy100(data, "f44"));
        vo.setLowPrice(divideBy100(data, "f45"));
        vo.setOpenPrice(divideBy100(data, "f46"));
        vo.setPreClosePrice(divideBy100(data, "f60"));
        vo.setChangeAmount(divideBy100(data, "f169"));
        vo.setChangePercent(divideBy100(data, "f170"));
        vo.setAmplitude(divideBy100(data, "f171"));
        vo.setTurnoverRate(divideBy100(data, "f168"));

        long vol = data.path("f47").asLong(0);
        vo.setVolume(vol);
        BigDecimal amount = data.path("f48").isNumber()
                ? BigDecimal.valueOf(data.path("f48").asDouble(0))
                : null;
        vo.setTurnoverAmount(amount);
        return vo;
    }

    private static BigDecimal divideBy100(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(n.asDouble(0)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static BigDecimal toBigDecimal(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    // ============== Redis 缓存 helper ==============

    private <T> T cacheGet(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.debug("读取缓存失败 {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void cacheSet(String key, Object value, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.debug("写入缓存失败 {}: {}", key, e.getMessage());
        }
    }

    private <T> List<T> cacheGetList(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return MAPPER.readValue(
                    json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.debug("读取列表缓存失败 {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void cacheSetList(String key, List<?> value, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.debug("写入列表缓存失败 {}: {}", key, e.getMessage());
        }
    }

    /**
     * 把入参股票按 code 排序后做 md5，作为批量缓存 key 后缀。
     * 避免不同顺序的同一股票集合各占一份缓存。
     */
    private String md5OfStockCodes(List<StockInfo> stocks) {
        List<String> codes = new ArrayList<>(stocks.size());
        for (StockInfo s : stocks) {
            if (s != null && s.getStockCode() != null) codes.add(s.getStockCode());
        }
        Collections.sort(codes);
        String joined = String.join(",", codes);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(joined.hashCode());
        }
    }

    private static Long toLong(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
