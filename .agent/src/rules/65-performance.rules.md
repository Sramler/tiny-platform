# 65 性能优化规范

## 适用范围

- 适用于：`**/*.java`、`**/*.ts`、`**/*.vue`、性能相关代码
- 不适用于：第三方库内部实现（但使用时应考虑性能影响）

## 总体策略

1. **性能优先**：在保证功能正确性的前提下，优先考虑性能。
2. **测量驱动**：性能优化应基于实际测量数据，避免过早优化。
3. **平衡取舍**：性能优化应考虑代码可维护性和开发效率。

---

## 禁止（Must Not）

### 1) 性能问题

- ❌ N+1 查询问题：循环中执行数据库查询（应使用批量查询或 JOIN）。
- ❌ 全表扫描：大数据量查询不使用索引（应创建合适的索引）。
- ❌ 内存泄漏：未释放资源（应使用 try-with-resources 或 finally 释放）。

### 2) 性能优化

- ❌ 过早优化：在未测量性能瓶颈前进行优化（应先测量再优化）。
- ❌ 过度优化：为了微小的性能提升牺牲代码可读性。

### 3) 资源使用

- ❌ 无界集合：创建无界集合可能导致内存溢出（应使用分页或流式处理）。
- ❌ 频繁创建对象：在循环中频繁创建昂贵对象（应复用或缓存）。

---

## 必须（Must）

### 1) 数据库查询

- ✅ 索引优化：常用查询字段必须创建索引，避免全表扫描。
- ✅ 批量操作：批量插入/更新使用批量操作，避免循环单条操作。
- ✅ 分页查询：大数据量查询必须分页，避免一次性加载全量数据。

### 2) 缓存使用

- ✅ 热点数据：频繁访问的数据应使用缓存（如 Redis）。
- ✅ 缓存失效：缓存应设置合理的过期时间，避免数据不一致。

### 3) 资源管理

- ✅ 资源释放：IO、数据库连接等资源必须及时释放。
- ✅ 连接池：数据库连接使用连接池，避免频繁创建连接。

### 4) 前端性能

- ✅ 代码分割：大模块使用动态导入，减少初始加载时间。
- ✅ 资源优化：图片、CSS、JS 等资源应压缩和优化。

---

## 应该（Should）

### 1) 查询优化

- ⚠️ JOIN 优化：JOIN 查询不超过 3 张表，被联表字段必须有索引。
- ⚠️ 查询字段：只查询需要的字段，避免 SELECT *。
- ⚠️ 查询条件：WHERE 条件字段必须有索引。

### 2) 代码优化

- ⚠️ 对象复用：避免重复创建昂贵对象（如 ObjectMapper、Pattern），应复用或缓存。
- ⚠️ 算法优化：选择合适的数据结构和算法，提升性能。

### 3) 前端优化

- ⚠️ 懒加载：路由和大型组件使用懒加载，减少初始加载时间。
- ⚠️ 虚拟滚动：大数据列表使用虚拟滚动，提升渲染性能。
- ⚠️ 防抖节流：频繁触发的事件使用防抖或节流。

### 4) 性能监控

- ⚠️ 性能指标：关键接口应监控响应时间、吞吐量等指标。
- ⚠️ 性能分析：定期进行性能分析，识别性能瓶颈。

---

## 可以（May）

- 💡 使用异步处理：非关键路径使用异步处理，提升响应速度。
- 💡 使用消息队列：耗时操作使用消息队列异步处理。

---

## 例外与裁决

### 开发阶段

- 开发阶段：开发阶段可适当放宽性能要求，但应避免明显的性能问题。

### 第三方库

- 第三方库：第三方库性能问题应反馈给库维护者，或考虑替换。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- 性能优化与代码可维护性冲突时，应平衡取舍。

---

## 示例

### ✅ 正例：批量查询（避免 N+1）

