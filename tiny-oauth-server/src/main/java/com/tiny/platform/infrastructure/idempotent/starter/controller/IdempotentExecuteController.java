package com.tiny.platform.infrastructure.idempotent.starter.controller;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import com.tiny.platform.infrastructure.core.exception.util.ExceptionUtils;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 幂等性执行接口（轻量模式）
 * 
 * <p>提供通用的幂等执行 HTTP 接口</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@RestController
@RequestMapping("/idempotent")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "tiny.idempotent.http-api", name = "enabled", havingValue = "true", matchIfMissing = false)
public class IdempotentExecuteController {
    
    private static final Logger log = LoggerFactory.getLogger(IdempotentExecuteController.class);
    
    private final IdempotentEngine idempotentEngine;
    private final IdempotentProperties properties;
    
    public IdempotentExecuteController(IdempotentEngine idempotentEngine, IdempotentProperties properties) {
        this.idempotentEngine = idempotentEngine;
        this.properties = properties;
    }
    
    /**
     * 幂等执行通用接口
     * 
     * <p>POST /idempotent/execute</p>
     * <p>接收 Key / BizContext / Payload，执行幂等保护的业务逻辑</p>
     * 
     * @param request 执行请求
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody ExecuteRequest request) {
        try {
            // 验证参数
            if (request.getKey() == null || request.getKey().isEmpty()) {
                ErrorResponse errorResponse = ErrorResponse.builder()
                    .code(ErrorCode.MISSING_PARAMETER.getCode())
                    .message(ErrorCode.MISSING_PARAMETER.getMessage())
                    .detail("key 不能为空")
                    .status(ErrorCode.MISSING_PARAMETER.getStatusValue())
                    .build();
                return ResponseEntity.status(ErrorCode.MISSING_PARAMETER.getStatus()).body(errorResponse);
            }
            
            // 解析 Key
            IdempotentKey key;
            try {
                key = IdempotentKey.parse(request.getKey());
            } catch (IllegalArgumentException e) {
                ErrorResponse errorResponse = ErrorResponse.builder()
                    .code(ErrorCode.INVALID_PARAMETER.getCode())
                    .message(ErrorCode.INVALID_PARAMETER.getMessage())
                    .detail("无效的 key 格式: " + e.getMessage())
                    .status(ErrorCode.INVALID_PARAMETER.getStatusValue())
                    .build();
                return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getStatus()).body(errorResponse);
            }
            
            // 构建策略
            long ttl = request.getTtl() != null ? request.getTtl() : properties.getTtl();
            boolean failOpen = request.getFailOpen() != null ? request.getFailOpen() : properties.isFailOpen();
            IdempotentStrategy strategy = new IdempotentStrategy(ttl, failOpen);
            
            // 构建上下文
            IdempotentContext context = new IdempotentContext(key, strategy);
            
            // 执行幂等性保护的业务逻辑
            // 注意：这里只是示例，实际业务逻辑应该由调用方在 payload 中指定或通过回调方式执行
            Object result;
            try {
                result = idempotentEngine.execute(context, () -> {
                    // 这里应该调用实际的业务逻辑
                    // 由于是通用接口，这里返回 payload 作为示例
                    return request.getPayload() != null ? request.getPayload() : Map.of("message", "执行成功");
                });
            } catch (Throwable e) {
                // 如果是 IdempotentException，会在外层 catch 处理
                if (e instanceof IdempotentException) {
                    throw (IdempotentException) e;
                }
                // 其他异常转换为 RuntimeException
                throw new RuntimeException(e);
            }
            
            // 成功响应（使用 Map 格式保持向后兼容）
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            response.put("key", key.getFullKey());
            return ResponseEntity.ok(response);
            
        } catch (IdempotentException e) {
            log.warn("幂等性检查失败: {}", e.getMessage());
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.IDEMPOTENT_CONFLICT.getCode())
                .message(ErrorCode.IDEMPOTENT_CONFLICT.getMessage())
                .detail(ExceptionUtils.getExceptionDetail(e))
                .status(ErrorCode.IDEMPOTENT_CONFLICT.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.IDEMPOTENT_CONFLICT.getStatus()).body(errorResponse);
        } catch (Exception e) {
            log.error("执行失败", e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getMessage())
                .detail(ExceptionUtils.getExceptionDetail(e))
                .status(ErrorCode.INTERNAL_ERROR.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(errorResponse);
        }
    }
    
    /**
     * 执行请求 DTO
     */
    public static class ExecuteRequest {
        private String key;
        private Long ttl;
        private Boolean failOpen;
        private Object payload;
        private Map<String, Object> bizContext;
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        public Long getTtl() {
            return ttl;
        }
        
        public void setTtl(Long ttl) {
            this.ttl = ttl;
        }
        
        public Boolean getFailOpen() {
            return failOpen;
        }
        
        public void setFailOpen(Boolean failOpen) {
            this.failOpen = failOpen;
        }
        
        public Object getPayload() {
            return payload;
        }
        
        public void setPayload(Object payload) {
            this.payload = payload;
        }
        
        public Map<String, Object> getBizContext() {
            return bizContext;
        }
        
        public void setBizContext(Map<String, Object> bizContext) {
            this.bizContext = bizContext;
        }
    }
}

