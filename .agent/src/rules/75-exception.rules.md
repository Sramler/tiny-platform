# 75 异常处理规范

## 适用范围

- 适用于：`**/exception/**`、`**/*Exception*.java`、`**/handler/**`、异常处理相关代码
- 不适用于：框架异常（Spring Security、Spring Data 等由框架处理）

## 总体策略

1. **统一异常处理**：Controller 不捕获业务异常，由 `GlobalExceptionHandler` 统一处理。
2. **异常分类**：区分业务异常、系统异常、参数校验异常。
3. **异常信息脱敏**：不暴露内部堆栈和敏感信息。

---

## 禁止（Must Not）

### 1) 异常处理方式

- ❌ Controller 层捕获业务异常并手动返回错误响应（应抛出异常，由 GlobalExceptionHandler 统一处理）。
- ❌ Service 层返回 `Map.of("success", false, "error", "...")`（应抛出异常）。
- ❌ 吞掉异常不记录日志（必须记录异常日志）。

### 2) 异常信息

- ❌ 异常信息中包含敏感数据（密码、密钥、完整 SQL、完整堆栈）。
- ❌ 向用户暴露内部堆栈信息（应返回友好的错误消息）。

### 3) 异常使用

- ❌ 使用异常做流程控制（应使用条件判断）。
- ❌ 捕获过宽异常（如 `catch (Exception e)`）而不处理。

---

## 必须（Must）

### 1) 异常分类

- ✅ 异常分类：`BusinessException`（业务异常）、`ValidationException`（参数校验异常）、`SystemException`（系统异常）。
- ✅ 异常码规范：使用 `ResponseCode` 枚举，不硬编码错误码。

### 2) 异常处理层次

- ✅ 统一异常响应：使用 `ErrorResponse` 格式（code, message, detail, status, path, timestamp）。
- ✅ 异常处理层次：Controller 不捕获业务异常，Service 抛出异常，`GlobalExceptionHandler` 统一处理。
- ✅ 异常信息脱敏：日志和响应中不包含敏感数据（密码、密钥、完整 SQL）。

### 3) 异常链

- ✅ 异常链：保留原始异常（`cause`），便于排查问题。
- ✅ 异常转换：第三方异常（如数据库异常）转换为业务异常，避免泄漏技术细节。

---

## 应该（Should）

### 1) 异常日志

- ⚠️ 异常日志：记录异常级别（ERROR/WARN）、异常信息、上下文（用户ID、请求路径、参数摘要）。
- ⚠️ 日志分级：业务异常使用 WARN，系统异常使用 ERROR。

### 2) 参数校验

- ⚠️ 参数校验：使用 `@Valid` 和 Bean Validation，校验失败抛出 `MethodArgumentNotValidException`。
- ⚠️ 校验消息：提供清晰的校验错误消息，便于前端展示。

### 3) 异常监控

- ⚠️ 异常监控：集成异常监控系统（如 Sentry），自动上报异常。
- ⚠️ 异常告警：关键异常（如系统异常、安全异常）触发告警。

---

## 可以（May）

- 💡 异常重试：可重试异常（如网络超时）支持自动重试机制。
- 💡 异常降级：非关键异常支持降级处理（返回默认值或缓存数据）。

---

## 例外与裁决

### OAuth2 异常

- OAuth2 异常：由 `OAuth2ExceptionHandler` 处理，遵循 OAuth2 规范。

### 框架异常

- 框架异常：Spring Security、Spring Data 等框架异常由框架处理。

### 冲突裁决

- 安全规范（40-security）优先于本规范。
- 异常处理与业务规范冲突时，优先保证异常信息不泄漏敏感数据。

---

## 示例

### ✅ 正例：Service 层抛出异常

```java
@Service
public class UserService {
    public UserDTO getById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ResponseCode.USER_NOT_FOUND, 
                "用户不存在: " + id
            ));
        return convertToDTO(user);
    }
}
```

### ✅ 正例：GlobalExceptionHandler 统一处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.warn("Business exception: code={}, message={}, path={}", 
            e.getCode(), e.getMessage(), request.getRequestURI(), e);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(e.getCode())
            .message(e.getMessage())
            .detail(e.getDetail())
            .status(e.getStatus().value())
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(e.getStatus()).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> errors = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ResponseCode.VALIDATION_ERROR.getCode())
            .message("参数校验失败")
            .detail(String.join(", ", errors))
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleSystemException(
            Exception e, HttpServletRequest request) {
        log.error("System exception: path={}", request.getRequestURI(), e);
        
        ErrorResponse response = ErrorResponse.builder()
            .code(ResponseCode.INTERNAL_ERROR.getCode())
            .message("系统内部错误，请联系管理员")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

### ❌ 反例：Controller 手动返回错误、Service 返回 Map

```java
// 错误：Controller 手动返回错误、Service 返回 Map
@RestController
public class UserController {
    @GetMapping("/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        Map<String, Object> result = userService.getUser(id);
        if (!(Boolean) result.get("success")) {
            return Map.of("success", false, "error", result.get("error"));
        }
        return result;
    }
}

@Service
public class UserService {
    public Map<String, Object> getUser(Long id) {
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return Map.of("success", false, "error", "用户不存在");
            }
            return Map.of("success", true, "data", user);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
```

### ✅ 正例：异常信息脱敏

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(
        Exception e, HttpServletRequest request) {
    // ✅ 不暴露内部堆栈
    log.error("Exception occurred: path={}, error={}", 
        request.getRequestURI(), e.getMessage(), e);
    
    ErrorResponse response = ErrorResponse.builder()
        .code("INTERNAL_ERROR")
        .message("系统内部错误，请联系管理员")
        // ❌ 不包含 stackTrace、内部路径等敏感信息
        .build();
    return ResponseEntity.status(500).body(response);
}
```

### ❌ 反例：暴露内部信息

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleException(Exception e) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", e.getMessage());
    response.put("stackTrace", Arrays.toString(e.getStackTrace())); // ❌ 暴露堆栈
    response.put("path", e.getClass().getName()); // ❌ 暴露内部路径
    return ResponseEntity.status(500).body(response);
}
```

### ✅ 正例：异常链保留

```java
try {
    processPayment(order);
} catch (PaymentException e) {
    log.error("Payment failed: orderId={}", order.getId(), e);
    throw new BusinessException(
        ResponseCode.PAYMENT_FAILED, 
        "支付失败: " + e.getMessage(),
        e // ✅ 保留原始异常
    );
}
```

### ✅ 正例：参数校验

```java
@PostMapping("/users")
public ResponseEntity<GlobalResponse<UserDTO>> createUser(
        @Valid @RequestBody CreateUserRequest request) {
    // @Valid 自动校验，失败时抛出 MethodArgumentNotValidException
    UserDTO user = userService.createUser(request);
    return ResponseUtil.ok(user);
}

public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 字符")
    private String username;
    
    @Email(message = "邮箱格式不正确")
    private String email;
}
```
