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
  password VARCHAR(100) NULL COMMENT '加密密码（已废弃，保留用于兼容，实际密码存储在 user_authentication_method 表中）',
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

-- 资源表
CREATE TABLE resource (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '资源ID',
  name VARCHAR(100) NOT NULL COMMENT '资源名称',
  path VARCHAR(200) COMMENT '资源路径（如 /api/user/add）',
  method VARCHAR(10) COMMENT 'HTTP方法（如GET、POST）',
  type VARCHAR(20) COMMENT '资源类型（如MENU, BUTTON, API）',
  parent_id BIGINT DEFAULT NULL COMMENT '父资源ID，用于菜单树',
  sort INT DEFAULT 0 COMMENT '排序号'
) COMMENT = '资源表';

-- 用户认证方法表
CREATE TABLE user_authentication_method (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  authentication_provider VARCHAR(50) NOT NULL COMMENT '认证提供者（LOCAL, GITHUB, GOOGLE 等）',
  authentication_type VARCHAR(50) NOT NULL COMMENT '认证类型（PASSWORD, TOTP, OAUTH2 等）',
  authentication_configuration JSON NOT NULL COMMENT '认证配置（JSON格式，如密码哈希、TOTP密钥等）',
  is_primary_method BOOLEAN DEFAULT FALSE COMMENT '是否主要认证方法',
  is_method_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
  authentication_priority INT DEFAULT 0 COMMENT '认证优先级（数字越小优先级越高）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  expires_at DATETIME NULL COMMENT '过期时间',
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  UNIQUE KEY uk_user_auth_method (user_id, authentication_provider, authentication_type),
  KEY idx_user_provider (user_id, authentication_provider)
) COMMENT = '用户认证方法表';