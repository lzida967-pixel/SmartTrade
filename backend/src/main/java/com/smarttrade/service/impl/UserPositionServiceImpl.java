package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.entity.UserPosition;
import com.smarttrade.mapper.UserPositionMapper;
import com.smarttrade.service.UserPositionService;
import org.springframework.stereotype.Service;

@Service
public class UserPositionServiceImpl extends ServiceImpl<UserPositionMapper, UserPosition>
        implements UserPositionService {
}