```java
// ✅ 使用批量查询
public List<UserDTO> getUsersByIds(List<Long> ids) {
    return userRepository.findAllById(ids)
        .stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
}

// ❌ 错误：N+1 查询
public List<UserDTO> getUsersByIds(List<Long> ids) {
    return ids.stream()
        .map(id -> userRepository.findById(id)) // ❌ 循环中查询
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(this::convertToDTO)
        .collect(Collectors.toList());
}
```

### ✅ 正例：分页查询

```java
// ✅ 使用分页查询
public PageResponse<UserDTO> getUsers(Pageable pageable) {
    Page<User> users = userRepository.findAll(pageable);
    return PageResponse.of(users.map(this::convertToDTO));
}

// ❌ 错误：一次性加载全量数据
public List<UserDTO> getUsers() {
    return userRepository.findAll() // ❌ 可能加载大量数据
        .stream()
        .map(this::convertToDTO)
        .collect(Collectors.toList());
}
```

### ✅ 正例：批量插入

```java
// ✅ 使用批量插入
@Transactional
public void batchCreateUsers(List<User> users) {
    userRepository.saveAll(users); // ✅ 批量插入
}

// ❌ 错误：循环单条插入
@Transactional
public void batchCreateUsers(List<User> users) {
    for (User user : users) {
        userRepository.save(user); // ❌ 性能差
    }
}
```

### ✅ 正例：对象复用

```java
// ✅ 对象复用
@Service
public class JsonService {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj); // ✅ 复用 ObjectMapper
    }
}

// ❌ 错误：频繁创建对象
public String toJson(Object obj) {
    ObjectMapper mapper = new ObjectMapper(); // ❌ 每次创建新对象
    return mapper.writeValueAsString(obj);
}
```

### ✅ 正例：缓存使用

```java
// ✅ 使用缓存
@Cacheable(value = "users", key = "#id")
public UserDTO getUserById(Long id) {
    return convertToDTO(userRepository.findById(id).orElseThrow());
}

// ❌ 错误：频繁查询数据库
public UserDTO getUserById(Long id) {
    return convertToDTO(userRepository.findById(id).orElseThrow()); // ❌ 每次都查询数据库
}
```

### ✅ 正例：前端懒加载

```typescript
// ✅ 路由懒加载
const routes = [
  {
    path: '/user',
    component: () => import('@/views/UserView.vue') // ✅ 动态导入
  }
]

// ✅ 组件懒加载
const HeavyComponent = defineAsyncComponent(() => 
  import('@/components/HeavyComponent.vue')
)
```

### ✅ 正例：前端虚拟滚动

```vue
<template>
  <!-- ✅ 大数据列表使用虚拟滚动 -->
  <a-table
    :columns="columns"
    :data-source="largeDataSet"
    :scroll="{ y: 400 }"
    :virtual="true"
  />
</template>
```

### ✅ 正例：防抖节流

```typescript
// ✅ 防抖：搜索输入
const debouncedSearch = debounce((keyword: string) => {
  searchUsers(keyword)
}, 300)

// ✅ 节流：滚动事件
const throttledScroll = throttle(() => {
  handleScroll()
}, 100)
```

### ✅ 正例：索引优化

```sql
-- ✅ 为常用查询字段创建索引
CREATE INDEX idx_user_tenant_id ON user(tenant_id);
CREATE INDEX idx_user_status ON user(status);
CREATE INDEX idx_user_create_time ON user(create_time);

-- ✅ 联合索引覆盖常用查询
CREATE INDEX idx_user_tenant_status ON user(tenant_id, status);
```

### ✅ 正例：查询字段优化

```java
// ✅ 只查询需要的字段
@Query("SELECT u.id, u.username, u.email FROM User u WHERE u.tenantId = :tenantId")
List<UserProjection> findUsersByTenantId(@Param("tenantId") Long tenantId);

// ❌ 错误：查询所有字段
List<User> findAll(); // ❌ SELECT * 可能查询不需要的字段
```
