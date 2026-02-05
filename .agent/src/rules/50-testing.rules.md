# 50 测试规范

## 适用范围

- 适用于：`**/*Test.java`、`**/*Tests.java`、`**/*.spec.ts`、`**/*.test.ts`、测试相关文件
- 不适用于：性能测试、压力测试（但应遵循基本测试原则）

## 总体策略

1. **测试隔离**：每个测试用例独立，不依赖其他测试的执行结果。
2. **快速反馈**：单元测试必须快速执行，不依赖外部服务。
3. **可维护性**：测试代码保持清晰，易于理解和维护。

---

## 禁止（Must Not）

### 1) 测试质量

- ❌ 只有"跑通"没有断言的测试。
- ❌ 测试依赖外部服务（数据库、网络、文件系统）而不使用 mock。
- ❌ 测试用例之间相互依赖（测试执行顺序影响结果）。

### 2) 测试数据

- ❌ 测试使用生产数据（必须使用独立的测试数据）。
- ❌ 测试数据污染：测试后不清理数据（影响后续测试）。

### 3) 测试实现

- ❌ 测试代码包含业务逻辑（测试应只验证行为）。
- ❌ 测试使用随机数据（应使用固定测试数据，便于重现）。

---

## 必须（Must）

### 1) 测试覆盖

- ✅ 修复 bug / 新增逻辑必须提供：测试或明确验证步骤。
- ✅ 测试命名清晰，能表达场景与预期（格式：`方法名_场景_预期结果`）。
- ✅ 测试断言：使用 `Assertions` 或 `assertThat`，明确断言预期结果。
- ✅ 测试隔离：每个测试用例独立，不依赖其他测试的执行结果。

### 2) 测试结构

- ✅ 测试遵循 AAA 模式：Arrange（准备）→ Act（执行）→ Assert（断言）。
- ✅ 测试方法单一职责：每个测试方法只验证一个行为。
- ✅ 测试数据准备：使用 `@Sql` 或 `@TestData` 准备测试数据，测试后清理。

### 3) 外部依赖

- ✅ 外部依赖用 mock：数据库使用 `@MockBean` 或内存数据库（H2），外部服务使用 Mockito。
- ✅ 集成测试：关键业务流程使用 `@SpringBootTest` 编写集成测试。

---

## 应该（Should）

### 1) 测试覆盖范围

- ⚠️ 关键分支与异常路径要覆盖：正常流程、边界条件、异常情况都要有测试用例。
- ⚠️ 测试覆盖率：关键业务逻辑（Service、Controller）覆盖率 ≥ 80%。
- ⚠️ 边界测试：测试边界值（null、空集合、最大值、最小值）。

### 2) 测试组织

- ⚠️ 测试类命名：`被测试类名 + Test`（如 `UserServiceTest`）。
- ⚠️ 测试方法命名：`方法名_场景_预期结果`（如 `getUserById_whenUserExists_returnsUser`）。
- ⚠️ 测试分组：使用 `@Nested` 或测试类分组相关测试。

### 3) Mock 与 Stub

- ⚠️ 合理使用 Mock：只 Mock 外部依赖，不 Mock 被测试类。
- ⚠️ 验证 Mock 调用：验证方法调用次数、参数、顺序（如 `verify(mock, times(1))`）。

### 4) 测试数据

- ⚠️ 测试数据工厂：使用 Builder 模式或 Factory 方法创建测试数据。
- ⚠️ 测试数据清理：使用 `@Transactional` 或 `@Sql` 清理测试数据。

---

## 可以（May）

- 💡 性能测试：关键接口编写性能测试（响应时间、并发能力）。
- 💡 契约测试：API 接口使用契约测试（如 Pact）确保接口兼容性。
- 💡 参数化测试：使用 `@ParameterizedTest` 测试多个输入场景。

---

## 例外与裁决

### 集成测试

- 集成测试可依赖真实数据库，但必须使用独立的测试数据库。
- 集成测试必须可重复执行，不依赖外部状态。

### 性能测试

