package com.smarttrade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarttrade.config.AiChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 调用 DashScope（通义千问）OpenAI 兼容流式 API，
 * 把上游返回的增量 token 转发为 SSE 事件给前端。
 */
@Slf4j
@Service
public class AiChatService {

    @Autowired
    private AiChatProperties properties;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 异步流式聊天
     *
     * @param userMessages 前端传过来的对话历史（不含 system，按时间正序）
     * @param emitter      SSE 通道
     */
    public void streamChat(List<Map<String, String>> userMessages, SseEmitter emitter) {
        if (!properties.isEnabled()) {
            sendError(emitter, "AI 助手未启用");
            return;
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            sendError(emitter, "AI 助手未配置 API Key（请设置环境变量 DASHSCOPE_API_KEY）");
            return;
        }

        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ai-chat-stream");
            t.setDaemon(true);
            return t;
        }).execute(() -> doStream(userMessages, emitter));
    }

    private void doStream(List<Map<String, String>> userMessages, SseEmitter emitter) {
        try {
            // 构造请求体
            ObjectNode body = mapper.createObjectNode();
            body.put("model", properties.getModel());
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            // 系统提示词
            ObjectNode sys = mapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", properties.getSystemPrompt());
            messages.add(sys);

            // 历史 + 当前用户消息（截尾保留最后 N 条）
            int max = Math.max(2, properties.getMaxHistory());
            int start = Math.max(0, userMessages.size() - max);
            for (int i = start; i < userMessages.size(); i++) {
                Map<String, String> m = userMessages.get(i);
                String role = m.getOrDefault("role", "user");
                String content = m.getOrDefault("content", "");
                if (content.isBlank()) continue;
                ObjectNode n = mapper.createObjectNode();
                n.put("role", role);
                n.put("content", content);
                messages.add(n);
            }

            // 发起流式请求
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofLines());

            if (resp.statusCode() != 200) {
                String errBody = resp.body().limit(50).reduce("", (a, b) -> a + b);
                log.warn("AI 上游响应非 200：status={}, body={}", resp.statusCode(), errBody);
                sendError(emitter, "AI 服务返回错误：" + resp.statusCode());
                return;
            }

            // 上游每一行通常是 "data: {...}" 或空行；遇到 "[DONE]" 表示结束
            long t0 = System.currentTimeMillis();
            int[] chunkCount = {0};
            try (java.util.stream.Stream<String> lines = resp.body()) {
                lines.forEach(line -> {
                    int n = handleUpstreamLine(line, emitter);
                    if (n > 0) {
                        chunkCount[0]++;
                        if (log.isDebugEnabled() && (chunkCount[0] <= 5 || chunkCount[0] % 50 == 0)) {
                            log.debug("[AI流] 第 {} 个 chunk 已发出 @{}ms", chunkCount[0], System.currentTimeMillis() - t0);
                        }
                    }
                });
            }
            log.info("[AI流] 完成：共 {} 个 chunk，耗时 {} ms", chunkCount[0], System.currentTimeMillis() - t0);
            // 正常结束 → 发 done 事件
            try {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            } catch (IOException ignore) { /* client closed */ }
            emitter.complete();
        } catch (Exception e) {
            log.error("AI 流式请求失败", e);
            sendError(emitter, "AI 调用失败：" + e.getMessage());
        }
    }

    /**
     * 处理一行上游 SSE，转发给前端。返回值 = 实际转发出去的事件数（0 表示没转发）
     */
    private int handleUpstreamLine(String rawLine, SseEmitter emitter) {
        if (rawLine == null) return 0;
        String line = rawLine.trim();
        if (line.isEmpty()) return 0;
        if (!line.startsWith("data:")) return 0;
        String json = line.substring(5).trim();
        if (json.isEmpty()) return 0;
        if ("[DONE]".equals(json)) return 0; // 由外层统一发 done

        try {
            JsonNode node = mapper.readTree(json);
            JsonNode choices = node.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return 0;
            JsonNode delta = choices.get(0).path("delta");

            int sent = 0;
            // 思考型模型（qwen3.5 thinking）会先吐 reasoning_content，再吐 content
            String reasoning = delta.path("reasoning_content").asText("");
            if (!reasoning.isEmpty()) {
                emitter.send(SseEmitter.event().name("reasoning").data(reasoning));
                sent++;
            }
            String piece = delta.path("content").asText("");
            if (!piece.isEmpty()) {
                emitter.send(SseEmitter.event().name("delta").data(piece));
                sent++;
            }
            return sent;
        } catch (IOException e) {
            log.debug("SSE 写出失败（客户端可能已断开）：{}", e.getMessage());
            return 0;
        } catch (Exception e) {
            log.warn("解析上游流式行失败: {} -> {}", rawLine, e.getMessage());
            return 0;
        }
    }

    private void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(msg));
        } catch (IOException ignore) { /* nothing to do */ }
        emitter.complete();
    }
}
