package com.tiny.platform.infrastructure.core.exception.util;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 响应工具类
 * 
 * <p><strong>注意：</strong>此工具类主要用于兼容旧代码或特定场景（如 idempotent 模块的 HTTP 接口）。
 * 新代码应优先使用 RFC 7807 Problem 格式（通过 {@link com.tiny.platform.infrastructure.core.exception.base.BaseExceptionHandler}）。</p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>idempotent 模块的 HTTP 接口（待迁移到 Problem 格式）</li>
 *   <li>需要向后兼容的旧接口</li>
 *   <li>非 REST API 的场景（如内部服务调用）</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 返回 404 错误
 * return ResponseUtils.notFound("用户");
 * 
 * // 返回 400 错误
 * return ResponseUtils.badRequest("参数验证失败");
 * 
 * // 返回 401 错误
 * return ResponseUtils.unauthorized("未登录");
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public class ResponseUtils {
    
    /**
     * 构建错误响应
     * 
     * @param errorCode 错误码
     * @param detail 错误详情
     * @param request HTTP 请求（可选，用于获取路径）
     * @return 错误响应
     */
    public static ResponseEntity<ErrorResponse> error(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .detail(detail)
            .status(errorCode.getStatusValue())
            .path(request != null ? request.getRequestURI() : null)
            .build();
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }
    
    /**
     * 构建错误响应（不包含路径）
     */
    public static ResponseEntity<ErrorResponse> error(ErrorCode errorCode, String detail) {
        return error(errorCode, detail, null);
    }
    
    /**
     * 构建错误响应（使用异常消息作为详情）
     */
    public static ResponseEntity<ErrorResponse> error(ErrorCode errorCode, Exception ex, HttpServletRequest request) {
        return error(errorCode, ExceptionUtils.getExceptionDetail(ex), request);
    }
    
    /**
     * 未授权错误（401）
     */
    public static ResponseEntity<ErrorResponse> unauthorized(String detail) {
        return error(ErrorCode.UNAUTHORIZED, detail);
    }
    
    /**
     * 未授权错误（401）- 未登录
     */
    public static ResponseEntity<ErrorResponse> unauthorized() {
        return unauthorized("未登录");
    }
    
    /**
     * 参数验证错误（400）
     */
    public static ResponseEntity<ErrorResponse> badRequest(String detail) {
        return error(ErrorCode.VALIDATION_ERROR, detail);
    }
    
    /**
     * 缺少参数错误（400）
     */
    public static ResponseEntity<ErrorResponse> missingParameter(String parameterName) {
        return error(ErrorCode.MISSING_PARAMETER, "缺少参数: " + parameterName);
    }
    
    /**
     * 无效参数错误（400）
     */
    public static ResponseEntity<ErrorResponse> invalidParameter(String detail) {
        return error(ErrorCode.INVALID_PARAMETER, detail);
    }
    
    /**
     * 资源不存在错误（404）
     */
    public static ResponseEntity<ErrorResponse> notFound(String resourceName) {
        return error(ErrorCode.NOT_FOUND, resourceName + " 不存在");
    }
    
    /**
     * 权限不足错误（403）
     */
    public static ResponseEntity<ErrorResponse> forbidden(String detail) {
        return error(ErrorCode.FORBIDDEN, detail);
    }
    
    /**
     * 业务错误（409）
     */
    public static ResponseEntity<ErrorResponse> businessError(String detail) {
        return error(ErrorCode.BUSINESS_ERROR, detail);
    }
    
    /**
     * 服务器内部错误（500）
     */
    public static ResponseEntity<ErrorResponse> internalError(String detail) {
        return error(ErrorCode.INTERNAL_ERROR, detail);
    }
    
    /**
     * 服务器内部错误（500）- 使用异常
     */
    public static ResponseEntity<ErrorResponse> internalError(Exception ex) {
        return error(ErrorCode.INTERNAL_ERROR, ExceptionUtils.getExceptionDetail(ex));
    }
}

