package com.smarttrade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Chat 配置（DashScope OpenAI 兼容模式）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "smarttrade.ai.chat")
public class AiChatProperties {
    /** 是否启用 AI 助手 */
    private boolean enabled = true;
    /** OpenAI 兼容 base url */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** 服务商 API Key */
    private String apiKey = "";
    /** 模型名称：qwen-turbo / qwen-plus / qwen-max */
    private String model = "qwen-plus";
    /** 单次会话超时（秒） */
    private int timeoutSeconds = 60;
    /** 拼上下文时最多带几轮历史消息 */
    private int maxHistory = 20;
    /** System Prompt */
    private String systemPrompt = "你是 SmartTrade 平台的智能投资助手。";
}
