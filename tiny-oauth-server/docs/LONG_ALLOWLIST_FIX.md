# OAuth2 Long 类型授权快照兼容说明

## 当前状态

历史上的 Spring Security Jackson 2 allowlist 兼容链已经下线。当前项目不再通过反射、`Unsafe` 或占位模块修改框架内部 allowlist，而是统一使用以下现行方案：

1. OAuth2 授权持久化走 Spring Security 7 的 Jackson 3 `authorizationMapper`
2. `SecurityUser.userId` / `activeTenantId` 通过自定义序列化器写成字符串
3. 自定义反序列化器继续兼容历史 `String` / `Number` 两种 Long 表达
4. `BasicPolymorphicTypeValidator` 显式放行授权快照中允许出现的类型

## 为什么这样做

- 避免继续维护 Jackson 2 时代的内部实现兼容壳
- 避免前端 Long 精度丢失
- 保持新旧授权快照都能被读取
- 降低升级 Spring Boot 4 / Spring Security 7 时的维护成本

## 当前落点

- `SecurityUserLongSerializer`：将 Long 写成字符串
- `SecurityUserLongDeserializer`：兼容字符串和数字
- `JacksonConfig#authorizationMapper()`：使用 Spring Security 7 Jackson 3 模块与类型白名单
- `OAuth2DataConfig`：将授权持久化显式绑定到 `authorizationMapper`

## 已移除的历史方案

以下 Jackson 2 兼容实现已移除，不再作为运行态依赖：

- 反射修改 allowlist 的占位模块
- 仅为测试保留的 Long 类型解析兼容壳

## 验证建议

1. 执行 OAuth2 登录授权流程，确认授权码和 token 正常签发
2. 清理并重建授权快照后，确认历史 `Long` 字段仍可反序列化
3. 关注 `JacksonConfigCoverageTest`、`JwtTokenCustomizerTest` 与 OAuth2 授权相关定向测试

## 参考

- [Spring Security Jackson2Modules 源码](https://github.com/spring-projects/spring-security/blob/main/core/src/main/java/org/springframework/security/jackson2/SecurityJackson2Modules.java)
- [Jackson Module 文档](https://github.com/FasterXML/jackson-docs/wiki/JacksonModules)
