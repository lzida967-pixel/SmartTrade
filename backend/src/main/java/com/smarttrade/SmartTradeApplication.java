package com.smarttrade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.smarttrade.mapper")
@EnableScheduling // 允许开启定时任务以调度 Python 大模型预测与抓取最新行情
public class SmartTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTradeApplication.class, args);
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║          SmartTrade 交易预测后端已启动       ║");
        System.out.println("║  API 文档: http://localhost:8080/api/doc.html║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

}
