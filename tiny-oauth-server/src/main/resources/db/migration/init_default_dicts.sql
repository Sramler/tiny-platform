/* =========================================================
 * Tiny Platform - tiny-core 默认初始化字典（v1.0）
 * MySQL 8.x / InnoDB / utf8mb4
 * tenant_id = 0 表示平台字典
 * 幂等执行：重复跑不会报错，会更新 label/sort_order/enabled
 * ========================================================= */

START TRANSACTION;

-- ---------------------------------------------------------
-- 1) ENABLE_STATUS 启用状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('ENABLE_STATUS', '启用状态', '通用启用/禁用状态', 0, 1, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_ENABLE_STATUS := (SELECT id FROM dict_type WHERE dict_code='ENABLE_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_ENABLE_STATUS,'ENABLED','启用',1,0,1,1,NOW(),NOW()),
(@DICT_ENABLE_STATUS,'DISABLED','禁用',2,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 2) YES_NO 是/否
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('YES_NO', '是否', '通用是/否（Y/N）', 0, 1, 1, 1, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_YES_NO := (SELECT id FROM dict_type WHERE dict_code='YES_NO' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_YES_NO,'Y','是',1,0,1,1,NOW(),NOW()),
(@DICT_YES_NO,'N','否',2,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 3) BOOLEAN_FLAG 布尔标识（1/0）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('BOOLEAN_FLAG', '布尔标识', '通用布尔（1/0）', 0, 1, 1, 1, 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_BOOLEAN_FLAG := (SELECT id FROM dict_type WHERE dict_code='BOOLEAN_FLAG' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_BOOLEAN_FLAG,'1','是',1,0,1,1,NOW(),NOW()),
(@DICT_BOOLEAN_FLAG,'0','否',2,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 4) DELETE_FLAG 逻辑删除标识（语义型）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('DELETE_FLAG', '逻辑删除标识', '通用逻辑删除语义', 0, 1, 1, 1, 4, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_DELETE_FLAG := (SELECT id FROM dict_type WHERE dict_code='DELETE_FLAG' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_DELETE_FLAG,'NORMAL','正常',1,0,1,1,NOW(),NOW()),
(@DICT_DELETE_FLAG,'DELETED','已删除',2,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 5) DATA_STATUS 数据状态（草稿/生效/失效）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('DATA_STATUS', '数据状态', '通用数据生命周期状态', 0, 1, 1, 1, 5, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_DATA_STATUS := (SELECT id FROM dict_type WHERE dict_code='DATA_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_DATA_STATUS,'DRAFT','草稿',1,0,1,1,NOW(),NOW()),
(@DICT_DATA_STATUS,'ACTIVE','生效',2,0,1,1,NOW(),NOW()),
(@DICT_DATA_STATUS,'INACTIVE','失效',3,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 6) AUDIT_STATUS 审核状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('AUDIT_STATUS', '审核状态', '通用审核流程状态', 0, 1, 1, 1, 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_AUDIT_STATUS := (SELECT id FROM dict_type WHERE dict_code='AUDIT_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_AUDIT_STATUS,'PENDING','待审核',1,0,1,1,NOW(),NOW()),
(@DICT_AUDIT_STATUS,'APPROVED','已通过',2,0,1,1,NOW(),NOW()),
(@DICT_AUDIT_STATUS,'REJECTED','已驳回',3,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 7) USER_STATUS 用户状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('USER_STATUS', '用户状态', '通用用户状态', 0, 1, 1, 1, 7, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_USER_STATUS := (SELECT id FROM dict_type WHERE dict_code='USER_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_USER_STATUS,'ACTIVE','正常',1,0,1,1,NOW(),NOW()),
(@DICT_USER_STATUS,'LOCKED','锁定',2,0,1,1,NOW(),NOW()),
(@DICT_USER_STATUS,'DISABLED','禁用',3,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 8) ACCOUNT_STATUS 账号状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('ACCOUNT_STATUS', '账号状态', '通用账号状态', 0, 1, 1, 1, 8, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_ACCOUNT_STATUS := (SELECT id FROM dict_type WHERE dict_code='ACCOUNT_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_ACCOUNT_STATUS,'ACTIVE','正常',1,0,1,1,NOW(),NOW()),
(@DICT_ACCOUNT_STATUS,'EXPIRED','已过期',2,0,1,1,NOW(),NOW()),
(@DICT_ACCOUNT_STATUS,'LOCKED','锁定',3,0,1,1,NOW(),NOW()),
(@DICT_ACCOUNT_STATUS,'DISABLED','禁用',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 9) USER_TYPE 用户类型（平台/租户/子账号/服务账号）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('USER_TYPE', '用户类型', '平台/租户用户类型', 0, 1, 1, 1, 9, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_USER_TYPE := (SELECT id FROM dict_type WHERE dict_code='USER_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_USER_TYPE,'PLATFORM','平台用户',1,0,1,1,NOW(),NOW()),
(@DICT_USER_TYPE,'TENANT','租户用户',2,0,1,1,NOW(),NOW()),
(@DICT_USER_TYPE,'TENANT_SUB','租户子账号',3,0,1,1,NOW(),NOW()),
(@DICT_USER_TYPE,'SERVICE','服务账号',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 10) ROLE_TYPE 角色类型
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('ROLE_TYPE', '角色类型', '平台/租户角色类型', 0, 1, 1, 1, 10, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_ROLE_TYPE := (SELECT id FROM dict_type WHERE dict_code='ROLE_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_ROLE_TYPE,'PLATFORM_ADMIN','平台管理员',1,0,1,1,NOW(),NOW()),
(@DICT_ROLE_TYPE,'TENANT_ADMIN','租户管理员',2,0,1,1,NOW(),NOW()),
(@DICT_ROLE_TYPE,'TENANT_USER','租户普通用户',3,0,1,1,NOW(),NOW()),
(@DICT_ROLE_TYPE,'READONLY','只读角色',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 11) RESOURCE_TYPE 资源类型（权限资源）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('RESOURCE_TYPE', '资源类型', '权限资源类型（菜单/接口/按钮/数据）', 0, 1, 1, 1, 11, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_RESOURCE_TYPE := (SELECT id FROM dict_type WHERE dict_code='RESOURCE_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_RESOURCE_TYPE,'MENU','菜单',1,0,1,1,NOW(),NOW()),
(@DICT_RESOURCE_TYPE,'API','接口',2,0,1,1,NOW(),NOW()),
(@DICT_RESOURCE_TYPE,'BUTTON','按钮',3,0,1,1,NOW(),NOW()),
(@DICT_RESOURCE_TYPE,'DATA','数据权限',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 12) PERMISSION_TYPE 权限类型（动作）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('PERMISSION_TYPE', '权限类型', '通用权限动作类型', 0, 1, 1, 1, 12, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_PERMISSION_TYPE := (SELECT id FROM dict_type WHERE dict_code='PERMISSION_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_PERMISSION_TYPE,'READ','读',1,0,1,1,NOW(),NOW()),
(@DICT_PERMISSION_TYPE,'WRITE','写',2,0,1,1,NOW(),NOW()),
(@DICT_PERMISSION_TYPE,'DELETE','删',3,0,1,1,NOW(),NOW()),
(@DICT_PERMISSION_TYPE,'APPROVE','审批',4,0,1,1,NOW(),NOW()),
(@DICT_PERMISSION_TYPE,'EXPORT','导出',5,0,1,1,NOW(),NOW()),
(@DICT_PERMISSION_TYPE,'IMPORT','导入',6,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 13) AUTH_MODE 认证方式
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('AUTH_MODE', '认证方式', '会话/JWT/API KEY/OAuth2/OIDC', 0, 1, 1, 1, 13, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_AUTH_MODE := (SELECT id FROM dict_type WHERE dict_code='AUTH_MODE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_AUTH_MODE,'SESSION','Session',1,0,1,1,NOW(),NOW()),
(@DICT_AUTH_MODE,'JWT','JWT',2,0,1,1,NOW(),NOW()),
(@DICT_AUTH_MODE,'API_KEY','API Key',3,0,1,1,NOW(),NOW()),
(@DICT_AUTH_MODE,'OAUTH2','OAuth2',4,0,1,1,NOW(),NOW()),
(@DICT_AUTH_MODE,'OIDC','OpenID Connect',5,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 14) OPERATION_RESULT 操作结果
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('OPERATION_RESULT', '操作结果', '通用操作结果', 0, 1, 1, 1, 14, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_OPERATION_RESULT := (SELECT id FROM dict_type WHERE dict_code='OPERATION_RESULT' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_OPERATION_RESULT,'SUCCESS','成功',1,0,1,1,NOW(),NOW()),
(@DICT_OPERATION_RESULT,'FAIL','失败',2,0,1,1,NOW(),NOW()),
(@DICT_OPERATION_RESULT,'PARTIAL','部分成功',3,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 15) ERROR_LEVEL 错误级别
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('ERROR_LEVEL', '错误级别', '通用错误级别', 0, 1, 1, 1, 15, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_ERROR_LEVEL := (SELECT id FROM dict_type WHERE dict_code='ERROR_LEVEL' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_ERROR_LEVEL,'INFO','信息',1,0,1,1,NOW(),NOW()),
(@DICT_ERROR_LEVEL,'WARN','警告',2,0,1,1,NOW(),NOW()),
(@DICT_ERROR_LEVEL,'ERROR','错误',3,0,1,1,NOW(),NOW()),
(@DICT_ERROR_LEVEL,'FATAL','致命',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 16) FILE_TYPE 文件类型（通用）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('FILE_TYPE', '文件类型', '通用文件类型', 0, 1, 1, 1, 16, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_FILE_TYPE := (SELECT id FROM dict_type WHERE dict_code='FILE_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_FILE_TYPE,'PDF','PDF',1,0,1,1,NOW(),NOW()),
(@DICT_FILE_TYPE,'XLSX','Excel(xlsx)',2,0,1,1,NOW(),NOW()),
(@DICT_FILE_TYPE,'CSV','CSV',3,0,1,1,NOW(),NOW()),
(@DICT_FILE_TYPE,'ZIP','ZIP',4,0,1,1,NOW(),NOW()),
(@DICT_FILE_TYPE,'PNG','PNG',5,0,1,1,NOW(),NOW()),
(@DICT_FILE_TYPE,'JPG','JPG',6,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 17) FILE_STATUS 文件状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('FILE_STATUS', '文件状态', '通用文件处理状态', 0, 1, 1, 1, 17, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_FILE_STATUS := (SELECT id FROM dict_type WHERE dict_code='FILE_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_FILE_STATUS,'UPLOADING','上传中',1,0,1,1,NOW(),NOW()),
(@DICT_FILE_STATUS,'READY','可用',2,0,1,1,NOW(),NOW()),
(@DICT_FILE_STATUS,'FAILED','失败',3,0,1,1,NOW(),NOW()),
(@DICT_FILE_STATUS,'DELETED','已删除',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 18) EXPORT_STATUS 导出状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('EXPORT_STATUS', '导出状态', '通用导出任务状态', 0, 1, 1, 1, 18, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_EXPORT_STATUS := (SELECT id FROM dict_type WHERE dict_code='EXPORT_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_EXPORT_STATUS,'PENDING','排队中',1,0,1,1,NOW(),NOW()),
(@DICT_EXPORT_STATUS,'RUNNING','执行中',2,0,1,1,NOW(),NOW()),
(@DICT_EXPORT_STATUS,'SUCCESS','成功',3,0,1,1,NOW(),NOW()),
(@DICT_EXPORT_STATUS,'FAILED','失败',4,0,1,1,NOW(),NOW()),
(@DICT_EXPORT_STATUS,'CANCELED','已取消',5,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 19) IMPORT_STATUS 导入状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('IMPORT_STATUS', '导入状态', '通用导入任务状态', 0, 1, 1, 1, 19, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_IMPORT_STATUS := (SELECT id FROM dict_type WHERE dict_code='IMPORT_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_IMPORT_STATUS,'PENDING','排队中',1,0,1,1,NOW(),NOW()),
(@DICT_IMPORT_STATUS,'RUNNING','执行中',2,0,1,1,NOW(),NOW()),
(@DICT_IMPORT_STATUS,'SUCCESS','成功',3,0,1,1,NOW(),NOW()),
(@DICT_IMPORT_STATUS,'FAILED','失败',4,0,1,1,NOW(),NOW()),
(@DICT_IMPORT_STATUS,'CANCELED','已取消',5,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 20) TASK_STATUS 任务状态
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('TASK_STATUS', '任务状态', '通用任务执行状态', 0, 1, 1, 1, 20, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_TASK_STATUS := (SELECT id FROM dict_type WHERE dict_code='TASK_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_TASK_STATUS,'PENDING','待执行',1,0,1,1,NOW(),NOW()),
(@DICT_TASK_STATUS,'RUNNING','执行中',2,0,1,1,NOW(),NOW()),
(@DICT_TASK_STATUS,'SUCCESS','成功',3,0,1,1,NOW(),NOW()),
(@DICT_TASK_STATUS,'FAILED','失败',4,0,1,1,NOW(),NOW()),
(@DICT_TASK_STATUS,'PAUSED','已暂停',5,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 21) PROCESS_STATUS 流程状态（工作流/审批）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('PROCESS_STATUS', '流程状态', '通用流程状态', 0, 1, 1, 1, 21, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_PROCESS_STATUS := (SELECT id FROM dict_type WHERE dict_code='PROCESS_STATUS' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_PROCESS_STATUS,'DRAFT','草稿',1,0,1,1,NOW(),NOW()),
(@DICT_PROCESS_STATUS,'RUNNING','运行中',2,0,1,1,NOW(),NOW()),
(@DICT_PROCESS_STATUS,'COMPLETED','已完成',3,0,1,1,NOW(),NOW()),
(@DICT_PROCESS_STATUS,'TERMINATED','已终止',4,0,1,1,NOW(),NOW()),
(@DICT_PROCESS_STATUS,'SUSPENDED','已挂起',5,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 22) SOURCE_TYPE 来源类型（调用来源）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('SOURCE_TYPE', '来源类型', 'WEB/API/JOB/SDK 调用来源', 0, 1, 1, 1, 22, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_SOURCE_TYPE := (SELECT id FROM dict_type WHERE dict_code='SOURCE_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_SOURCE_TYPE,'WEB','Web',1,0,1,1,NOW(),NOW()),
(@DICT_SOURCE_TYPE,'API','API',2,0,1,1,NOW(),NOW()),
(@DICT_SOURCE_TYPE,'JOB','定时任务',3,0,1,1,NOW(),NOW()),
(@DICT_SOURCE_TYPE,'SDK','SDK',4,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 23) CHANNEL_TYPE 渠道类型
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('CHANNEL_TYPE', '渠道类型', 'PC/MOBILE/THIRD_PARTY', 0, 1, 1, 1, 23, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_CHANNEL_TYPE := (SELECT id FROM dict_type WHERE dict_code='CHANNEL_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_CHANNEL_TYPE,'PC','PC',1,0,1,1,NOW(),NOW()),
(@DICT_CHANNEL_TYPE,'MOBILE','移动端',2,0,1,1,NOW(),NOW()),
(@DICT_CHANNEL_TYPE,'THIRD_PARTY','第三方',3,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 24) TIME_UNIT 时间单位
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('TIME_UNIT', '时间单位', '秒/分/时/天/周/月/年', 0, 1, 1, 1, 24, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_TIME_UNIT := (SELECT id FROM dict_type WHERE dict_code='TIME_UNIT' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_TIME_UNIT,'SECOND','秒',1,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'MINUTE','分钟',2,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'HOUR','小时',3,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'DAY','天',4,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'WEEK','周',5,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'MONTH','月',6,0,1,1,NOW(),NOW()),
(@DICT_TIME_UNIT,'YEAR','年',7,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

-- ---------------------------------------------------------
-- 25) DATE_TYPE 日期类型（自然日/工作日）
-- ---------------------------------------------------------
INSERT INTO dict_type (dict_code, dict_name, description, tenant_id, is_builtin, builtin_locked, enabled, sort_order, created_at, updated_at)
VALUES ('DATE_TYPE', '日期类型', '自然日/工作日', 0, 1, 1, 1, 25, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    dict_name=VALUES(dict_name), 
    description=VALUES(description), 
    enabled=VALUES(enabled),
    sort_order=VALUES(sort_order),
    is_builtin=VALUES(is_builtin),
    builtin_locked=VALUES(builtin_locked),
    updated_at=NOW();

SET @DICT_DATE_TYPE := (SELECT id FROM dict_type WHERE dict_code='DATE_TYPE' AND tenant_id=0 LIMIT 1);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id, is_builtin, enabled, created_at, updated_at)
VALUES
(@DICT_DATE_TYPE,'NATURAL','自然日',1,0,1,1,NOW(),NOW()),
(@DICT_DATE_TYPE,'WORKING','工作日',2,0,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE 
    label=VALUES(label), 
    sort_order=VALUES(sort_order), 
    enabled=VALUES(enabled),
    is_builtin=VALUES(is_builtin),
    updated_at=NOW();

COMMIT;

/* 可选：快速查看初始化结果
SELECT t.dict_code, t.dict_name, i.value, i.label, i.sort_order
FROM dict_type t
JOIN dict_item i ON i.dict_type_id=t.id
WHERE t.tenant_id=0
ORDER BY t.dict_code, i.sort_order;
*/

