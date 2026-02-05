package com.tiny.platform.infrastructure.core.exception.base;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * 基础异常处理器
 *
 * <p>提供通用的异常处理逻辑，使用 Spring Boot 3 的 {@link ProblemDetail} 作为统一异常响应格式。</p>
 *
 * <p><strong>注意：</strong>此类是抽象基类，不应该有 {@code @RestControllerAdvice} 注解。
 * 子类（如 {@code OAuthServerExceptionHandler}）应该添加 {@code @RestControllerAdvice} 注解。</p>
 *
 * @author Tiny Platform
 * @since 1.0.0
 */
public abstract class BaseExceptionHandler {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

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
     * 处理业务异常
     *
     * @param ex 业务异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(@Nonnull BusinessException ex, @Nonnull NativeWebRequest request) {
        log.warn("业务异常: {}", ex.getMessage());

        ErrorCode errorCode = ex.getErrorCode();
        ProblemDetail body = buildProblemDetail(
                errorCode,
                ExceptionUtils.getExceptionDetail(ex),
                request
        );
        return ResponseEntity.of(body).build();
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
    public ResponseEntity<ProblemDetail> handleAuthentication(
            @Nonnull AuthenticationException ex, @Nonnull NativeWebRequest request) {

        log.warn("认证异常: {}", ex.getMessage());

        ProblemDetail body = buildProblemDetail(
                ErrorCode.UNAUTHORIZED,
                ExceptionUtils.getExceptionDetail(ex),
                request
        );
        return ResponseEntity.of(body).build();
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
    public final ResponseEntity<ProblemDetail> handleRuntimeException(
            @Nonnull RuntimeException ex, @Nonnull NativeWebRequest request) {

        // 如果不是业务异常，使用默认处理
        log.error("运行时异常: {}", ex.getMessage(), ex);

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        String detail = ExceptionUtils.isBusinessException(ex)
            ? ExceptionUtils.getExceptionDetail(ex)
            : "操作失败: " + ExceptionUtils.getExceptionDetail(ex);

        ProblemDetail body = buildProblemDetail(
                errorCode,
                detail,
                request
        );
        return ResponseEntity.of(body).build();
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
    public final ResponseEntity<ProblemDetail> handleException(@Nonnull Exception ex, @Nonnull NativeWebRequest request) {

        log.error("系统异常: {}", ex.getMessage(), ex);

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        ProblemDetail body = buildProblemDetail(
                errorCode,
                "服务器内部错误: " + ExceptionUtils.getExceptionDetail(ex),
                request
        );
        return ResponseEntity.of(body).build();
    }

    /**
     * 构建 ProblemDetail（便捷方法，子类可以使用）
     *
     * @param errorCode 错误码
     * @param detail 错误详情
     * @param typeSuffix Problem 类型后缀（兼容旧签名，不再使用具体 type）
     * @param request 请求对象（用于获取 instance URI）
     * @return Problem 对象
     */
    protected ProblemDetail buildProblemDetail(ErrorCode errorCode, String detail, String typeSuffix, @Nonnull NativeWebRequest request) {
        return buildProblemDetail(errorCode, detail, request);
    }

    /**
     * 构建 ProblemDetail（核心实现）
     */
    protected ProblemDetail buildProblemDetail(ErrorCode errorCode, String detail, @Nonnull NativeWebRequest request) {
        HttpServletRequest httpRequest = request.getNativeRequest(HttpServletRequest.class);
        ProblemDetail problemDetail = ProblemDetail.forStatus(errorCode.getStatus());
        problemDetail.setTitle(errorCode.getMessage());
        problemDetail.setDetail(detail);
        problemDetail.setProperty("code", errorCode.getCode());

        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            problemDetail.setInstance(instanceUri);
        }
        if (httpRequest != null) {
            problemDetail.setProperty("path", httpRequest.getRequestURI());
        }
        return problemDetail;
    }
}

