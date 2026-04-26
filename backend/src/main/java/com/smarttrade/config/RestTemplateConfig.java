package com.smarttrade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;

/**
 * RestTemplate 用于调用第三方行情接口
 *
 * 东方财富的 CDN 会校验 User-Agent / Referer，否则可能直接断流，
 * 表现为 "Unexpected end of file from server"。这里统一注入浏览器样的请求头。
 */
@Configuration
public class RestTemplateConfig {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Bean
    public RestTemplate restTemplate() {
        // 使用 JDK 11+ 的 HttpClient（支持 HTTP/2 与现代 TLS）。
        // SimpleClientHttpRequestFactory 用的是老式 HttpURLConnection，
        // 在访问 push2his.eastmoney.com 等仅暴露 HTTP/2 的 CDN 时会出现
        // "Unexpected end of file from server"。
        // 强制 HTTP/1.1：push2his.eastmoney.com 在 HTTP/2 协商下会异常关闭连接，
        // 表现为 "HTTP/1.1 header parser received no bytes"。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestTemplate rt = new RestTemplate(factory);

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
