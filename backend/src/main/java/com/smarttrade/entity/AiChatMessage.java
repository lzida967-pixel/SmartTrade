package com.smarttrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zidatrade_ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
    /** 思考型模型推理过程，普通模型为 null */
    private String reasoning;
    private LocalDateTime createdAt;
}
