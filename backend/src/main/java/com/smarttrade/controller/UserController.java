package com.smarttrade.controller;

import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.dto.LoginDTO;
import com.smarttrade.dto.RegisterDTO;
import com.smarttrade.dto.UpdateProfileDTO;
import com.smarttrade.entity.User;
import com.smarttrade.entity.UserAssetSnapshot;
import com.smarttrade.service.AssetService;
import com.smarttrade.service.AuditLogService;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.UserContext;
import com.smarttrade.vo.UserAssetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 登录无法走 AOP（登录前没有 UserContext），手动写审计日志：
     *  - 成功：记一条 LOGIN SUCCESS
     *  - 失败：记一条 LOGIN FAILURE，附错误用户名 + 错误信息（用于检测撞库）
     */
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        String username = loginDTO == null ? null : loginDTO.getUsername();
        Map<String, Object> details = new HashMap<>();
        details.put("username", username);
        try {
            String token = userService.login(loginDTO);
            // 登录成功：尝试反查 userId（可选）
            User u = userService.lambdaQuery().eq(User::getUsername, username).one();
            Long uid = u == null ? null : u.getId();
            String role = u == null ? "USER" : u.getRole();
            auditLogService.record("AUTH", "LOGIN", uid, username, role,
                    "USER", uid == null ? null : uid.toString(),
                    "登录成功", details, true, null);
            return Result.success(token, "登录成功");
        } catch (Exception e) {
            auditLogService.record("AUTH", "LOGIN", null, username, "GUEST",
                    "USER", null, "登录失败", details, false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/register")
    @AuditLog(category = "AUTH", action = "REGISTER",
            targetType = "USER",
            target = "#registerDTO.username",
            summary = "新用户注册：#{#registerDTO.username}",
            includeArgs = {"registerDTO"})
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

    /**
     * 修改用户基本信息 (例如昵称、头像)
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileDTO dto) {
        Long userId = UserContext.getUserId();
        User user = new User();
        user.setId(userId);
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        // mybatis-plus 将自动忽略为 null 的字段，只更新非空字段
        userService.updateById(user);
        return Result.success(null, "信息更新成功");
    }

    /**
     * 用户资产聚合：账户资金 + 持仓列表 + 持仓市值（含实时行情计算的市值/浮盈亏）
     */
    @GetMapping("/asset")
    public Result<UserAssetVO> getAsset() {
        Long userId = UserContext.getUserId();
        UserAssetVO vo = assetService.buildCurrentAsset(userId);
        if (vo == null) {
            return Result.error("用户不存在");
        }
        return Result.success(vo);
    }

    /**
     * 资产收益曲线：返回最近 days 天的快照点（按日期升序）
     */
    @GetMapping("/asset/curve")
    public Result<List<UserAssetSnapshot>> getAssetCurve(
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        Long userId = UserContext.getUserId();
        return Result.success(assetService.getCurve(userId, days));
    }
}
