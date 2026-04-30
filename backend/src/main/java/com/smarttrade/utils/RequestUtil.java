package com.smarttrade.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前 HTTP 请求中提取 IP / UA
 * 兼容反向代理（X-Forwarded-For / X-Real-IP）
 */
public class RequestUtil {

    private RequestUtil() {}

    public static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getClientIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理时取第一个
            int idx = ip.indexOf(',');
            return (idx > 0 ? ip.substring(0, idx) : ip).trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return req.getRemoteAddr();
    }

    public static String getUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String ua = req.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
