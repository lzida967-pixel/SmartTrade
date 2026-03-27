package com.smarttrade.config;

import com.smarttrade.utils.JwtUtils;
import com.smarttrade.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器，抽取 Token 中的 userId 存入 ThreadLocal
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求，因为跨域时浏览器会先发送一次预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        
        // 尝试从 "Bearer <token>" 结构中剥离
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtils.parseToken(token);
                if (jwtUtils.isTokenExpired(claims)) {
                    throw new RuntimeException("Token已过期，请重新登录");
                }
                
                // 将用户ID提取到上下文中，后续逻辑可以直接使用 UserContext.getUserId() 获取
                Long userId = claims.get("userId", Long.class);
                UserContext.setUserId(userId);
                
                return true;
            } catch (Exception e) {
                log.warn("Token validation failed: {}", e.getMessage());
            }
        }
        
        // 当缺失token或验证失败，直接返回 401 状态
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(401);
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录态失效，请重新登录\",\"data\":null}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后必须清理，以防线程复用导致的内存泄漏
        UserContext.remove();
    }
}