package com.smarttrade.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 全局统一接口返回包装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    // 状态码：200为成功，其余均为失败或者特定业务码
    private Integer code;
    
    // 错误信息或成功提示
    private String msg;
    
    // 数据载体
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> error() {
        return new Result<>(500, "系统未知错误，请稍后再试", null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}