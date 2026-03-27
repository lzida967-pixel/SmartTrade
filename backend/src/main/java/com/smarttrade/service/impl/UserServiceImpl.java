package com.smarttrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smarttrade.dto.LoginDTO;
import com.smarttrade.dto.RegisterDTO;
import com.smarttrade.entity.User;
import com.smarttrade.mapper.UserMapper;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public String login(LoginDTO loginDTO) {
        // 先检查用户是否存在
        User user = this.getBaseMapper().selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, loginDTO.getUsername())
        );

        if (user == null) {
            throw new RuntimeException("该账号不存在或未注册");
        }
        
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("该账号已被系统封禁");
        }

        // 加密密码再比对 (MD5简单的加盐机制或直接MD5)
        String md5Password = DigestUtils.md5DigestAsHex(loginDTO.getPassword().getBytes());
        if (!user.getPassword().equals(md5Password)) {
            throw new RuntimeException("账号或密码错误");
        }

        // 生成并返回 token
        return jwtUtils.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        // 判断是否重名
        Long count = this.getBaseMapper().selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, registerDTO.getUsername())
        );
        if (count > 0) {
            throw new RuntimeException("账号已被注册，请尝试其他账号");
        }

        User newUser = new User();
        newUser.setUsername(registerDTO.getUsername());
        // 使用 MD5 简单加密
        newUser.setPassword(DigestUtils.md5DigestAsHex(registerDTO.getPassword().getBytes()));
        
        newUser.setNickname(registerDTO.getNickname() != null && !registerDTO.getNickname().isEmpty() 
            ? registerDTO.getNickname() 
            : "Trader_" + (System.currentTimeMillis() % 10000));
            
        // 赋予 100 万 初始模拟资金
        newUser.setTotalAssets(new BigDecimal("1000000.00"));
        newUser.setAvailableFunds(new BigDecimal("1000000.00"));
        newUser.setFrozenFunds(BigDecimal.ZERO);
        newUser.setStatus(1);
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        this.getBaseMapper().insert(newUser);
    }
}