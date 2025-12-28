package com.tiny.platform.infrastructure.core.exception.code;

import org.springframework.http.HttpStatus;

/**
 * 通用错误码枚举
 * 
 * <p>错误码设计规范：使用 HTTP 状态码前缀</p>
 * <p>格式：HTTP状态码 + 2位序号 = 5位错误码</p>
 * <p>示例：</p>
 * <ul>
 *   <li>40001 = HTTP 400 + 序号 01（参数校验失败）</li>
 *   <li>40401 = HTTP 404 + 序号 01（资源不存在）</li>
 *   <li>40901 = HTTP 409 + 序号 01（幂等性冲突）</li>
 * </ul>
 * 
 * <p>优点：</p>
 * <ul>
 *   <li>从错误码直接看出对应的 HTTP 状态码</li>
 *   <li>符合 REST API 设计最佳实践</li>
 *   <li>便于调试和日志分析</li>
 *   <li>前端可以根据错误码前缀快速判断错误类型</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 在业务代码中抛出异常
 * throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
 * 
 * // 在异常处理器中使用
 * Problem problem = Problem.builder()
 *     .withStatus(Status.valueOf(ErrorCode.NOT_FOUND.getStatusValue()))
 *     .with("code", ErrorCode.NOT_FOUND.getCode())
 *     .build();
 * </pre>
 * 
 * <p>各项目可以扩展自己的错误码，遵循相同的设计规范</p>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public enum ErrorCode {
    
    // ==================== 成功 (200) ====================
    SUCCESS(20000, "操作成功", HttpStatus.OK),
    
    // ==================== 客户端错误 (400-499) ====================
    // --- 参数错误 (400) ---
    VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(40002, "缺少参数", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(40003, "无效的参数", HttpStatus.BAD_REQUEST),
    // --- 资源错误 (404) ---
    NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),
    // --- 方法错误 (405) ---
    METHOD_NOT_SUPPORTED(40501, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    // --- 媒体类型错误 (415) ---
    MEDIA_TYPE_NOT_SUPPORTED(41501, "媒体类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    // --- 业务验证错误 (422) ---
    UNPROCESSABLE_ENTITY(42201, "请求格式正确，但语义错误", HttpStatus.UNPROCESSABLE_ENTITY),
    
    // ==================== 认证错误 (401) ====================
    UNAUTHORIZED(40101, "未授权", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "令牌已过期", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(40103, "无效的令牌", HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING(40104, "缺少令牌", HttpStatus.UNAUTHORIZED),
    
    // ==================== 权限错误 (403) ====================
    ACCESS_DENIED(40301, "拒绝访问", HttpStatus.FORBIDDEN),
    FORBIDDEN(40302, "没有权限", HttpStatus.FORBIDDEN),
    
    // ==================== 业务错误 (409) ====================
    IDEMPOTENT_CONFLICT(40901, "请勿重复提交", HttpStatus.CONFLICT),
    BUSINESS_ERROR(40902, "业务处理失败", HttpStatus.CONFLICT),
    RESOURCE_CONFLICT(40903, "资源冲突", HttpStatus.CONFLICT),  // 通用资源冲突：资源有子资源、被引用、循环引用等
    RESOURCE_ALREADY_EXISTS(40904, "资源已存在", HttpStatus.CONFLICT),  // 数据唯一性冲突：名称、URL、URI等已存在
    RESOURCE_STATE_INVALID(40905, "资源状态不允许此操作", HttpStatus.CONFLICT),  // 资源状态不允许更新/删除等操作
    
    // ==================== 服务器错误 (500-599) ====================
    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    // --- 网关错误 (502, 504) ---
    BAD_GATEWAY(50201, "网关错误", HttpStatus.BAD_GATEWAY),
    GATEWAY_TIMEOUT(50401, "网关超时", HttpStatus.GATEWAY_TIMEOUT),
    // --- 服务不可用 (503) ---
    SERVICE_UNAVAILABLE(50301, "服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
    // --- 其他 (429) ---
    TOO_MANY_REQUESTS(42901, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    // --- 未知错误 ---
    UNKNOWN_ERROR(50099, "未知错误", HttpStatus.INTERNAL_SERVER_ERROR);
    
    private final int code;
    private final String message;
    private final HttpStatus status;
    
    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public HttpStatus getStatus() {
        return status;
    }
    
    public int getStatusValue() {
        return status.value();
    }
}

