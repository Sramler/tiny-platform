# 日志与性能实践（Skill）

> 放长示例与实践模板；Rules 只保留红线与裁决。

## 日志实践模板

### 统一日志字段建议

- `traceId` / `requestId` / `tenantId` / `userId` / `clientIp` 为默认字段
- 网关与后端均需透传并写入 MDC

### 后端：结构化日志与上下文

```java
log.info("User login", kv("userId", userId), kv("traceId", traceId));
```

### 前端：统一 logger

```typescript
import logger from '@/utils/logger'

logger.log('User logged in', { userId: user.id })
logger.warn('API request failed', { url, status })
logger.error('Failed to load data', error)
```

### TraceId 注入（前端）

```typescript
import axios from 'axios'
import { getOrCreateTraceId, generateRequestId } from '@/utils/traceId'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

request.interceptors.request.use(config => {
  const traceId = getOrCreateTraceId()
  const requestId = generateRequestId()

  config.headers['X-Trace-Id'] = traceId
  config.headers['X-Request-Id'] = requestId

  return config
})
```

### TraceId 提取（后端 Filter）

```java
@Component
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        String requestId = getOrCreateRequestId(request);
        String clientIp = IpUtils.getClientIp(request);

        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 日志脱敏示例

```java
public class LogMaskUtils {
    public static String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.substring(0, 1) + "***" + username.substring(username.length() - 1);
    }
}
```

## 性能实践模板

### 性能监控指标建议

- 接口耗时 P95 / P99
- 错误率与超时率
- 慢 SQL 统计
- 缓存命中率

### 日志采样建议

- 高频接口建议采样记录 INFO 日志
- ERROR 日志不采样

### 避免 N+1

```java
// ✅ 批量查询
userRepository.findAllById(ids);

// ❌ 循环中查询
ids.forEach(id -> userRepository.findById(id));
```

### 分页查询

```java
Page<User> users = userRepository.findAll(pageable);
```

### 批量写入

```java
userRepository.saveAll(users);
```

### 前端懒加载

```typescript
const routes = [
  { path: '/user', component: () => import('@/views/UserView.vue') }
]
```

### 大列表虚拟滚动

```vue
<a-table :data-source="largeDataSet" :scroll="{ y: 400 }" :virtual="true" />
```
