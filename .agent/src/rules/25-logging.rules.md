# 25 日志规范

## 适用范围

- 适用于：`**/*.java`、`**/*.ts`、`**/*.vue`、日志相关代码
- 不适用于：第三方库日志（但使用时应遵循日志级别配置）

## 总体策略

1. **统一追踪标识**：所有 HTTP 请求从前端到后端、再到数据库/调度日志，都包含 traceId、requestId、userId、ip。
2. **完整闭环**：从前端发起、后端 Filter / Spring Security / 业务层 / DB 操作 / 审计日志，形成一条完整可追踪链路。
3. **运维友好**：通过一两个命令即可快速定位某个用户/请求的全链路日志。

---

## 禁止（Must Not）

### 1) 日志内容

- ❌ 日志中输出敏感信息：密码、密钥、Token、Cookie、私钥、完整证件号/银行卡号等。
- ❌ 使用 `printStackTrace()`；必须使用统一日志体系（便于 traceId/tenantId 关联）。
- ❌ 日志中输出完整堆栈信息（应记录关键信息，完整堆栈仅在 ERROR 级别且脱敏后记录）。

### 2) 日志框架

- ❌ 直接使用 `System.out.println`、`System.err.println`（必须使用日志框架）。
- ❌ 混用不同日志框架（统一使用 SLF4J + Logback/Log4j2）。

### 3) 日志级别

- ❌ 生产环境输出 DEBUG 日志（应使用 INFO 或更高级别）。
- ❌ 错误日志不包含异常对象（ERROR 级别必须携带 throwable）。

---

## 必须（Must）

### 1) TraceId 与 RequestId

- ✅ 所有 HTTP 请求必须包含 `traceId`（32 位十六进制）和 `requestId`（16 位十六进制）。
- ✅ 前端：通过 `request.ts` 统一注入 `X-Trace-Id` 和 `X-Request-Id` 请求头。
- ✅ 后端：通过 `HttpRequestLoggingFilter` 提取并设置到 MDC。
- ✅ MDC 字段：`traceId`、`requestId`、`userId`、`clientIp` 必须设置到 MDC。

### 2) 日志框架

- ✅ 日志统一使用 SLF4J 等门面接口，不直接使用具体日志框架。
- ✅ 后端使用 Logback 或 Log4j2，前端使用统一的 logger 工具。

### 3) 日志格式

- ✅ 日志格式统一：包含时间、级别、线程、Logger、traceId、requestId、userId、消息。
- ✅ 日志文件命名应包含应用名、日志类型、描述，有助于归类管理。
- ✅ 日志保留周期至少 15 天。

### 4) 日志级别

- ✅ 日志必须分级；error 必须携带 throwable。
- ✅ 关键链路建议统一打印：tenantId、traceId、userId（按项目日志规范）。

### 5) 日志脱敏

- ✅ 日志必须脱敏：密码、密钥、Token、完整证件号等敏感信息必须脱敏或省略。

---

## 应该（Should）

### 1) 日志级别使用

- ⚠️ 日志级别建议：
  - `DEBUG`：详细调试信息（仅开发环境）
  - `INFO`：一般信息、业务流程关键节点
  - `WARN`：警告信息、业务异常
  - `ERROR`：错误信息、系统异常（必须携带 throwable）

### 2) 日志内容

- ⚠️ 日志应包含上下文信息：用户ID、请求路径、参数摘要（不包含敏感信息）。
- ⚠️ 业务关键节点必须记录日志：登录、登出、重要操作、异常处理。

### 3) 前端日志

- ⚠️ 前端使用统一的 logger 工具，通过环境变量控制日志输出。
- ⚠️ 生产环境关闭 DEBUG 日志，只保留 ERROR 和 WARN。

### 4) 日志聚合

- ⚠️ 日志应支持集中收集（如 ELK、Loki），便于统一查询和分析。
- ⚠️ 日志应支持结构化输出（JSON 格式），便于解析和索引。

---

## 可以（May）

- 💡 使用日志采样：高频日志可采样记录，避免日志量过大。
- 💡 使用异步日志：高性能场景可使用异步日志，提升性能。

---

## 例外与裁决

### 第三方库日志

- 第三方库日志：可通过日志级别配置控制，不强制修改第三方库日志。

### 调试日志

- 开发环境：开发环境可输出 DEBUG 日志，但必须通过配置控制。
- 生产环境：生产环境必须关闭 DEBUG 日志。

