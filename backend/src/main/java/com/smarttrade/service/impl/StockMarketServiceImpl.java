package com.smarttrade.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.vo.KlinePointVO;
import com.smarttrade.vo.StockQuoteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public StockQuoteVO fetchRealtimeQuote(String stockCode, String market) {
        String secid = buildSecid(stockCode, market);
        if (secid == null) {
            return null;
        }
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
            log.warn("拉取股票 {} 实时行情失败: {}", stockCode, e.getMessage());
            return null;
        }
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
                    return list;
                }
            }
        } catch (Exception e) {
            log.warn("东方财富 K 线失败({}): {}，降级到新浪", stockCode, e.getMessage());
        }
        // 兜底：新浪财经
        return fetchDailyKlineFromSina(stockCode, market, limit);
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
        try {
            String url = String.format(ULIST_URL, sb.toString());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return Collections.emptyList();
            }
            JsonNode diff = MAPPER.readTree(body).path("data").path("diff");
            if (!diff.isArray()) {
                return Collections.emptyList();
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
            return result;
        } catch (Exception e) {
            log.warn("批量拉取实时行情失败: {}", e.getMessage());
            return Collections.emptyList();
        }
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
