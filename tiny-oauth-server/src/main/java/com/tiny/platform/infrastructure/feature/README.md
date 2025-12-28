# Feature Toggle 基础设施

## 职责

Feature Toggle 基础设施模块，提供 SaaS 平台的 Feature 启用、禁用、查询能力。

## 目录结构

```
feature/
├── domain/          # Feature 实体
│   └── Feature.java  # Feature 实体（待实现）
├── repository/      # Feature 仓储
│   └── FeatureRepository.java  # Feature 仓储接口（待实现）
├── service/         # Feature 服务
│   └── FeatureService.java  # Feature 服务接口（待实现）
└── interceptor/     # Feature 拦截器
    └── FeatureToggleFilter.java  # Feature Toggle 检查过滤器（待实现）
```

## 设计说明

- **Feature 实体**：定义 Feature 的基本信息（key、name、status、rollout 等）
- **FeatureRepository**：提供 Feature 的持久化能力
- **FeatureService**：提供 Feature 的业务逻辑（启用、禁用、查询等）
- **FeatureToggleFilter**：在请求拦截器中检查 Feature 是否已启用

## 相关表设计

```sql
-- Feature 定义表
CREATE TABLE feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  key VARCHAR(128) UNIQUE NOT NULL COMMENT 'Feature 标识',
  name VARCHAR(128) NOT NULL COMMENT 'Feature 名称',
  description TEXT COMMENT 'Feature 描述',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 租户 Feature 配置表
CREATE TABLE tenant_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  feature_key VARCHAR(128) NOT NULL COMMENT 'Feature 标识',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  rollout INT DEFAULT 100 COMMENT '灰度比例：0-100',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_feature (tenant_id, feature_key),
  FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
```

## 使用场景

1. **Feature 启用**：租户可以启用某个 Feature（如 dict.v2、workflow.v1 等）
2. **Feature 禁用**：租户可以禁用不需要的 Feature
3. **灰度发布**：通过 rollout 字段控制 Feature 的灰度比例
4. **Feature 检查**：在请求拦截器中检查 Feature 是否已启用，未启用则拒绝访问

## 拦截顺序

```
Tenant → Plugin → Feature → Permission
```

Feature 检查在 Plugin 检查之后、Permission 检查之前。