- 性能测试可依赖外部服务，但必须明确标注且不影响 CI 流程。
- 性能测试应在独立环境执行，不阻塞常规测试。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- 测试规范与业务规范冲突时，优先保证测试可维护性。

---

## 示例

### ✅ 正例：AAA 模式、清晰命名、完整断言

```java
@SpringBootTest
class UserServiceTest {
    @MockBean
    private UserRepository userRepository;
    
    @Autowired
    private UserService userService;
    
    @Test
    void getUserById_whenUserExists_returnsUser() {
        // Arrange（准备）
        Long userId = 1L;
        User user = User.builder()
            .id(userId)
            .username("test")
            .email("test@example.com")
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // Act（执行）
        User result = userService.getUserById(userId);
        
        // Assert（断言）
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getUsername()).isEqualTo("test");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }
    
    @Test
    void getUserById_whenUserNotExists_throwsException() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("用户不存在");
    }
}
```

### ❌ 反例：没有断言、依赖外部服务、测试之间相互依赖

```java
// 错误：没有断言、依赖外部服务、测试之间相互依赖
@Test
void testGetUser() {
    User user = userService.getUserById(1L); // ❌ 没有断言
    // ❌ 依赖真实数据库，可能因为数据不存在而失败
}

@Test
void testUpdateUser() {
    // ❌ 依赖上一个测试创建的数据
    userService.updateUser(existingUser);
}
```

### ✅ 正例：测试数据工厂

```java
class UserTestDataFactory {
    static User createUser(Long id, String username) {
        return User.builder()
            .id(id)
            .username(username)
            .email(username + "@example.com")
            .build();
    }
    
    static User createDefaultUser() {
        return createUser(1L, "test");
    }
}

@Test
void getUserById_whenUserExists_returnsUser() {
    // Arrange
    User user = UserTestDataFactory.createDefaultUser();
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    
    // Act & Assert
    // ...
}
```

### ✅ 正例：参数化测试

```java
@ParameterizedTest
@ValueSource(strings = {"admin", "user", "guest"})
void hasPermission_whenUserHasRole_returnsTrue(String role) {
    // Arrange
    User user = User.builder().role(role).build();
    
    // Act
    boolean hasPermission = userService.hasPermission(user, "READ");
    
    // Assert
    assertThat(hasPermission).isTrue();
}
```

### ✅ 正例：Mock 验证

```java
@Test
void createUser_whenValidUser_callsRepositorySave() {
    // Arrange
    CreateUserRequest request = CreateUserRequest.builder()
        .username("test")
        .email("test@example.com")
        .build();
    
    // Act
    userService.createUser(request);
    
    // Assert
    verify(userRepository, times(1)).save(any(User.class));
    verify(userRepository, never()).delete(any());
}
```

### ✅ 正例：集成测试（使用测试数据库）

```java
@SpringBootTest
@Transactional
@Sql(scripts = "/test-data/users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UserServiceIntegrationTest {
    @Autowired
    private UserService userService;
    
    @Test
    void createUser_whenValidUser_createsUserInDatabase() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
            .username("integration-test")
            .email("integration@example.com")
            .build();
        
        // Act
        UserDTO result = userService.createUser(request);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getUsername()).isEqualTo("integration-test");
    }
}
```

### ✅ 正例：边界测试

```java
@Test
void getUserById_whenIdIsNull_throwsException() {
    assertThatThrownBy(() -> userService.getUserById(null))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void getUserById_whenIdIsNegative_throwsException() {
    assertThatThrownBy(() -> userService.getUserById(-1L))
        .isInstanceOf(IllegalArgumentException.class);
}
```

### ❌ 反例：测试包含业务逻辑

```java
// ❌ 错误：测试代码包含业务逻辑
@Test
void testGetUser() {
    User user = userService.getUserById(1L);
    
    // ❌ 测试代码包含业务逻辑
    if (user != null && user.getStatus().equals("ACTIVE")) {
        assertThat(user.getUsername()).isNotNull();
    }
}
```
