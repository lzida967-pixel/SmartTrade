package com.smarttrade.utils;

/**
 * 基于 ThreadLocal 的当前登录用户上下文
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    /**
     * 清除，防止内存泄漏
     */
    public static void remove() {
        USER_ID_THREAD_LOCAL.remove();
    }
}