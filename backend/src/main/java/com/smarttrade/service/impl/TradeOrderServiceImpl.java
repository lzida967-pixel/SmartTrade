package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.TradeOrder;
import com.smarttrade.mapper.TradeOrderMapper;
import com.smarttrade.service.TradeOrderService;
import org.springframework.stereotype.Service;

@Service
public class TradeOrderServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder>
        implements TradeOrderService {
}
