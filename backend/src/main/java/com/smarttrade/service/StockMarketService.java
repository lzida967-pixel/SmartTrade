package com.smarttrade.service;

import com.smarttrade.entity.StockInfo;
import com.smarttrade.vo.KlinePointVO;
import com.smarttrade.vo.StockQuoteVO;

import java.util.List;

/**
 * 外部行情数据服务（接入东方财富免费接口）
 */
public interface StockMarketService {

    /**
     * 拉取单只股票的实时行情
     */
    StockQuoteVO fetchRealtimeQuote(String stockCode, String market);

    /**
     * 批量拉取实时行情，返回顺序与入参一致；获取失败的元素为 null
     */
    List<StockQuoteVO> batchFetchRealtimeQuote(List<String> stockCodes, List<String> markets);

    /**
     * 一次性批量拉取多只股票的实时行情（使用东方财富 ulist 批量接口）。
     * 返回的 VO 仅包含行情字段，调用方可在外部合并 stock_info 中的 industry/plate 等。
     */
    List<StockQuoteVO> batchFetchQuoteForStocks(List<StockInfo> stocks);

    /**
     * 拉取最近 N 个交易日的日 K 线（前复权）
     *
     * @param limit 最近多少根
     */
    List<KlinePointVO> fetchDailyKline(String stockCode, String market, int limit);

    /**
     * 从东方财富抓取沪深 A 股 + 创业板 + 科创板的真实股票列表（按成交额排序）
     *
     * @param pageSize 期望返回数量，传 100 通常足够
     */
    List<StockInfo> fetchStockList(int pageSize);
}
