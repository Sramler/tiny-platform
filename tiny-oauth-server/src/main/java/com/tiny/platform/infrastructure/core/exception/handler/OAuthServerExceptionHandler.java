package com.tiny.platform.infrastructure.core.exception.handler;

import com.tiny.platform.infrastructure.core.exception.base.BaseExceptionHandler;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
// TODO: 当 idempotent-starter 依赖可用时，取消注释以下 import 和方法
// import com.tiny.idempotent.exception.IdempotentException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import jakarta.annotation.Nonnull;
import java.net.URI;

/**
 * OAuth Server 异常处理器
 * 
 * <p>继承 {@link BaseExceptionHandler}，使用 RFC 7807 Problem 格式处理异常</p>
 * 
 * <p><strong>职责：</strong>只处理 OAuth Server 项目特定的异常，通用异常由 {@link BaseExceptionHandler} 处理</p>
 * 
 * <p><strong>处理的异常类型：</strong></p>
 * <ul>
 *   <li>{@link OAuth2AuthorizationException} - OAuth2 授权异常（problem-spring-web-starter 不会自动处理）</li>
 *   <li>{@link IdempotentException} - 幂等性异常（项目特定异常）</li>
 * </ul>
 * 
 * <p><strong>不处理的异常（由 BaseExceptionHandler 处理）：</strong></p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} - 参数验证异常</li>
 *   <li>{@link BusinessException} - 业务异常</li>
 *   <li>{@link RuntimeException} - 运行时异常</li>
 *   <li>{@link Exception} - 通用异常</li>
 * </ul>
 * 
 * <p>所有异常都会返回统一的 Problem 格式（RFC 7807）：</p>
 * <pre>
 * {
 *   "type": "https://example.org/problems/oauth2-error",
 *   "title": "未授权",
 *   "status": 401,
 *   "detail": "OAuth2 Error [invalid_token]: Token expired",
 *   "code": 40101,
 *   "oauth2ErrorCode": "invalid_token",
 *   "oauth2ErrorUri": "https://tools.ietf.org/html/rfc6750#section-3.1",
 *   "instance": "/oauth2/token"
 * }
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
@RestControllerAdvice
public class OAuthServerExceptionHandler extends BaseExceptionHandler {

    /**
     * 处理 OAuth2 授权异常
     * 
     * <p><strong>必要性：</strong>problem-spring-web-starter 不会自动处理 OAuth2AuthorizationException，
     * 需要手动处理以返回统一的 Problem 格式并提取 OAuth2Error 详细信息。</p>
     * 
     * <p>SecurityAdviceTrait 只处理：</p>
     * <ul>
     *   <li>AccessDeniedException (403)</li>
     *   <li>AuthenticationException (401)</li>
     *   <li>但不包括 OAuth2AuthorizationException</li>
     * </ul>
     * 
     * @param ex OAuth2 授权异常
     * @param request 请求
     * @return 错误响应
     */
    @ExceptionHandler(OAuth2AuthorizationException.class)
    public ResponseEntity<Problem> handleOAuth2AuthorizationException(
            @Nonnull OAuth2AuthorizationException ex, @Nonnull NativeWebRequest request) {
        
        OAuth2Error error = ex.getError();
        String errorCode = error.getErrorCode();
        String description = error.getDescription();
        
        log.error("OAuth2 授权错误: code={}, description={}, uri={}", 
                 errorCode, description, error.getUri(), ex);
        
        String detail = String.format("OAuth2 Error [%s]: %s", errorCode, description);
        
        var builder = Problem.builder()
            .withType(getProblemType("oauth2-error"))
            .withTitle(ErrorCode.UNAUTHORIZED.getMessage())
            .withStatus(Status.valueOf(ErrorCode.UNAUTHORIZED.getStatusValue()))
            .withDetail(detail)
            .with("code", ErrorCode.UNAUTHORIZED.getCode())
            .with("oauth2ErrorCode", errorCode)
            .with("oauth2ErrorUri", error.getUri() != null ? error.getUri().toString() : null);
        
        // 添加 instance 字段（RFC 7807）
        URI instanceUri = getInstanceUri(request);
        if (instanceUri != null) {
            builder.withInstance(instanceUri);
        }
        
        Problem problem = builder.build();
        return create(ex, problem, request);
    }

    /**
     * 处理幂等性异常（项目特定异常）
     * 
     * <p><strong>必要性：</strong>IdempotentException 是项目特定异常，
     * problem-spring-web-starter 无法自动处理。</p>
     * 
     * <p>注意：当前 idempotent 模块已合并到 tiny-oauth-server 内部，
     * 当 IdempotentException 可用时，取消注释此方法。</p>
     */
    /*
    @ExceptionHandler(IdempotentException.class)
    public ResponseEntity<Problem> handleIdempotentException(
            @Nonnull IdempotentException ex, @Nonnull NativeWebRequest request) {
        log.warn("幂等性检查失败: {}", ex.getMessage());
        
        Problem problem = buildProblem(
            ErrorCode.IDEMPOTENT_CONFLICT,
            ex.getMessage(),
            "idempotent-conflict"
        );
        
        return create(ex, problem, request);
    }
    */
}

