package com.smarttrade.controller;

import com.smarttrade.common.Result;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.StockMarketService;
import com.smarttrade.vo.KlinePointVO;
import com.smarttrade.vo.StockQuoteVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock")
public class StockInfoController {

    @Autowired
    private StockInfoService stockInfoService;

    @Autowired
    private StockMarketService stockMarketService;

    /**
     * 股票池基础信息列表（不含实时行情）
     */
    @GetMapping("/list")
    public Result<List<StockInfo>> list() {
        return Result.success(stockInfoService.list());
    }

    /**
     * 行情列表：股票池 + 东方财富实时行情.
     * 使用东方财富 ulist 批量接口，一次 HTTP 请求完成所有股票的实时行情.
     */
    @GetMapping("/quotes")
    public Result<List<StockQuoteVO>> quotes() {
        List<StockInfo> stocks = stockInfoService.list();
        List<StockQuoteVO> quotes = stockMarketService.batchFetchQuoteForStocks(stocks);
        // 用本地 stock_info 中的板块/行业补齐 VO
        Map<String, StockInfo> infoMap = new HashMap<>(stocks.size() * 2);
        for (StockInfo info : stocks) {
            infoMap.put(info.getStockCode(), info);
        }
        for (StockQuoteVO vo : quotes) {
            StockInfo info = infoMap.get(vo.getStockCode());
            if (info == null) continue;
            vo.setPlateType(info.getPlateType());
            vo.setIndustryName(info.getIndustryName());
            if (vo.getStockName() == null) {
                vo.setStockName(info.getStockName());
            }
            if (vo.getMarket() == null) {
                vo.setMarket(info.getMarket());
            }
        }
        return Result.success(quotes);
    }

    /**
     * 单只股票详情：基础信息 + 实时行情
     */
    @GetMapping("/detail/{code}")
    public Result<StockQuoteVO> detail(@PathVariable("code") String code) {
        StockInfo info = stockInfoService.getById(code);
        StockQuoteVO vo = stockMarketService.fetchRealtimeQuote(code,
                info != null ? info.getMarket() : null);
        if (vo == null) {
            vo = new StockQuoteVO();
            vo.setStockCode(code);
            if (info != null) {
                vo.setStockName(info.getStockName());
                vo.setMarket(info.getMarket());
            }
        }
        if (info != null) {
            vo.setPlateType(info.getPlateType());
            vo.setIndustryName(info.getIndustryName());
            if (vo.getStockName() == null) {
                vo.setStockName(info.getStockName());
            }
        }
        return Result.success(vo);
    }

    /**
     * 历史 K 线（前复权日 K，最多 limit 根，默认 120）
     */
    @GetMapping("/kline/{code}")
    public Result<List<KlinePointVO>> kline(@PathVariable("code") String code,
                                            @RequestParam(value = "limit", defaultValue = "120") Integer limit) {
        StockInfo info = stockInfoService.getById(code);
        String market = info != null ? info.getMarket() : null;
        List<KlinePointVO> list = stockMarketService.fetchDailyKline(code, market,
                limit == null ? 120 : limit);
        return Result.success(list);
    }

    /**
     * 只有管理员或者系统初始化才应该调用
     */
    @PostMapping("/init")
    public Result<String> initMockData() {
        stockInfoService.initMockStocks();
        return Result.success("测试股票池初始化成功");
    }
}