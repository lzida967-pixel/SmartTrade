package com.smarttrade.controller;

import com.smarttrade.service.AiChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 助手对话接口 - SSE 流式输出
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    /**
     * 流式聊天
     * 请求体：{ "messages": [{"role":"user","content":"你好"}, ...] }
     * 响应：text/event-stream，事件类型有 delta / done / error
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        // 关键：阻断中间链路（Tomcat / Vite proxy / Nginx 等）对 SSE 的缓冲
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no"); // 让 nginx 不缓冲
        response.setCharacterEncoding("UTF-8");

        // 0L = 不超时（由上游 HttpClient timeout 控制），客户端断开时 emitter 会自动 complete
        SseEmitter emitter = new SseEmitter(0L);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.getOrDefault("messages", List.of());
        if (messages.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("messages 不能为空"));
            } catch (Exception ignore) {}
            emitter.complete();
            return emitter;
        }

        emitter.onCompletion(() -> log.debug("AI SSE 完成"));
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.debug("AI SSE 异常: {}", t.getMessage()));

        aiChatService.streamChat(messages, emitter);
        return emitter;
    }
}
