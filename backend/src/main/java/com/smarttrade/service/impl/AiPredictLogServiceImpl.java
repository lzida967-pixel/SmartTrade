package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.AiPredictLog;
import com.smarttrade.mapper.AiPredictLogMapper;
import com.smarttrade.service.AiPredictLogService;
import org.springframework.stereotype.Service;

@Service
public class AiPredictLogServiceImpl extends ServiceImpl<AiPredictLogMapper, AiPredictLog>
        implements AiPredictLogService {
}
