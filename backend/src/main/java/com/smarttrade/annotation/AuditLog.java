package com.smarttrade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：贴在 Controller / Service 方法上，由 AuditLogAspect 切面处理。
 *
 * <pre>
 * 用法示例：
 *   {@code @AuditLog(category = "TRADE", action = "PLACE_ORDER",
 *           targetType = "STOCK", target = "#dto.stockCode",
 *           summary = "下单 #{#dto.direction} #{#dto.stockCode} #{#dto.entrustQuantity}股")}
 *
 *   表达式中可引用方法形参：
 *     - 按形参名：#dto, #orderNo
 *     - 按下标：#p0, #p1
 *     - 引用返回值：#result（仅切面 AfterReturning 阶段可用）
 *
 *   summary 支持 #{...} 占位的 Spring SpEL 字符串模板。
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 日志分类：AUTH/TRADE/ACCOUNT/ADMIN_USER/ADMIN_STOCK/ADMIN_ORDER */
    String category();

    /** 动作名称，如 PLACE_ORDER */
    String action();

    /** 操作对象类型，如 ORDER / USER / STOCK，可为空 */
    String targetType() default "";

    /** target_id 的 SpEL 表达式，例如 "#dto.stockCode" 或 "#orderNo" */
    String target() default "";

    /** 一句话摘要，支持 #{spel} 占位 */
    String summary() default "";

    /**
     * 需要写入 details_json 的形参名列表（按名称从方法形参里提取）。
     * 例如 {"dto"} 会把 dto 整个对象序列化（已自动屏蔽 password/token 字段）。
     * 留空时则记录全部参数（按形参名）。
     */
    String[] includeArgs() default {};
}
