package com.tiny.platform.infrastructure.idempotent.starter.controller;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import com.tiny.platform.infrastructure.core.exception.util.ExceptionUtils;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 幂等性 Token 管理接口（轻量模式）
 * 
 * <p>提供获取、验证、释放幂等 Token 的 HTTP 接口</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@RestController
@RequestMapping("/idempotent")
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "tiny.idempotent.http-api", name = "enabled", havingValue = "true", matchIfMissing = false)
public class IdempotentTokenController {
    
    private static final Logger log = LoggerFactory.getLogger(IdempotentTokenController.class);
    
    private final IdempotentEngine idempotentEngine;
    private final IdempotentProperties properties;
    
    public IdempotentTokenController(IdempotentEngine idempotentEngine, IdempotentProperties properties) {
        this.idempotentEngine = idempotentEngine;
        this.properties = properties;
    }
    
    /**
     * 获取一次性幂等 Token
     * 
     * <p>GET /idempotent/token</p>
     * <p>POST /idempotent/token</p>
     * 
     * @param scope 作用域（可选，如：业务场景标识）
     * @return 幂等 Token（Key）
     */
    @GetMapping("/token")
    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestParam(required = false) String scope) {
        try {
            // 生成唯一 Token
            String uniqueKey = UUID.randomUUID().toString().replace("-", "");
            String tokenScope = scope != null ? scope : "default";
            
            // 构建 Key
            IdempotentKey key = IdempotentKey.of("http", tokenScope, uniqueKey);
            
            // 尝试获取 Token（设置 PENDING 状态）
            long ttl = properties.getTtl();
            boolean failOpen = properties.isFailOpen();
            
            IdempotentContext context = idempotentEngine.process(key, ttl, failOpen);
            
            // 成功响应（使用 Map 格式保持向后兼容）
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", key.getFullKey());
            response.put("ttl", ttl);
            response.put("expireAt", context.getExpireAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取幂等 Token 失败", e);
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
     * 验证 Token 是否可用（预检）
     * 
     * <p>POST /idempotent/validate</p>
     * 
     * @param request 验证请求（包含 token）
     * @return 验证结果
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.MISSING_PARAMETER.getCode())
                .message(ErrorCode.MISSING_PARAMETER.getMessage())
                .detail("token 不能为空")
                .status(ErrorCode.MISSING_PARAMETER.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.MISSING_PARAMETER.getStatus()).body(errorResponse);
        }
        
        try {
            IdempotentKey key = IdempotentKey.parse(token);
            boolean exists = idempotentEngine.exists(key);
            
            // 成功响应（使用 Map 格式保持向后兼容）
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("valid", exists);
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Token 格式无效: token={}, error={}", token, e.getMessage());
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message(ErrorCode.INVALID_PARAMETER.getMessage())
                .detail("无效的 token 格式: " + e.getMessage())
                .status(ErrorCode.INVALID_PARAMETER.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getStatus()).body(errorResponse);
        } catch (Exception e) {
            log.error("验证 Token 失败: token={}", token, e);
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
     * 主动释放 Token（避免长期占用）
     * 
     * <p>POST /idempotent/release</p>
     * 
     * @param request 释放请求（包含 token）
     * @return 释放结果
     */
    @PostMapping("/release")
    public ResponseEntity<?> releaseToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.MISSING_PARAMETER.getCode())
                .message(ErrorCode.MISSING_PARAMETER.getMessage())
                .detail("token 不能为空")
                .status(ErrorCode.MISSING_PARAMETER.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.MISSING_PARAMETER.getStatus()).body(errorResponse);
        }
        
        try {
            IdempotentKey key = IdempotentKey.parse(token);
            idempotentEngine.delete(key);
            
            // 成功响应（使用 Map 格式保持向后兼容）
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Token 已释放");
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Token 格式无效: token={}, error={}", token, e.getMessage());
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message(ErrorCode.INVALID_PARAMETER.getMessage())
                .detail("无效的 token 格式: " + e.getMessage())
                .status(ErrorCode.INVALID_PARAMETER.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getStatus()).body(errorResponse);
        } catch (Exception e) {
            log.error("释放 Token 失败: token={}", token, e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getMessage())
                .detail(ExceptionUtils.getExceptionDetail(e))
                .status(ErrorCode.INTERNAL_ERROR.getStatusValue())
                .build();
            return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(errorResponse);
        }
    }
}

