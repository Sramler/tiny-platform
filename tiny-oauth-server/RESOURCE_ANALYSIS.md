# Resource 架构分析

## Resource 的性质

### Resource（资源）
**性质：核心基础设施 - 权限控制基础**

## 关键依赖关系

### 1. ✅ **RBAC 权限体系核心**
- 与 Role 多对多关系（`role_resource` 表）
- 被 `SecurityUser` 使用，生成权限列表：
  ```java
  role.getResources().stream()
      .map(resource -> new SimpleGrantedAuthority(resource.getName()))
  ```
- Resource 的 `name` 字段作为权限标识（`GrantedAuthority`）

### 2. ✅ **权限判断核心**
- Resource 包含 `permission` 字段，用于前端权限控制
- Resource 包含 `uri` 和 `method` 字段，用于后端 API 权限控制
- 被 `ResourceService.findByPermission()` 使用

### 3. ✅ **资源类型支持**
- `ResourceType.DIRECTORY` - 目录
- `ResourceType.MENU` - 菜单
- `ResourceType.BUTTON` - 按钮
- `ResourceType.API` - 接口

### 4. ✅ **前端路由和权限控制**
- Resource 包含前端路由信息（`url`, `component`）
- Resource 包含权限标识（`permission`），用于前端按钮/菜单显示控制

## 结论

**Resource 是权限体系的基础，无法抽离。**

**没有 Resource，系统无法进行：**
- ❌ 细粒度的权限控制（API、按钮、菜单级别）
- ❌ 基于资源的权限判断
- ❌ 前端权限控制（按钮显示/隐藏）
- ❌ 后端 API 权限控制

## 权限体系完整链路

```
User (用户)
  ↓ 拥有
Role (角色)
  ↓ 关联
Resource (资源) ← 权限的载体
  ↓ 转换为
GrantedAuthority (权限标识)
  ↓ 用于
@PreAuthorize / hasAuthority() (权限判断)
```

## 最终结论

**User、Role、Resource 是权限体系的三要素，都是基础设施，缺一不可。**

| 实体 | 作用 | 是否可抽离 |
|------|------|-----------|
| **User** | 认证主体 | ❌ 不可抽离 |
| **Role** | 角色/组 | ❌ 不可抽离 |
| **Resource** | 权限资源 | ❌ 不可抽离 |

