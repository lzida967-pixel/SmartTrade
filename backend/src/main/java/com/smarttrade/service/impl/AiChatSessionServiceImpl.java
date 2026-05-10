package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.AiChatSession;
import com.smarttrade.mapper.AiChatSessionMapper;
import com.smarttrade.service.AiChatSessionService;
import org.springframework.stereotype.Service;

@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession>
        implements AiChatSessionService {
}
