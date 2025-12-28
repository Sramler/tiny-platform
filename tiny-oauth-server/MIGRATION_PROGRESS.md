# 代码迁移进度

## 已完成

### 1. Dict 模块迁移 ✅
- ✅ 复制 Dict 相关文件到 `core.dict`
- ✅ 更新包名：`com.tiny.dict` → `com.tiny.platform.core.dict`
- ✅ 更新内部 import 语句

### 2. 实体类迁移 ✅
- ✅ User.java → `infrastructure.auth.user.domain.User`
- ✅ Role.java → `infrastructure.auth.role.domain.Role`
- ✅ Resource.java → `infrastructure.auth.resource.domain.Resource`
- ✅ 修复包名和 import 语句

## 待完成

### 3. Repository 迁移
- [ ] UserRepository → `infrastructure.auth.user.repository.UserRepository`
- [ ] RoleRepository → `infrastructure.auth.role.repository.RoleRepository`
- [ ] ResourceRepository → `infrastructure.auth.resource.repository.ResourceRepository`
- [ ] MenuRepository（如果存在）→ `infrastructure.menu.repository.MenuRepository`

### 4. Service 迁移
- [ ] UserService → `infrastructure.auth.user.service.UserService`
- [ ] RoleService → `infrastructure.auth.role.service.RoleService`
- [ ] ResourceService → `infrastructure.auth.resource.service.ResourceService`
- [ ] MenuService → `infrastructure.menu.service.MenuService`

### 5. Security 迁移
- [ ] UserDetailsServiceImpl → `infrastructure.auth.security.UserDetailsServiceImpl`
- [ ] SecurityUser → `infrastructure.auth.security.SecurityUser`
- [ ] 其他 Security 相关类

### 6. Controller 迁移
- [ ] UserController → `application.controller.user.UserController`
- [ ] RoleController → `application.controller.role.RoleController`
- [ ] ResourceController → `application.controller.resource.ResourceController`
- [ ] MenuController → `application.controller.menu.MenuController`
- [ ] DictController → `application.controller.dict.DictController`

### 7. DTO 和其他类迁移
- [ ] 所有 DTO 类需要迁移到对应的包
- [ ] Enum 类（如 ResourceType）需要迁移
- [ ] Converter 类需要迁移

### 8. 更新所有 import 语句
- [ ] 更新所有引用 User、Role、Resource 的文件
- [ ] 更新所有引用 Dict 的文件
- [ ] 更新所有引用 sys 包的文件

### 9. 更新 Spring Boot 扫描路径
- [ ] 更新 `OauthServerApplication.java` 的扫描路径
- [ ] 更新 `@EntityScan` 和 `@EnableJpaRepositories` 的路径

## 注意事项

1. **循环依赖**：User、Role、Resource 之间有循环依赖，需要正确处理 import
2. **ResourceType Enum**：需要迁移到合适的位置（可能是 `infrastructure.auth.resource.enums`）
3. **JsonStringConverter**：Dict 中使用的转换器也需要迁移
4. **测试**：迁移后需要测试所有功能是否正常

## 迁移命令参考

```bash
# 查找需要更新的文件
find . -name "*.java" -exec grep -l "com.tiny.oauthserver.sys" {} \;
find . -name "*.java" -exec grep -l "com.tiny.dict" {} \;

# 批量替换包名（谨慎使用）
find . -name "*.java" -exec perl -pi -e 's/old/new/g' {} \;
```

