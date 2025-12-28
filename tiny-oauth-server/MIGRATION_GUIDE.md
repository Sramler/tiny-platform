# 代码迁移指南

## 当前状态

### ✅ 已完成的基础迁移

1. **Dict 模块**
   - ✅ 文件已复制到 `com.tiny.platform.core.dict`
   - ✅ 包名已更新
   - ✅ Controller 已迁移到 `application.controller.dict`

2. **实体类**
   - ✅ User.java → `infrastructure.auth.user.domain.User`
   - ✅ Role.java → `infrastructure.auth.role.domain.Role`
   - ✅ Resource.java → `infrastructure.auth.resource.domain.Resource`

3. **Repository**
   - ✅ UserRepository → `infrastructure.auth.user.repository.UserRepository`
   - ✅ RoleRepository → `infrastructure.auth.role.repository.RoleRepository`
   - ✅ ResourceRepository → `infrastructure.auth.resource.repository.ResourceRepository`

4. **Service（部分）**
   - ✅ UserService/UserServiceImpl → `infrastructure.auth.user.service`
   - ✅ MenuService/MenuServiceImpl → `infrastructure.menu.service`

### ⚠️ 需要修复的问题

1. **UserServiceImpl 的 import 错误**
   - `com.tiny.platform.infrastructure.auth.user.model.*` → DTO 类需要迁移
   - `com.tiny.platform.infrastructure.auth.user.repository.RoleRepository` → 应该是 `infrastructure.auth.role.repository.RoleRepository`
   - `com.tiny.oauthserver.sys.model.Role` → 应该是 `infrastructure.auth.role.domain.Role`

2. **DTO 类未迁移**
   - UserRequestDto, UserResponseDto, UserCreateUpdateDto 等
   - RoleRequestDto, RoleResponseDto, RoleCreateUpdateDto 等
   - ResourceRequestDto, ResourceResponseDto, ResourceCreateUpdateDto 等

3. **其他类未迁移**
   - ResourceType Enum
   - JsonStringConverter, ResourceTypeConverter
   - SecurityUser, UserDetailsServiceImpl
   - 其他辅助类

## 推荐的迁移顺序

### 阶段 1：完成 Service 迁移
1. 迁移 RoleService 和 RoleServiceImpl
2. 迁移 ResourceService 和 ResourceServiceImpl
3. 修复所有 Service 中的 import 语句

### 阶段 2：迁移 DTO 和辅助类
1. 创建 DTO 目录结构
2. 迁移所有 DTO 类
3. 迁移 Enum 类（ResourceType）
4. 迁移 Converter 类

### 阶段 3：迁移 Security 相关类
1. 迁移 UserDetailsServiceImpl
2. 迁移 SecurityUser
3. 迁移其他 Security 相关类

### 阶段 4：迁移 Controller
1. 迁移 UserController
2. 迁移 RoleController
3. 迁移 ResourceController
4. 迁移 MenuController

### 阶段 5：批量更新 import
1. 查找所有引用旧包的文件
2. 批量替换 import 语句
3. 修复循环依赖

### 阶段 6：更新 Spring Boot 配置
1. 更新扫描路径
2. 更新 EntityScan
3. 更新 EnableJpaRepositories

## 快速修复命令

```bash
# 查找需要更新的文件
find . -name "*.java" -exec grep -l "com.tiny.oauthserver.sys" {} \;

# 批量替换（谨慎使用，建议先备份）
find . -name "*.java" -exec perl -pi -e 's/old/new/g' {} \;
```

## 注意事项

1. **备份**：迁移前建议先提交代码或创建分支
2. **测试**：每完成一个阶段都要测试编译
3. **循环依赖**：User、Role、Resource 之间有循环依赖，需要正确处理
4. **DTO 位置**：DTO 可以放在对应的 service 包下，或者创建单独的 dto 包

