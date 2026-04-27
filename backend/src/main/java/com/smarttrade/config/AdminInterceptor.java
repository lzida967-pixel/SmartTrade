package com.smarttrade.config;

import com.smarttrade.entity.User;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理后台权限拦截器：
 *   - 在 JwtInterceptor 之后执行
 *   - 校验当前 UserContext.userId 对应的用户是否为 ADMIN
 *   - 仅拦截 /admin/** 路径
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        Long userId = UserContext.getUserId();
        if (userId == null) {
            return reject(response, 401, "未登录或登录态失效");
        }
        User user = userService.getById(userId);
        if (user == null) {
            return reject(response, 401, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return reject(response, 403, "账号已被禁用");
        }
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            log.warn("非管理员尝试访问管理接口: userId={} role={} path={}",
                    userId, user.getRole(), request.getRequestURI());
            return reject(response, 403, "权限不足，需要管理员身份");
        }
        return true;
    }

    private boolean reject(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(code);
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }
}
