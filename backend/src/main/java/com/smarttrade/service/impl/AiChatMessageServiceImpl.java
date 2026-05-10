package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.AiChatMessage;
import com.smarttrade.mapper.AiChatMessageMapper;
import com.smarttrade.service.AiChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage>
        implements AiChatMessageService {
}
