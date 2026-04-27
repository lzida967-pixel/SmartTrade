package com.smarttrade.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * RestTemplate 用于调用第三方行情接口。
 *
 * 本项目主要场景：长期运行的 Spring 进程不停轮询东方财富 push2.eastmoney.com
 * （前端 30s 自动刷新 + 后台定时任务）。
 *
 * 历史上踩到的坑及当前选型：
 *   1. SimpleClientHttpRequestFactory（默认）—— 老式 HttpURLConnection，
 *      访问 push2his 报 "Unexpected end of file from server"。
 *   2. JdkClientHttpRequestFactory（JDK11+ HttpClient，强制 HTTP/1.1）—— 连接池
 *      不做 stale-connection 校验，一旦对端静默关闭 keep-alive 连接，复用就会抛
 *      "HTTP/1.1 header parser received no bytes"。重试也救不回来（仍命中坏连接）。
 *   3. 当前方案：Apache HttpClient5
 *      - 自带 validateAfterInactivity：连接闲置超过阈值时复用前先探活
 *      - evictExpiredConnections / evictIdleConnections：周期性清掉陈旧连接
 *      - 连接池保活，性能优于"每次都关连接"的暴力方案
 */
@Configuration
public class RestTemplateConfig {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Bean
    public RestTemplate restTemplate() {
        // 连接池：复用连接 + 主动探活
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(50)
                        .setMaxConnPerRoute(20)
                        // 关键：连接闲置超过 2s 再次使用前先做 stale check
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(5))
                                .setSocketTimeout(Timeout.ofSeconds(10))
                                .setValidateAfterInactivity(TimeValue.ofSeconds(2))
                                // 连接最长存活 30s，超过就关掉重建
                                .setTimeToLive(TimeValue.ofSeconds(30))
                                .build())
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(10))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 后台周期性驱逐过期/闲置连接，避免请求时才发现连接坏掉
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                // 出现 IOException（含 NoHttpResponseException、socket close 等）时自动重试 2 次
                .setRetryStrategy(new org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy(
                        2, TimeValue.ofMilliseconds(200)))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate rt = new RestTemplate(factory);

        // 统一注入浏览器样的请求头：东财 CDN 会校验 UA/Referer
        ClientHttpRequestInterceptor headerInterceptor = (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            if (!headers.containsKey(HttpHeaders.USER_AGENT)) {
                headers.set(HttpHeaders.USER_AGENT, UA);
            }
            if (!headers.containsKey(HttpHeaders.REFERER)) {
                headers.set(HttpHeaders.REFERER, "https://quote.eastmoney.com/");
            }
            if (!headers.containsKey(HttpHeaders.ACCEPT)) {
                headers.set(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
            }
            return execution.execute(request, body);
        };
        rt.setInterceptors(Collections.singletonList(headerInterceptor));
        return rt;
    }
}
