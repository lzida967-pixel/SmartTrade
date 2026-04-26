package com.smarttrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttrade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}
