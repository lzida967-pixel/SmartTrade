package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.TradeDeal;
import com.smarttrade.mapper.TradeDealMapper;
import com.smarttrade.service.TradeDealService;
import org.springframework.stereotype.Service;

@Service
public class TradeDealServiceImpl extends ServiceImpl<TradeDealMapper, TradeDeal>
        implements TradeDealService {
}
