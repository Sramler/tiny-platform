-- 创建数据库
CREATE DATABASE IF NOT EXISTS tiny_web
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

-- 使用该数据库
USE tiny_web;

-- 用户表
CREATE TABLE user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(100) NULL COMMENT '加密密码（已废弃，保留用于兼容；实际密码存储在 user_auth_credential + user_auth_scope_policy）',
  nickname VARCHAR(50) COMMENT '昵称',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  account_non_expired BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否未过期',
  account_non_locked BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否未锁定',
  credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE COMMENT '密码是否未过期',
  last_login_at DATETIME NULL COMMENT '最后登录时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT = '用户表';

-- 角色表
CREATE TABLE role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  code VARCHAR(50) NULL COMMENT '角色编码（兼容 oauth 角色口径）',
  name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识（如ROLE_ADMIN）',
  description VARCHAR(100) COMMENT '角色描述'
) COMMENT = '角色表';

-- 用户-角色关联表
CREATE TABLE user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) COMMENT = '用户与角色关联表';

-- 权限主数据表（canonical）
CREATE TABLE permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
  permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
  permission_name VARCHAR(255) NOT NULL COMMENT '权限名称',
  permission_type VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '权限类型',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户ID，NULL=平台模板',
  normalized_tenant_id BIGINT GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED COMMENT '归一化租户ID',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  UNIQUE KEY uk_permission_tenant_code (normalized_tenant_id, permission_code)
) COMMENT = '权限主数据表';

-- 角色-权限关系表（canonical）
CREATE TABLE role_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  normalized_tenant_id BIGINT GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED COMMENT '归一化租户ID',
  UNIQUE KEY uk_role_permission_tenant (normalized_tenant_id, role_id, permission_id)
) COMMENT = '角色-权限关系表';

-- 菜单载体表
CREATE TABLE menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户ID，NULL=平台模板',
  path VARCHAR(200) NOT NULL DEFAULT '' COMMENT '前端路由路径',
  permission VARCHAR(128) NOT NULL DEFAULT '' COMMENT '权限码（兼容可读）',
  required_permission_id BIGINT DEFAULT NULL COMMENT '显式绑定权限ID',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用'
) COMMENT = '菜单载体表';

-- 页面动作载体表
CREATE TABLE ui_action (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '动作ID',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户ID，NULL=平台模板',
  page_path VARCHAR(200) NOT NULL DEFAULT '' COMMENT '页面路径',
  permission VARCHAR(128) NOT NULL DEFAULT '' COMMENT '权限码（兼容可读）',
  required_permission_id BIGINT DEFAULT NULL COMMENT '显式绑定权限ID',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用'
) COMMENT = '页面动作载体表';

-- API 端点载体表
CREATE TABLE api_endpoint (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '端点ID',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户ID，NULL=平台模板',
  uri VARCHAR(200) NOT NULL DEFAULT '' COMMENT '接口路径',
  method VARCHAR(10) NOT NULL DEFAULT '' COMMENT 'HTTP方法',
  permission VARCHAR(128) NOT NULL DEFAULT '' COMMENT '权限码（兼容可读）',
  required_permission_id BIGINT DEFAULT NULL COMMENT '显式绑定权限ID',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用'
) COMMENT = 'API 端点载体表';

-- 用户认证凭证表（材料层；与 tiny-oauth-server 新模型对齐）
CREATE TABLE user_auth_credential (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  authentication_provider VARCHAR(50) NOT NULL COMMENT '认证提供者',
  authentication_type VARCHAR(50) NOT NULL COMMENT '认证类型',
  authentication_configuration JSON NOT NULL COMMENT '认证材料配置 JSON',
  last_verified_at DATETIME NULL COMMENT '最后验证时间',
  last_verified_ip VARCHAR(50) DEFAULT NULL COMMENT '最后验证 IP',
  expires_at DATETIME NULL COMMENT '过期时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_auth_credential_user_provider_type (user_id, authentication_provider, authentication_type),
  KEY idx_user_auth_credential_user_id (user_id),
  CONSTRAINT fk_user_auth_credential_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) COMMENT = '用户认证凭证表';

-- 用户认证作用域策略表（策略层）
CREATE TABLE user_auth_scope_policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  credential_id BIGINT NOT NULL COMMENT '关联认证凭证 ID',
  scope_type VARCHAR(16) NOT NULL COMMENT '作用域类型：GLOBAL/PLATFORM/TENANT',
  scope_id BIGINT DEFAULT NULL COMMENT '作用域 ID',
  scope_key VARCHAR(128) NOT NULL COMMENT '作用域归一化键',
  is_primary_method BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否主认证方式',
  is_method_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用认证方式',
  authentication_priority INT NOT NULL DEFAULT 0 COMMENT '认证优先级',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_auth_scope_policy_credential_scope_key (credential_id, scope_key),
  KEY idx_user_auth_scope_policy_scope_type_id (scope_type, scope_id),
  CONSTRAINT chk_user_auth_scope_policy_scope_type CHECK (scope_type IN ('GLOBAL', 'PLATFORM', 'TENANT')),
  CONSTRAINT chk_user_auth_scope_policy_scope_id_by_type CHECK (
    (scope_type IN ('GLOBAL', 'PLATFORM') AND scope_id IS NULL)
    OR (scope_type = 'TENANT' AND scope_id IS NOT NULL)
  ),
  CONSTRAINT fk_user_auth_scope_policy_credential FOREIGN KEY (credential_id) REFERENCES user_auth_credential(id) ON DELETE CASCADE
) COMMENT = '用户认证作用域策略表';
