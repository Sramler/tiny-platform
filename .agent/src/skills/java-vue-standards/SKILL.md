# Java & Vue 3 编码规范（Skill）

> 放长示例、模板、代码片段、常见坑；Rules 只保留红线与裁决。

## Java 模板

### DTO / VO 约定

```java
public class UserDTO {
    private Long id;
    private String username;
    private String email;
}

public class UserVO {
    private Long id;
    private String username;
}
```

### 统一响应包装

```java
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = "0";
        r.message = "OK";
        r.data = data;
        return r;
    }
}
```

### 异常模板

```java
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
```

### 单测模板

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    @Test
    void shouldFindUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        assertNotNull(userService.getUserById(1L));
    }
}
```

## Vue 3 模板

### 请求封装

```typescript
import axios from 'axios'

const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL })
export default request
```

### 路由守卫

```typescript
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) return next('/login')
  next()
})
```

### 权限指令

```typescript
app.directive('permission', {
  mounted(el, binding) {
    const perms = getUserPerms()
    if (!perms.includes(binding.value)) el.remove()
  }
})
```

## Ant Design Vue 模板

### 表格

```vue
<a-table :columns="columns" :data-source="data" row-key="id" />
```

### 表单

```vue
<a-form :model="form" @finish="onSubmit">
  <a-form-item name="name" label="Name">
    <a-input v-model:value="form.name" />
  </a-form-item>
</a-form>
```

### 弹窗

```vue
<a-modal v-model:open="open" title="Edit" @ok="onOk">
  <EditForm />
</a-modal>
```
