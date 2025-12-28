# 插件管理基础设施

## 职责

插件管理基础设施模块，提供 SaaS 平台的插件安装、卸载、查询能力。

## 目录结构

```
plugin/
├── domain/          # 插件实体
│   └── Plugin.java  # 插件实体（待实现）
├── repository/      # 插件仓储
│   └── PluginRepository.java  # 插件仓储接口（待实现）
├── service/         # 插件服务
│   └── PluginService.java  # 插件服务接口（待实现）
└── interceptor/     # 插件拦截器
    └── PluginInstallFilter.java  # 插件安装检查过滤器（待实现）
```

## 设计说明

- **Plugin 实体**：定义插件的基本信息（key、name、version、status 等）
- **PluginRepository**：提供插件的持久化能力
- **PluginService**：提供插件的业务逻辑（安装、卸载、查询等）
- **PluginInstallFilter**：在请求拦截器中检查插件是否已安装

## 相关表设计

```sql
-- 插件定义表
CREATE TABLE plugin (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  key VARCHAR(128) UNIQUE NOT NULL COMMENT '插件标识',
  name VARCHAR(128) NOT NULL COMMENT '插件名称',
  version VARCHAR(32) NOT NULL COMMENT '插件版本',
  status TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 租户插件安装表
CREATE TABLE tenant_plugin (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  plugin_key VARCHAR(128) NOT NULL COMMENT '插件标识',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  installed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_plugin (tenant_id, plugin_key),
  FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
```

## 使用场景

1. **插件安装**：租户可以安装业务插件（如 workflow、report 等）
2. **插件卸载**：租户可以卸载不需要的插件
3. **插件检查**：在请求拦截器中检查插件是否已安装，未安装则拒绝访问

