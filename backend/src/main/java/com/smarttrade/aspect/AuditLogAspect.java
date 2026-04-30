package com.smarttrade.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrade.annotation.AuditLog;
import com.smarttrade.entity.User;
import com.smarttrade.service.AuditLogService;
import com.smarttrade.service.UserService;
import com.smarttrade.utils.RequestUtil;
import com.smarttrade.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @AuditLog 切面：环绕通知
 *  1. 记录开始时间
 *  2. 调用真实方法
 *  3. 成功 / 失败均尝试落一条审计日志（异步）
 *  4. SpEL 解析 target / summary 表达式，可引用形参 #dto / #orderNo / #p0 等，及 #result
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParserContext templateCtx = new TemplateParserContext(); // 识别 #{...}
    private final DefaultParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /** 这些字段名出现在请求参数对象里时，序列化时会被屏蔽 */
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
            "password", "passwd", "pwd", "newPassword", "oldPassword",
            "token", "accessToken", "refreshToken", "secret", "apiKey"
    ));

    @Around("@annotation(auditAnno)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditAnno) throws Throwable {
        long startMs = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                writeAuditLog(pjp, auditAnno, result, error, System.currentTimeMillis() - startMs);
            } catch (Exception e) {
                // 切面里任何异常都不能往外冒，避免拖累主业务
                log.error("[Audit] 写审计日志失败 (aspect): {}", e.getMessage(), e);
            }
        }
    }

    private void writeAuditLog(ProceedingJoinPoint pjp, AuditLog anno,
                               Object returnValue, Throwable error, long costMs) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();
        String[] paramNames = paramNameDiscoverer.getParameterNames(method);

        // ===== 1. 构造 SpEL 评估上下文 =====
        EvaluationContext ctx = new StandardEvaluationContext();
        boolean nameDiscoveryOk = paramNames != null;
        if (nameDiscoveryOk) {
            for (int i = 0; i < paramNames.length; i++) {
                ctx.setVariable(paramNames[i], i < args.length ? args[i] : null);
            }
        }
        // Fallback 1：永远注册 #p0..#pN 和 #a0..#aN
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
            ctx.setVariable("a" + i, args[i]);
        }
        // Fallback 2：当 -parameters 未启用时，按形参类型简单名注册
        //   PlaceOrderDTO → "placeOrderDTO"  / 同时也注册 "dto" 这种首字母小写的尾段
        if (!nameDiscoveryOk) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) continue;
                String simple = args[i].getClass().getSimpleName();
                if (simple.isEmpty()) continue;
                String camel = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
                ctx.setVariable(camel, args[i]);
                // 截尾常见后缀：PlaceOrderDTO → "dto", LoginRequest → "request"
                int upper = -1;
                for (int j = simple.length() - 1; j > 0; j--) {
                    if (Character.isUpperCase(simple.charAt(j))) { upper = j; break; }
                }
                if (upper > 0) {
                    String tail = Character.toLowerCase(simple.charAt(upper)) + simple.substring(upper + 1);
                    ctx.setVariable(tail, args[i]);
                }
            }
        }
        ctx.setVariable("result", returnValue);

        // ===== 2. 解析 target / summary =====
        String targetId = evalString(anno.target(), ctx, false);
        String summary = evalString(anno.summary(), ctx, true);

        // ===== 3. 收集 details_json =====
        Map<String, Object> details = collectDetails(anno, paramNames, args);

        // ===== 4. 当前用户信息 =====
        Long userId = UserContext.getUserId();
        String username = null;
        String role = "GUEST";
        if (userId != null) {
            try {
                User u = userService.getById(userId);
                if (u != null) {
                    username = u.getUsername();
                    role = u.getRole();
                }
            } catch (Exception ignore) { /* 用户查询失败也不影响日志 */ }
        }

        // ===== 5. 构建并异步写入 =====
        com.smarttrade.entity.AuditLog log = new com.smarttrade.entity.AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setRole(role);
        log.setCategory(anno.category());
        log.setAction(anno.action());
        log.setTargetType(emptyToNull(anno.targetType()));
        log.setTargetId(emptyToNull(targetId));
        log.setSummary(emptyToNull(summary));
        log.setDetailsJson(toJson(details));
        log.setResult(error == null ? "SUCCESS" : "FAILURE");
        log.setErrorMsg(error == null ? null : truncate(error.getMessage(), 500));
        log.setIp(RequestUtil.getClientIp());
        log.setUserAgent(RequestUtil.getUserAgent());
        log.setCostMs((int) costMs);

        auditLogService.recordAsync(log);
    }

    private String evalString(String expr, EvaluationContext ctx, boolean asTemplate) {
        if (expr == null || expr.isBlank()) return null;
        // 不含 SpEL 标记 → 直接返回原文
        boolean isTemplate = asTemplate && expr.contains("#{");
        boolean isExpr = !asTemplate && expr.startsWith("#");
        if (!isTemplate && !isExpr) return expr;
        try {
            Expression e = asTemplate
                    ? parser.parseExpression(expr, templateCtx)
                    : parser.parseExpression(expr);
            Object v = e.getValue(ctx);
            return v == null ? null : v.toString();
        } catch (Exception ex) {
            log.warn("[Audit] SpEL 解析失败 expr={} cause={}", expr, ex.getMessage());
            return expr; // 保留原文，便于排查
        }
    }

    private Map<String, Object> collectDetails(AuditLog anno, String[] paramNames, Object[] args) {
        if (paramNames == null || paramNames.length == 0) return null;
        Set<String> include = anno.includeArgs().length == 0
                ? null
                : new HashSet<>(Arrays.asList(anno.includeArgs()));
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            String name = paramNames[i];
            if (include != null && !include.contains(name)) continue;
            Object val = i < args.length ? args[i] : null;
            details.put(name, sanitize(val));
        }
        return details;
    }

    /**
     * 递归屏蔽 password/token 等敏感字段：用反射读对象的所有 getter，把命中字段替换成 "***"
     */
    private Object sanitize(Object val) {
        if (val == null) return null;
        if (isPrimitiveOrCommon(val)) return val;
        try {
            // 把对象转 Map，命中的敏感字段写成 ***
            Map<String, Object> map = jsonMapper.convertValue(val, Map.class);
            if (map == null) return val.toString();
            map.replaceAll((k, v) -> SENSITIVE_FIELDS.contains(k) && v != null ? "***" : v);
            return map;
        } catch (Exception e) {
            return val.toString();
        }
    }

    private boolean isPrimitiveOrCommon(Object val) {
        return val instanceof Number || val instanceof CharSequence || val instanceof Boolean
                || val instanceof Character || val instanceof Enum<?>;
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(details);
        } catch (Exception e) {
            return null;
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
