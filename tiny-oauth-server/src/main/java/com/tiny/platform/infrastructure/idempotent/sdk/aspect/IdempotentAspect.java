package com.tiny.platform.infrastructure.idempotent.sdk.aspect;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.idempotent.console.IdempotentBlacklistChecker;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
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
    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 128;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");
    
    private final IdempotentEngine engine;
    private final List<IdempotentKeyResolver> keyResolvers;
    private final IdempotentMetricsService metricsService;
    private final IdempotentBlacklistChecker blacklistChecker;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    public IdempotentAspect(IdempotentEngine engine, List<IdempotentKeyResolver> keyResolvers) {
        this(engine, keyResolvers, new IdempotentMetricsService(null), null);
    }

    public IdempotentAspect(IdempotentEngine engine, List<IdempotentKeyResolver> keyResolvers,
                            IdempotentMetricsService metricsService) {
        this(engine, keyResolvers, metricsService, null);
    }

    public IdempotentAspect(IdempotentEngine engine, List<IdempotentKeyResolver> keyResolvers,
                            IdempotentMetricsService metricsService,
                            IdempotentBlacklistChecker blacklistChecker) {
        this.engine = engine;
        this.keyResolvers = keyResolvers != null ? keyResolvers : List.of();
        this.metricsService = metricsService != null ? metricsService : new IdempotentMetricsService(null);
        this.blacklistChecker = blacklistChecker;
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
        
        // 黑名单检查
        if (blacklistChecker != null && blacklistChecker.isBlacklisted(key.getFullKey())) {
            metricsService.recordValidationRejected("blacklist");
            throw new com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException("该幂等键已被加入黑名单");
        }
        
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
            if (StringUtils.hasText(uniqueKey)) {
                return buildHttpKey(method, uniqueKey);
            }
            log.debug("幂等性key表达式结果为空，回退默认策略: method={}, expression={}",
                method.toGenericString(), keyExpression);
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

    private IdempotentKey buildHttpKey(Method method, String uniqueKey) {
        String validatedKey = validateUniqueKey(uniqueKey);
        return IdempotentKey.of("http", getScope(method), validatedKey);
    }
    
    /**
     * 获取作用域（方法路径）
     */
    private String getScope(Method method) {
        HttpServletRequest request = getRequest();
        if (request != null && StringUtils.hasText(request.getMethod()) && StringUtils.hasText(request.getRequestURI())) {
            return withTenantScope(request.getMethod() + " " + request.getRequestURI(), request);
        }
        return withTenantScope(method.getDeclaringClass().getSimpleName() + "." + method.getName(), request);
    }

    private String withTenantScope(String baseScope, HttpServletRequest request) {
        String scope = baseScope;

        String currentUserId = resolveCurrentUserId();
        if (StringUtils.hasText(currentUserId)) {
            scope = currentUserId + "|" + scope;
        }

        Long activeTenantId = TenantContext.getActiveTenantId();
        if ((activeTenantId == null || activeTenantId <= 0) && request != null) {
            String activeTenantHeader = request.getHeader(com.tiny.platform.core.oauth.tenant.TenantContextContract.ACTIVE_TENANT_ID_HEADER);
            if (StringUtils.hasText(activeTenantHeader)) {
                try {
                    activeTenantId = Long.parseLong(activeTenantHeader);
                } catch (NumberFormatException e) {
                    log.debug("幂等性活动租户头格式非法，忽略: {}", activeTenantHeader);
                }
            }
        }
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId + "|" + scope;
        }
        return scope;
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
        
        return buildHttpKey(method, uniqueKey);
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

    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (!authentication.isAuthenticated() && !AuthenticationFactorAuthorities.hasAnyFactor(authentication)) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            if (securityUser.getUserId() != null) {
                return securityUser.getUserId().toString();
            }
            return StringUtils.hasText(securityUser.getUsername()) ? securityUser.getUsername() : null;
        }
        if (principal instanceof UserDetails userDetails) {
            return StringUtils.hasText(userDetails.getUsername()) ? userDetails.getUsername() : null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return extractUserId(jwtAuthenticationToken.getToken());
        }
        if (principal instanceof String principalName && !"anonymousUser".equalsIgnoreCase(principalName)
            && StringUtils.hasText(principalName)) {
            return principalName;
        }
        return null;
    }

    private String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String userId = jwt.getClaimAsString("user_id");
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("uid");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("username");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getSubject();
        }
        return StringUtils.hasText(userId) ? userId : null;
    }

    private String validateUniqueKey(String uniqueKey) {
        if (!StringUtils.hasText(uniqueKey)) {
            metricsService.recordValidationRejected("blank");
            throw BusinessException.validationError("幂等键不能为空");
        }
        if (!uniqueKey.equals(uniqueKey.trim())) {
            metricsService.recordValidationRejected("surrounding_whitespace");
            throw BusinessException.validationError("幂等键不能包含前后空白字符");
        }
        if (uniqueKey.length() < MIN_KEY_LENGTH || uniqueKey.length() > MAX_KEY_LENGTH) {
            metricsService.recordValidationRejected("length");
            throw BusinessException.validationError(
                String.format("幂等键长度必须在 %d 到 %d 个字符之间", MIN_KEY_LENGTH, MAX_KEY_LENGTH)
            );
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(uniqueKey).matches()) {
            metricsService.recordValidationRejected("format");
            throw BusinessException.validationError("幂等键只允许字母、数字、点、短横线、下划线和冒号");
        }
        return uniqueKey;
    }
}
