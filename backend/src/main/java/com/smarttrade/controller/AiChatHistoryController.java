package com.smarttrade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smarttrade.common.Result;
import com.smarttrade.entity.AiChatMessage;
import com.smarttrade.entity.AiChatSession;
import com.smarttrade.service.AiChatMessageService;
import com.smarttrade.service.AiChatSessionService;
import com.smarttrade.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 对话历史管理：会话 CRUD + 消息持久化
 */
@Slf4j
@RestController
@RequestMapping("/ai/sessions")
public class AiChatHistoryController {

    @Autowired
    private AiChatSessionService sessionService;
    @Autowired
    private AiChatMessageService messageService;

    /** 获取当前用户所有会话（按最后更新倒序） */
    @GetMapping
    public Result<List<AiChatSession>> listSessions() {
        Long userId = UserContext.getUserId();
        List<AiChatSession> list = sessionService.list(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdatedAt)
        );
        return Result.success(list);
    }

    /** 创建新会话 */
    @PostMapping
    public Result<AiChatSession> createSession(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String title = body.getOrDefault("title", "新对话");
        if (title.isBlank()) title = "新对话";
        if (title.length() > 100) title = title.substring(0, 100);

        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionService.save(session);
        return Result.success(session);
    }

    /** 修改会话标题 */
    @PatchMapping("/{id}/title")
    public Result<Void> renameSession(@PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        AiChatSession session = sessionService.getById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        String title = body.getOrDefault("title", "").trim();
        if (title.isBlank()) return Result.error(400, "标题不能为空");
        if (title.length() > 100) title = title.substring(0, 100);

        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionService.updateById(session);
        return Result.success(null);
    }

    /** 删除会话（含其所有消息） */
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        AiChatSession session = sessionService.getById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        messageService.remove(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, id)
        );
        sessionService.removeById(id);
        return Result.success(null);
    }

    /** 读取会话内所有消息 */
    @GetMapping("/{id}/messages")
    public Result<List<AiChatMessage>> getMessages(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        AiChatSession session = sessionService.getById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        List<AiChatMessage> msgs = messageService.list(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, id)
                        .orderByAsc(AiChatMessage::getId)
        );
        return Result.success(msgs);
    }

    /**
     * 批量保存消息（每次对话结束后前端调用，传入本轮新增的消息列表）
     * 同时更新会话的 updatedAt；若标题仍为"新对话"则用第一条 user 消息自动命名。
     */
    @PostMapping("/{id}/messages")
    public Result<Void> saveMessages(@PathVariable Long id,
                                     @RequestBody List<Map<String, String>> messages) {
        Long userId = UserContext.getUserId();
        AiChatSession session = sessionService.getById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            return Result.error(403, "无权操作");
        }
        if (messages == null || messages.isEmpty()) return Result.success(null);

        LocalDateTime now = LocalDateTime.now();
        for (Map<String, String> m : messages) {
            String role = m.getOrDefault("role", "user");
            String content = m.getOrDefault("content", "");
            if (content.isBlank()) continue;
            AiChatMessage msg = new AiChatMessage();
            msg.setSessionId(id);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            String reasoning = m.get("reasoning");
            if (reasoning != null && !reasoning.isBlank()) msg.setReasoning(reasoning);
            msg.setCreatedAt(now);
            messageService.save(msg);

            // 自动以第一条 user 消息命名会话（截取前 30 字）
            if ("user".equals(role) && "新对话".equals(session.getTitle())) {
                String autoTitle = content.length() > 30 ? content.substring(0, 30) + "…" : content;
                session.setTitle(autoTitle);
            }
        }
        session.setUpdatedAt(now);
        sessionService.updateById(session);
        return Result.success(null);
    }
}
