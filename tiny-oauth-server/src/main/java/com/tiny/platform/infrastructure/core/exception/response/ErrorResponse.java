package com.tiny.platform.infrastructure.core.exception.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 统一错误响应格式
 * 
 * <p><strong>注意：</strong>此格式主要用于兼容旧代码或特定场景（如 idempotent 模块的 HTTP 接口）。
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
 * ErrorResponse response = ErrorResponse.builder()
 *     .code(ErrorCode.NOT_FOUND.getCode())
 *     .message(ErrorCode.NOT_FOUND.getMessage())
 *     .detail("用户不存在")
 *     .status(ErrorCode.NOT_FOUND.getStatusValue())
 *     .build();
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * 错误码
     */
    private int code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 错误详情（可选）
     */
    private String detail;
    
    /**
     * HTTP 状态码
     */
    private Integer status;
    
    /**
     * 请求路径（可选）
     */
    private String path;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 扩展字段（可选）
     */
    private Object data;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(int code, String message) {
        this();
        this.code = code;
        this.message = message;
    }
    
    public ErrorResponse(int code, String message, Integer status) {
        this(code, message);
        this.status = status;
    }
    
    // Getters and Setters
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public void setDetail(String detail) {
        this.detail = detail;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    /**
     * 创建 ErrorResponse 的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final ErrorResponse response = new ErrorResponse();
        
        public Builder code(int code) {
            response.setCode(code);
            return this;
        }
        
        public Builder message(String message) {
            response.setMessage(message);
            return this;
        }
        
        public Builder detail(String detail) {
            response.setDetail(detail);
            return this;
        }
        
        public Builder status(int status) {
            response.setStatus(status);
            return this;
        }
        
        public Builder path(String path) {
            response.setPath(path);
            return this;
        }
        
        public Builder data(Object data) {
            response.setData(data);
            return this;
        }
        
        public ErrorResponse build() {
            return response;
        }
    }
}

