package com.smarttrade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttrade.common.Result;
import com.smarttrade.entity.User;
import com.smarttrade.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理后台 - 用户管理
 *
 * 路径前缀 /admin/users 由 AdminInterceptor 拦截校验 ADMIN 身份
 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * 用户分页列表（按 keyword 模糊搜索 username/nickname）
     */
    @GetMapping
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {

        Page<User> p = new Page<>(page, size);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        if (role != null && !role.isBlank()) qw.eq(User::getRole, role);
        if (status != null) qw.eq(User::getStatus, status);

        Page<User> result = userService.page(p, qw);
        // 屏蔽密码字段
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    /**
     * 改资金：action = SET（直接赋值）/ ADD（加减，amount 可为负）
     * body: { "available": 100000, "frozen": 0, "action": "SET" }
     */
    @PutMapping("/{id}/funds")
    public Result<User> updateFunds(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userService.getById(id);
        if (user == null) return Result.error("用户不存在");

        String action = (String) body.getOrDefault("action", "SET");
        BigDecimal available = numOrNull(body.get("available"));
        BigDecimal frozen = numOrNull(body.get("frozen"));

        if ("ADD".equalsIgnoreCase(action)) {
            if (available != null) user.setAvailableFunds(nz(user.getAvailableFunds()).add(available));
            if (frozen != null) user.setFrozenFunds(nz(user.getFrozenFunds()).add(frozen));
        } else { // SET
            if (available != null) user.setAvailableFunds(available);
            if (frozen != null) user.setFrozenFunds(frozen);
        }
        if (nz(user.getAvailableFunds()).compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("可用资金不能为负");
        }
        if (nz(user.getFrozenFunds()).compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("冻结资金不能为负");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        user.setPassword(null);
        return Result.success(user, "资金已更新");
    }

    /**
     * 改角色：USER / ADMIN
     */
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            return Result.error("非法角色");
        }
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setUpdatedAt(LocalDateTime.now());
        userService.updateById(u);
        return Result.success(null, "角色已更新");
    }

    /**
     * 改状态：1 启用 / 0 禁用
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return Result.error("非法状态");
        }
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        u.setUpdatedAt(LocalDateTime.now());
        userService.updateById(u);
        return Result.success(null, status == 1 ? "已启用" : "已禁用");
    }

    /**
     * 重置密码（重置为 123456，MD5）
     */
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        User u = new User();
        u.setId(id);
        // 与 UserServiceImpl 保持同款 MD5（无加盐）
        u.setPassword(org.springframework.util.DigestUtils.md5DigestAsHex("123456".getBytes()));
        u.setUpdatedAt(LocalDateTime.now());
        userService.updateById(u);
        return Result.success(null, "已重置为 123456");
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static BigDecimal numOrNull(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }
}
