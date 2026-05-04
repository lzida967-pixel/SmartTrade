package com.smarttrade.controller;

import com.smarttrade.annotation.AuditLog;
import com.smarttrade.common.Result;
import com.smarttrade.dto.ChangePasswordDTO;
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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 忘记密码 — 提交重置申请。
     *
     * 流程：用户在登录页填用户名 → 后端记一条审计日志 → 管理员在审计页看到 →
     * 用现有「重置密码」按钮把密码重置为 123456 → 线下告知用户。
     *
     * 安全考虑：
     *   1) 不需要登录态（已加入 WebMvcConfig 放行列表）
     *   2) 同一用户名 5 分钟内只允许提交 1 次（内存简单限流）
     *   3) 即使用户名不存在也返回 success，避免被用作账号枚举
     */
    @PostMapping("/password-reset-request")
    public Result<String> passwordResetRequest(@RequestBody Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        if (username == null || username.isBlank()) {
            return Result.error(400, "请输入用户名");
        }
        username = username.trim();

        // 限流：同名 5 分钟内仅一次
        long now = System.currentTimeMillis();
        Long last = RESET_REQUEST_RATE.get(username);
        if (last != null && now - last < 5 * 60 * 1000L) {
            return Result.error(429, "请勿频繁提交，请稍候再试");
        }
        RESET_REQUEST_RATE.put(username, now);
        // 顺手清理过期 key（容量上限保护）
        if (RESET_REQUEST_RATE.size() > 1000) {
            RESET_REQUEST_RATE.entrySet().removeIf(e -> now - e.getValue() > 30 * 60 * 1000L);
        }

        // 反查是否真的存在该用户（仅用于落库 userId，对客户端永远返回成功提示）
        User u = userService.lambdaQuery().eq(User::getUsername, username).one();
        Map<String, Object> details = new HashMap<>();
        details.put("username", username);
        details.put("userExists", u != null);

        auditLogService.record(
                "AUTH", "PASSWORD_RESET_REQUEST",
                u == null ? null : u.getId(), username, u == null ? "GUEST" : u.getRole(),
                "USER", u == null ? null : u.getId().toString(),
                "用户提交了密码重置申请", details, true, null);

        return Result.success(null, "申请已提交，请联系管理员核实身份后重置");
    }

    /** 用户名 → 上次提交时间戳，用于简单限流 */
    private static final Map<String, Long> RESET_REQUEST_RATE = new ConcurrentHashMap<>();

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
     * 修改密码：需要提供当前密码核验，防止他人在未锁屏时改密码
     */
    @PutMapping("/password")
    @AuditLog(category = "AUTH", action = "CHANGE_PASSWORD",
            targetType = "USER", target = "#userId",
            summary = "用户修改了登录密码")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordDTO dto) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, dto);
        return Result.success(null, "密码修改成功，请重新登录");
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
