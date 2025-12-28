# 代码迁移总结

## ✅ 已完成

### 1. Dict 模块迁移
- ✅ 复制到 `com.tiny.platform.core.dict`
- ✅ 更新包名和内部 import
- ✅ Controller 迁移到 `application.controller.dict`

### 2. 实体类迁移
- ✅ User → `infrastructure.auth.user.domain.User`
- ✅ Role → `infrastructure.auth.role.domain.Role`
- ✅ Resource → `infrastructure.auth.resource.domain.Resource`

### 3. Repository 迁移
- ✅ UserRepository → `infrastructure.auth.user.repository.UserRepository`
- ✅ RoleRepository → `infrastructure.auth.role.repository.RoleRepository`
- ✅ ResourceRepository → `infrastructure.auth.resource.repository.ResourceRepository`

### 4. Service 迁移（部分）
- ✅ UserService → `infrastructure.auth.user.service.UserService`
- ✅ UserServiceImpl → `infrastructure.auth.user.service.UserServiceImpl`
- ✅ MenuService → `infrastructure.menu.service.MenuService`
- ✅ MenuServiceImpl → `infrastructure.menu.service.MenuServiceImpl`

## ⚠️ 待完成

### 1. 继续 Service 迁移
- [ ] RoleService → `infrastructure.auth.role.service.RoleService`
- [ ] RoleServiceImpl → `infrastructure.auth.role.service.RoleServiceImpl`
- [ ] ResourceService → `infrastructure.auth.resource.service.ResourceService`
- [ ] ResourceServiceImpl → `infrastructure.auth.resource.service.ResourceServiceImpl`

### 2. Security 迁移
- [ ] UserDetailsServiceImpl → `infrastructure.auth.security.UserDetailsServiceImpl`
- [ ] SecurityUser → `infrastructure.auth.security.SecurityUser`
- [ ] 其他 Security 相关类

### 3. Controller 迁移
- [ ] UserController → `application.controller.user.UserController`
- [ ] RoleController → `application.controller.role.RoleController`
- [ ] ResourceController → `application.controller.resource.ResourceController`
- [ ] MenuController → `application.controller.menu.MenuController`

### 4. DTO 和辅助类迁移
- [ ] 所有 DTO 类需要迁移到对应的包
- [ ] Enum 类（ResourceType）需要迁移
- [ ] Converter 类（JsonStringConverter, ResourceTypeConverter）需要迁移

### 5. 更新所有 import 语句
需要更新以下文件中的 import：
- `com.tiny.oauthserver.sys.*` → 新的包路径
- `com.tiny.dict.*` → `com.tiny.platform.core.dict.*`
- 所有引用 User、Role、Resource 的文件

### 6. 更新 Spring Boot 配置
- [ ] 更新 `OauthServerApplication.java` 的扫描路径
- [ ] 更新 `@EntityScan` 路径
- [ ] 更新 `@EnableJpaRepositories` 路径

## 迁移后的目录结构

```
com.tiny.platform/
├── infrastructure/
│   ├── auth/
│   │   ├── user/
│   │   │   ├── domain/User.java ✅
│   │   │   ├── repository/UserRepository.java ✅
│   │   │   └── service/UserService.java ✅
│   │   ├── role/
│   │   │   ├── domain/Role.java ✅
│   │   │   ├── repository/RoleRepository.java ✅
│   │   │   └── service/ (待迁移)
│   │   ├── resource/
│   │   │   ├── domain/Resource.java ✅
│   │   │   ├── repository/ResourceRepository.java ✅
│   │   │   └── service/ (待迁移)
│   │   └── security/ (待迁移)
│   └── menu/
│       └── service/
│           ├── MenuService.java ✅
│           └── MenuServiceImpl.java ✅
├── core/
│   └── dict/ ✅ (已迁移)
└── application/
    └── controller/
        └── dict/DictController.java ✅
```

## 下一步操作

1. **继续迁移剩余 Service**
2. **迁移 Security 相关类**
3. **迁移 Controller**
4. **迁移 DTO 和辅助类**
5. **批量更新所有 import 语句**
6. **更新 Spring Boot 配置**
7. **测试编译和运行**

## 注意事项

1. **循环依赖**：User、Role、Resource 之间有循环依赖，import 需要正确
2. **ResourceType Enum**：需要迁移到 `infrastructure.auth.resource.enums`
3. **JsonStringConverter**：Dict 中使用的转换器需要迁移或共享
4. **测试**：迁移后需要全面测试