### 冲突裁决

- 安全规范（40-security）优先于本规范（日志脱敏）。
- 平台特定规则（90+）优先于本规范。

---

## 示例

### ✅ 正例：后端日志（包含 TraceId、RequestId、UserId）

```java
@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    public UserDTO getUserById(Long id) {
        log.info("Get user by id: userId={}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found: userId={}", id);
                return new BusinessException(ResponseCode.USER_NOT_FOUND);
            });
        return convertToDTO(user);
    }
    
    public void updateUser(User user) {
        try {
            log.info("Update user: userId={}, username={}", user.getId(), maskUsername(user.getUsername()));
            userRepository.save(user);
            log.info("User updated successfully: userId={}", user.getId());
        } catch (Exception e) {
            log.error("Failed to update user: userId={}", user.getId(), e);
            throw new SystemException("UPDATE_USER_FAILED", e);
        }
    }
}
```

### ❌ 反例：日志中泄漏敏感信息、缺少 TraceId

```java
// 错误：日志中泄漏密码、缺少 TraceId
log.info("User login: username={}, password={}", username, password); // ❌ 泄漏密码

// 错误：使用 printStackTrace
catch (Exception e) {
    e.printStackTrace(); // ❌ 不使用日志框架
}

// 错误：缺少上下文信息
log.info("User updated"); // ❌ 缺少 userId、traceId 等上下文
```

### ✅ 正例：前端日志（使用 logger 工具）

```typescript
import logger from '@/utils/logger'

// 普通信息
logger.log('User logged in', { userId: user.id })

// 警告信息
logger.warn('API request failed', { url, status })

// 错误信息
logger.error('Failed to load data', error)

// 调试信息（仅开发环境）
logger.debug('Component mounted', { component: 'UserList' })
```

### ❌ 反例：前端直接使用 console

```typescript
// 错误：直接使用 console，无法控制输出
console.log('User logged in') // ❌ 生产环境无法关闭
console.error('Error:', error) // ❌ 应使用 logger.error
```

### ✅ 正例：日志格式（Logback）

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50}
  traceId=%X{traceId} requestId=%X{requestId} userId=%X{userId} ip=%X{clientIp} - %msg%n
</pattern>
```

### ✅ 正例：日志脱敏

```java
// 日志脱敏工具
public class LogMaskUtils {
    public static String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.substring(0, 1) + "***" + username.substring(username.length() - 1);
    }
    
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return "***@" + parts[1];
        }
        return parts[0].substring(0, 1) + "***@" + parts[1];
    }
}

// 使用
log.info("User login: userId={}, username={}, email={}", 
    userId, maskUsername(username), maskEmail(email));
```

### ✅ 正例：前端 TraceId 注入

```typescript
// request.ts
import axios from 'axios'
import { getOrCreateTraceId, generateRequestId } from '@/utils/traceId'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

// 请求拦截器：注入 TraceId
request.interceptors.request.use(config => {
  const traceId = getOrCreateTraceId()
  const requestId = generateRequestId()
  
  config.headers['X-Trace-Id'] = traceId
  config.headers['X-Request-Id'] = requestId
  
  return config
})

// 响应拦截器：记录 TraceId
request.interceptors.response.use(
  response => {
    const traceId = response.headers['x-trace-id']
    logger.debug('API response', { traceId, url: response.config.url })
    return response
  },
  error => {
    logger.error('API error', { error, url: error.config?.url })
    return Promise.reject(error)
  }
)
```

### ✅ 正例：后端 TraceId 提取（Filter）

```java
@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        // 提取 TraceId
        String traceId = resolveTraceId(request);
        String requestId = getOrCreateRequestId(request);
        String clientIp = IpUtils.getClientIp(request);
        
        // 设置到 MDC
        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理 MDC
            MDC.clear();
        }
    }
    
    private String resolveTraceId(HttpServletRequest request) {
        // 优先从请求头获取
        String traceId = request.getHeader("X-Trace-Id");
        if (StringUtils.hasText(traceId)) {
            return normalizeTraceId(traceId);
        }
        
        // 从查询参数获取（OIDC 重定向场景）
        traceId = request.getParameter("trace_id");
        if (StringUtils.hasText(traceId)) {
            return normalizeTraceId(traceId);
        }
        
        // Fallback：生成新的 TraceId
        return generateTraceId();
    }
}
```
