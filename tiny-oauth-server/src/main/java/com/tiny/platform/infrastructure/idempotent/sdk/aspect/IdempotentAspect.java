package com.tiny.platform.infrastructure.idempotent.sdk.aspect;

import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.idempotent.sdk.resolver.IdempotentKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 幂等性切面
 * 
 * <p>拦截标记了 {@link Idempotent} 注解的方法，实现幂等性控制。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@Aspect
public class IdempotentAspect {
    
    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    
    private final IdempotentEngine engine;
    private final List<IdempotentKeyResolver> keyResolvers;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    public IdempotentAspect(IdempotentEngine engine, List<IdempotentKeyResolver> keyResolvers) {
        this.engine = engine;
        this.keyResolvers = keyResolvers != null ? keyResolvers : List.of();
    }
    
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 构建策略
        IdempotentStrategy strategy = new IdempotentStrategy(
            idempotent.timeout(),
            idempotent.failOpen()
        );
        
        // 生成幂等性 Key
        IdempotentKey key = generateKey(joinPoint, method, idempotent);
        
        // 构建上下文
        IdempotentContext context = new IdempotentContext(key, strategy);
        
        try {
            // 使用 Engine 执行
            return engine.execute(context, () -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException e) {
            // 转换为 SDK 异常
            throw new com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException(idempotent.message());
        } catch (RuntimeException e) {
            // 重新抛出业务异常
            if (e.getCause() instanceof Throwable) {
                throw e.getCause();
            }
            throw e;
        }
    }
    
    /**
     * 生成幂等性 key
     */
    private IdempotentKey generateKey(ProceedingJoinPoint joinPoint, Method method, Idempotent idempotent) {
        String keyExpression = idempotent.key();
        
        // 如果指定了 key 表达式，使用 SpEL 解析
        if (!keyExpression.isEmpty()) {
            String uniqueKey = parseKeyExpression(joinPoint, method, keyExpression);
            return IdempotentKey.of("http", getScope(method), uniqueKey);
        }
        
        // 尝试使用 KeyResolver
        for (IdempotentKeyResolver resolver : keyResolvers) {
            try {
                IdempotentKey key = resolver.resolve(joinPoint, method, joinPoint.getArgs());
                if (key != null) {
                    return key;
                }
            } catch (Exception e) {
                log.debug("KeyResolver {} 解析失败: {}", resolver.getClass().getName(), e.getMessage());
            }
        }
        
        // 使用默认策略
        return generateDefaultKey(joinPoint, method);
    }
    
    /**
     * 获取作用域（方法路径）
     */
    private String getScope(Method method) {
        // 简化版本：使用类名+方法名
        // 实际应该从 HTTP 请求中获取 path
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
    
    /**
     * 解析 SpEL 表达式生成 key
     */
    private String parseKeyExpression(ProceedingJoinPoint joinPoint, Method method, String expression) {
        try {
            EvaluationContext context = createEvaluationContext(joinPoint, method);
            Expression expr = parser.parseExpression(expression);
            Object value = expr.getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("解析幂等性key表达式失败: expression={}, error={}", expression, e.getMessage());
            return "";
        }
    }
    
    /**
     * 创建 SpEL 表达式上下文
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint, Method method) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 获取方法参数名
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        
        // 将参数添加到上下文
        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        
        // 添加 HttpServletRequest 到上下文
        HttpServletRequest request = getRequest();
        if (request != null) {
            context.setVariable("request", request);
        }
        
        return context;
    }
    
    /**
     * 生成默认的幂等性 key
     */
    private IdempotentKey generateDefaultKey(ProceedingJoinPoint joinPoint, Method method) {
        HttpServletRequest request = getRequest();
        String uniqueKey;
        
        // 优先使用请求头中的 X-Idempotency-Key
        if (request != null) {
            String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                uniqueKey = idempotencyKey;
            } else {
                // 否则使用方法名 + 参数值的 MD5
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                String argsString = Arrays.stream(joinPoint.getArgs())
                        .map(arg -> arg != null ? arg.toString() : "null")
                        .collect(Collectors.joining(","));
                String key = methodName + ":" + argsString;
                uniqueKey = DigestUtils.md5DigestAsHex(key.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            // 非 Web 环境
            String methodName = method.getDeclaringClass().getName() + "." + method.getName();
            String argsString = Arrays.stream(joinPoint.getArgs())
                    .map(arg -> arg != null ? arg.toString() : "null")
                    .collect(Collectors.joining(","));
            String key = methodName + ":" + argsString;
            uniqueKey = DigestUtils.md5DigestAsHex(key.getBytes(StandardCharsets.UTF_8));
        }
        
        return IdempotentKey.of("http", getScope(method), uniqueKey);
    }
    
    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
