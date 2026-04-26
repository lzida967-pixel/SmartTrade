package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.StockInfo;
import com.smarttrade.mapper.StockInfoMapper;
import com.smarttrade.service.StockInfoService;
import com.smarttrade.service.StockMarketService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {

    @Autowired
    private StockMarketService stockMarketService;

    @Value("${smarttrade.stock-pool-size:100}")
    private int stockPoolSize;

    /**
     * 应用启动后若股票池为空，则自动从东方财富拉取真实股票列表写入；
     * 网络失败时兜底使用本地 mock，避免整体不可用。
     */
    @PostConstruct
    public void autoInitOnStartup() {
        try {
            long count = this.count();
            if (count > 0) {
                return;
            }
            log.info("zidatrade_stock_info 为空，开始从东方财富拉取真实股票列表...");
            List<StockInfo> remote = stockMarketService.fetchStockList(stockPoolSize);
            if (remote != null && !remote.isEmpty()) {
                this.saveBatch(remote);
                log.info("真实股票池初始化完成，共 {} 支", remote.size());
                return;
            }
            log.warn("外部股票列表为空，回退到 mock 数据");
            initMockStocks();
            log.info("Mock 股票池初始化完成，共 {} 支", this.count());
        } catch (Exception e) {
            log.warn("自动初始化股票池失败: {}", e.getMessage());
        }
    }

    @Override
    public void initMockStocks() {
        List<StockInfo> mocks = Arrays.asList(
            createMock("000001", "SZ", "平安银行", "主板", "银行"),
            createMock("600519", "SH", "贵州茅台", "主板", "白酒"),
            createMock("002594", "SZ", "比亚迪",  "主板", "汽车整车"),
            createMock("601398", "SH", "工商银行", "主板", "银行"),
            createMock("300750", "SZ", "宁德时代", "创业板", "电池"),
            createMock("600036", "SH", "招商银行", "主板", "银行"),
            createMock("601857", "SH", "中国石油", "主板", "石油石化"),
            createMock("000858", "SZ", "五粮液",  "主板", "白酒"),
            createMock("601012", "SH", "隆基绿能", "主板", "光伏"),
            createMock("002714", "SZ", "牧原股份", "主板", "农牧"),
            createMock("300059", "SZ", "东方财富", "创业板", "证券"),
            createMock("600030", "SH", "中信证券", "主板", "证券")
        );
        for (StockInfo stock : mocks) {
            // Mybatis-plus 提供的方法：判断主键存在则更新，不存在则插入
            this.saveOrUpdate(stock);
        }
    }

    private StockInfo createMock(String code, String market, String name, String plate, String industry) {
        StockInfo info = new StockInfo();
        info.setStockCode(code);
        info.setMarket(market);
        info.setStockName(name);
        info.setPlateType(plate);
        info.setIndustryName(industry);
        info.setStatus(1);
        return info;
    }
}