package com.smarttrade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步执行配置：审计日志专用线程池，避免和业务线程互相影响
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("audit-log-");
        // 队列满时直接丢弃最老的，保业务不阻塞（合规可接受少量丢失，业务比合规优先）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 流式任务专用线程池（AI 报告、AI 聊天、分歧解读共用）。
     * 替代之前各 Service 里每次调用都 new Executors.newSingleThreadExecutor() 的泄漏写法。
     * 每个任务都是长阻塞（调 FastAPI / 流式调 Qwen），所以给较大池 + 较小队列，超载 CallerRuns 背压。
     */
    @Bean("sseStreamExecutor")
    public Executor sseStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 预测日志 / 轻量持久化异步池。用于 predict_log 异步落库等场景。
     * 写操作快，用较小池即可；队列稍大防丢。
     */
    @Bean("persistenceExecutor")
    public Executor persistenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("persist-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
