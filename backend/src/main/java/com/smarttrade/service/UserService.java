package com.smarttrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smarttrade.dto.LoginDTO;
import com.smarttrade.dto.RegisterDTO;
import com.smarttrade.entity.User;

public interface UserService extends IService<User> {

    /**
     * 登录
     * @return 返回 JWT Token
     */
    String login(LoginDTO loginDTO);

    /**
     * 注册
     */
    void register(RegisterDTO registerDTO);
    
}