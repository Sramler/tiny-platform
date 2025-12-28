package com.tiny.platform.infrastructure.core.exception.base;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.security.core.AuthenticationException;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.ProblemHandling;
import org.zalando.problem.spring.web.advice.security.SecurityAdviceTrait;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * 基础异常处理器
 * 
 * <p>提供通用的异常处理逻辑，使用 RFC 7807 Problem 格式</p>
 * 
 * <p>实现 {@code ProblemHandling} 和 {@code SecurityAdviceTrait} 接口，遵循 problem-spring-web-starter 官方建议：</p>
 * <ul>
 *   <li>覆盖 {@code handleThrowable()} 方法作为主要异常处理入口</li>
 *   <li>使用配置化的 Problem 类型 URI（通过 {@code spring.problem.type} 配置）</li>
 *   <li>使用 {@code Problem.builder()} 和 {@code create()} 方法构建响应</li>
 *   <li>实现 {@code SecurityAdviceTrait} 以正确处理 Spring Security 认证异常（返回 401）</li>
 * </ul>
 * 
 * <p><strong>注意：</strong>此类是抽象基类，不应该有 {@code @RestControllerAdvice} 注解。
 * 子类（如 {@code OAuthServerExceptionHandler}）应该添加 {@code @RestControllerAdvice} 注解。</p>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public abstract class BaseExceptionHandler implements ProblemHandling, SecurityAdviceTrait {
    
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Problem Type 基础 URI（从配置文件读取，默认值：https://example.org/problems）
     * 
     * <p>配置方式：在 application.yaml 中设置 {@code spring.problem.type}</p>
     */
    @Value("${spring.problem.type:https://example.org/problems}")
    protected String problemTypeBase;
    
    /**
     * 获取 Problem Type URI
     * 
     * @param suffix 类型后缀（如 "validation-error"）
     * @return 完整的 Problem Type URI
     */
    protected URI getProblemType(String suffix) {
        return URI.create(problemTypeBase + "/" + suffix);
    }
    
    /**
     * 从请求中获取 instance URI（用于 RFC 7807 Problem 格式的 instance 字段）
     * 
     * @param request 请求对象
     * @return 请求路径的 URI，如果无法获取则返回 null
     */
    protected URI getInstanceUri(@Nonnull NativeWebRequest request) {
        try {
            HttpServletRequest httpRequest = request.getNativeRequest(HttpServletRequest.class);
            if (httpRequest != null) {
                String path = httpRequest.getRequestURI();
                // 确保路径以 / 开头
                if (path != null && !path.isEmpty()) {
                    return URI.create(path);
                }
            }
        } catch (Exception e) {
            log.debug("无法获取请求路径用于 instance 字段: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 覆盖 handleThrowable 方法（官方建议的方式）
     * 
     * <p>这是 problem-spring-web-starter 官方推荐的主要异常处理入口。
     * 通过覆盖此方法，可以统一处理所有异常，然后根据异常类型分发到具体的处理方法。</p>
     * 
     * <p>注意：如果 ProblemHandling 接口没有 handleThrowable 方法，此方法将不会被调用。
     * 异常处理将通过 @ExceptionHandler 注解的方法进行。</p>
     * 
     * @param throwable 异常
     * @param request 请求
     * @return 错误响应
     */
    public ResponseEntity<Problem> handleThrowable(@Nonnull Throwable throwable, @Nonnull NativeWebRequest request) {
        // 根据异常类型分发到具体的处理方法
        // 注意：
        // 1. MethodArgumentNotValidException 由 problem-spring-web-starter 的默认实现处理
        // 2. AuthenticationException 由 handleAuthentication 方法处理（@ExceptionHandler 优先级更高）
        if (throwable instanceof BusinessException) {
            return handleBusinessException((BusinessException) throwable, request);
        }
        // 排除 AuthenticationException，因为它由专门的 handleAuthentication 方法处理
        if (throwable instanceof RuntimeException && !(throwable instanceof AuthenticationException)) {
            return handleRuntimeException((RuntimeException) throwable, request);
        }
        if (throwable instanceof Exception && !(throwable instanceof AuthenticationException)) {
            return handleException((Exception) throwable, request);
        }
        // 其他类型的异常，使用默认处理（创建通用 Problem）
        log.error("未知异常类型: {}", throwable.getClass().getName(), throwable);
        Problem problem = buildProblem(
            ErrorCode.INTERNAL_ERROR,
            "服务器内部错误: " + ExceptionUtils.getExceptionDetail(throwable),
            "unknown-error",
            request
        );
        return create(throwable, problem, request);
    }
    
    /**
     * 处理业务异常
     * 
     * @param ex 业务异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Problem> handleBusinessException(@Nonnull BusinessException ex, @Nonnull NativeWebRequest request) {
        log.warn("业务异常: {}", ex.getMessage());
        
        ErrorCode errorCode = ex.getErrorCode();
        var builder = Problem.builder()
            .withType(getProblemType("business-error"))
            .withTitle(errorCode.getMessage())
            .withStatus(Status.valueOf(errorCode.getStatusValue()))
            .withDetail(ExceptionUtils.getExceptionDetail(ex))
            .with("code", errorCode.getCode());
        
        // 添加 instance 字段（RFC 7807）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        Problem problem = builder.build();
        return create(ex, problem, request);
    }
    
    /**
     * 处理 Spring Security 认证异常
     * 
     * <p>覆盖 SecurityAdviceTrait 的默认实现，使用统一的错误码格式（40101）</p>
     * 
     * @param ex 认证异常
     * @param request 请求
     * @return 错误响应（401 UNAUTHORIZED）
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> handleAuthentication(
            @Nonnull AuthenticationException ex, @Nonnull NativeWebRequest request) {
        
        log.warn("认证异常: {}", ex.getMessage());
        
        var builder = Problem.builder()
            .withType(getProblemType("authentication-error"))
            .withTitle(ErrorCode.UNAUTHORIZED.getMessage())
            .withStatus(Status.valueOf(ErrorCode.UNAUTHORIZED.getStatusValue()))
            .withDetail(ExceptionUtils.getExceptionDetail(ex))
            .with("code", ErrorCode.UNAUTHORIZED.getCode());
        
        // 添加 instance 字段（RFC 7807）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        Problem problem = builder.build();
        return create(ex, problem, request);
    }
    
    /**
     * 处理运行时异常（final，不允许子类覆盖）
     * 
     * <p>注意：</p>
     * <ul>
     *   <li>子类可以通过 @ExceptionHandler 处理特定的异常类型，这些特定处理器会优先匹配</li>
     *   <li>Spring Security 的认证异常（AuthenticationException）由 handleAuthentication 方法处理，不会被此方法捕获</li>
     * </ul>
     * 
     * @param ex 运行时异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public final ResponseEntity<Problem> handleRuntimeException(
            @Nonnull RuntimeException ex, @Nonnull NativeWebRequest request) {
        
        // 如果不是业务异常，使用默认处理
        log.error("运行时异常: {}", ex.getMessage(), ex);
        
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        String detail = ExceptionUtils.isBusinessException(ex) 
            ? ExceptionUtils.getExceptionDetail(ex)
            : "操作失败: " + ExceptionUtils.getExceptionDetail(ex);
        
        var builder = Problem.builder()
            .withType(getProblemType("runtime-error"))
            .withTitle(errorCode.getMessage())
            .withStatus(Status.valueOf(errorCode.getStatusValue()))
            .withDetail(detail)
            .with("code", errorCode.getCode());
        
        // 添加 instance 字段（RFC 7807）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        Problem problem = builder.build();
        return create(ex, problem, request);
    }
    
    /**
     * 处理通用异常（final，不允许子类覆盖）
     * 
     * <p>注意：子类可以通过 @ExceptionHandler 处理特定的异常类型，
     * 这些特定处理器会优先匹配</p>
     * 
     * @param ex 异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<Problem> handleException(@Nonnull Exception ex, @Nonnull NativeWebRequest request) {
        
        log.error("系统异常: {}", ex.getMessage(), ex);
        
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        var builder = Problem.builder()
            .withType(getProblemType("internal-error"))
            .withTitle(errorCode.getMessage())
            .withStatus(Status.valueOf(errorCode.getStatusValue()))
            .withDetail("服务器内部错误: " + ExceptionUtils.getExceptionDetail(ex))
            .with("code", errorCode.getCode());
        
        // 添加 instance 字段（RFC 7807）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        Problem problem = builder.build();
        return create(ex, problem, request);
    }
    
    /**
     * 构建 Problem（便捷方法，子类可以使用）
     * 
     * @param errorCode 错误码
     * @param detail 错误详情
     * @param typeSuffix Problem Type 后缀（如 "validation-error"）
     * @param request 请求对象（用于获取 instance URI）
     * @return Problem 对象
     */
    protected Problem buildProblem(ErrorCode errorCode, String detail, String typeSuffix, @Nonnull NativeWebRequest request) {
        var builder = Problem.builder()
            .withType(getProblemType(typeSuffix))
            .withTitle(errorCode.getMessage())
            .withStatus(Status.valueOf(errorCode.getStatusValue()))
            .withDetail(detail)
            .with("code", errorCode.getCode());
        
        // 添加 instance 字段（RFC 7807 可选字段，但建议包含）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        return builder.build();
    }
}

