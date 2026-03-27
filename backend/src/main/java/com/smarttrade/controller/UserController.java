package com.smarttrade.controller;

import com.smarttrade.common.Result;
import com.smarttrade.dto.LoginDTO;
import com.smarttrade.dto.RegisterDTO;
import com.smarttrade.entity.User;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token, "登录成功");
    }

    @PostMapping("/register")
    public Result<String> register(@Validated @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success("注册成功");
    }

    /**
     * 获取当前登录人的账户资产信息
     */
    @GetMapping("/info")
    public Result<User> getUserInfo() {
        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if(user != null) {
            // 脱敏：不将密码返回给前端
            user.setPassword(null);
        }
        return Result.success(user);
    }
}