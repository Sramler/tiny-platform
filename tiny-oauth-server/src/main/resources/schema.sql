-- 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `account_non_expired` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否未过期',
    `account_non_locked` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否未锁定',
    `credentials_non_expired` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '密码是否未过期',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `last_login_at` TIMESTAMP NULL COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP地址',
    `last_login_device` VARCHAR(200) DEFAULT NULL COMMENT '最后登录设备',
    `failed_login_count` INT NOT NULL DEFAULT 0 COMMENT '失败登录次数',
    `last_failed_login_at` TIMESTAMP NULL COMMENT '最后失败登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 创建角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    `code` VARCHAR(50) NOT NULL COMMENT '角色标识',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `builtin` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否内置',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_role_code` (`code`),
    UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 创建用户-角色关联表
CREATE TABLE IF NOT EXISTS `user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_role_user_id` (`user_id`),
    KEY `idx_user_role_role_id` (`role_id`),
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- 创建资源表（统一的权限资源表，包含菜单和API）
CREATE TABLE IF NOT EXISTS `resource` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '权限资源名（后端内部识别名）',
    `url` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '前端路由路径',
    `uri` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '后端 API 路径',
    `method` VARCHAR(10) NOT NULL DEFAULT '' COMMENT 'HTTP 方法',
    `icon` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '菜单图标',
    `show_icon` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否显示图标',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `component` VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Vue 路由组件路径',
    `redirect` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '重定向地址（父菜单使用）',
    `hidden` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在侧边栏隐藏',
    `keep_alive` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否缓存页面',
    `title` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '前端菜单显示标题',
    `permission` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '权限标识，用于前端控制',
    `type` TINYINT NOT NULL DEFAULT 0 COMMENT '资源类型：0-目录，1-菜单，2-按钮，3-接口',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父资源ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
    UNIQUE KEY `uk_resource_name` (`name`) COMMENT '资源名称唯一约束，防止重复插入',
    KEY `idx_resource_parent_id` (`parent_id`),
    KEY `idx_resource_type` (`type`),
    KEY `idx_resource_sort` (`sort`),
    KEY `idx_resource_hidden` (`hidden`),
    FOREIGN KEY (`parent_id`) REFERENCES `resource` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限资源表';

-- 创建角色-资源关联表
CREATE TABLE IF NOT EXISTS `role_resource` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `resource_id` BIGINT NOT NULL COMMENT '资源ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_role_resource` (`role_id`, `resource_id`),
    KEY `idx_role_resource_role_id` (`role_id`),
    KEY `idx_role_resource_resource_id` (`resource_id`),
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`resource_id`) REFERENCES `resource` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源关联表';

-- 创建用户认证方法表
CREATE TABLE IF NOT EXISTS `user_authentication_method` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `authentication_provider` VARCHAR(50) NOT NULL COMMENT '认证提供者',
    `authentication_type` VARCHAR(50) NOT NULL COMMENT '认证类型',
    `authentication_configuration` JSON NOT NULL COMMENT '认证配置',
    `is_primary_method` BOOLEAN DEFAULT FALSE COMMENT '是否主要认证方法',
    `is_method_enabled` BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    `authentication_priority` INT DEFAULT 0 COMMENT '认证优先级',
    `last_verified_at` TIMESTAMP NULL COMMENT '最后验证时间',
    `last_verified_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后验证IP地址',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expires_at` TIMESTAMP NULL COMMENT '过期时间',
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_user_auth_method` (`user_id`, `authentication_provider`, `authentication_type`),
    KEY `idx_user_provider` (`user_id`, `authentication_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证方法表';

-- 创建用户认证审计表
CREATE TABLE IF NOT EXISTS `user_authentication_audit` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NULL COMMENT '对应 user.id，可为空（匿名或外部登录）',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名冗余字段，便于查询',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型，如 LOGIN / LOGOUT / MFA_BIND / TOKEN_ISSUE',
    `success` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '事件是否成功',
    `authentication_provider` VARCHAR(50) NULL COMMENT '认证提供者，如 LOCAL / GITHUB / GOOGLE / LDAP',
    `authentication_factor` VARCHAR(50) NULL COMMENT '认证因子类型，如 PASSWORD / TOTP / OAUTH2 / EMAIL / MFA',
    `ip_address` VARCHAR(45) NULL COMMENT '登录 IP 地址',
    `user_agent` VARCHAR(255) NULL COMMENT '客户端 User-Agent 信息',
    `session_id` VARCHAR(128) NULL COMMENT '会话 ID，可选',
    `token_id` VARCHAR(128) NULL COMMENT 'OAuth2 / OIDC token ID，可选',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_username` (`username`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证审计表';

-- 创建用户头像表
CREATE TABLE IF NOT EXISTS `user_avatar` (
    `user_id` BIGINT NOT NULL PRIMARY KEY COMMENT '用户ID，与user.id一对一关系',
    `content_type` VARCHAR(128) NOT NULL COMMENT 'MIME类型，如image/png, image/jpeg, image/webp',
    `filename` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    `file_size` INT UNSIGNED NOT NULL COMMENT '文件大小（字节），限制在1MB以内',
    `data` LONGBLOB NOT NULL COMMENT '头像二进制数据，使用LONGBLOB支持最大4GB',
    `content_hash` CHAR(64) DEFAULT NULL COMMENT 'SHA-256哈希值（64字符），用于去重、校验和缓存',
    `uploaded_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    KEY `idx_content_hash` (`content_hash`) COMMENT '索引：用于去重查询',
    CONSTRAINT `chk_file_size` CHECK (`file_size` <= 1048576)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户头像表，文件大小限制：最大1MB'; 

-- 创建 HTTP 请求日志表
CREATE TABLE IF NOT EXISTS `http_request_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增ID',
    `trace_id` CHAR(32) NOT NULL COMMENT '全链路 trace id（十六进制）',
    `span_id` CHAR(32) DEFAULT NULL COMMENT '当前 span id（可选）',
    `request_id` CHAR(32) NOT NULL COMMENT '本服务内请求ID（唯一）',
    `service_name` VARCHAR(64) NOT NULL COMMENT '服务名，如 user-service',
    `env` VARCHAR(32) NOT NULL COMMENT '环境：dev/test/pre/prod',
    `module` VARCHAR(64) DEFAULT NULL COMMENT '业务模块/分组，如 order/payment',
    `user_id` VARCHAR(128) DEFAULT NULL COMMENT '用户ID（若未登录则空）',
    `client_ip` VARCHAR(45) DEFAULT NULL COMMENT '客户端IP（支持 IPv6）',
    `host` VARCHAR(128) DEFAULT NULL COMMENT '请求的 Host/域名',
    `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent（建议截断）',
    `http_version` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP/1.1, HTTP/2 等',
    `method` VARCHAR(10) NOT NULL COMMENT 'GET/POST/PUT/DELETE 等',
    `path_template` VARCHAR(256) NOT NULL COMMENT '路径模板，如 /orders/{id}',
    `raw_path` VARCHAR(1024) DEFAULT NULL COMMENT '原始请求路径，如 /orders/123?x=1',
    `query_string` VARCHAR(1024) DEFAULT NULL COMMENT '原始 query string（脱敏/限长）',
    `request_size` BIGINT DEFAULT NULL COMMENT '请求体大小（字节）',
    `response_size` BIGINT DEFAULT NULL COMMENT '响应体大小（字节）',
    `status` SMALLINT DEFAULT NULL COMMENT 'HTTP 状态码',
    `success` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功：1=成功，0=失败',
    `duration_ms` INT DEFAULT NULL COMMENT '耗时（毫秒）',
    `error` VARCHAR(512) DEFAULT NULL COMMENT '错误摘要（message），不要存堆栈',
    `request_body` MEDIUMTEXT DEFAULT NULL COMMENT '请求体（按需开启；脱敏+限长）',
    `response_body` MEDIUMTEXT DEFAULT NULL COMMENT '响应体（按需开启；脱敏+限长）',
    `request_at` TIMESTAMP NOT NULL COMMENT '请求发生时间',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志写入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_service_env_created` (`service_name`, `env`, `created_at`),
    KEY `idx_path_template_created` (`path_template`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_trace` (`trace_id`),
    KEY `idx_request_at` (`request_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='HTTP 请求/响应日志（面向数据分析，body 可选，注意脱敏与限长）';

-- 创建导出任务表
CREATE TABLE IF NOT EXISTS `export_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '任务唯一ID（UUID）',
    `user_id` VARCHAR(64) NOT NULL COMMENT '任务发起用户ID',
    `username` VARCHAR(128) DEFAULT NULL COMMENT '任务发起用户名（可选）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
    `progress` INT DEFAULT 0 COMMENT '导出进度 0~100（动态更新）',
    `total_rows` BIGINT DEFAULT NULL COMMENT '总行数（可为 NULL 表示未知或估算值）',
    `processed_rows` BIGINT DEFAULT 0 COMMENT '已处理行数（Slice/Iterator 用这个）',
    `sheet_count` INT DEFAULT 1 COMMENT '总 Sheet 数（用于多Sheet导出）',
    `file_path` VARCHAR(512) DEFAULT NULL COMMENT '导出文件本地路径（或 OSS 对象key）',
    `download_url` VARCHAR(1024) DEFAULT NULL COMMENT '可选：文件下载URL（通常是 OSS 预签名URL）',
    `error_msg` TEXT COMMENT '错误信息（失败时记录）',
    `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误编码（可选，便于分类统计）',
    `query_params` JSON DEFAULT NULL COMMENT '导出查询参数（JSON 持久化，用于审计和重跑）',
    `worker_id` VARCHAR(64) DEFAULT NULL COMMENT '执行任务的 worker 实例ID',
    `attempt` INT NOT NULL DEFAULT 0 COMMENT '任务尝试次数',
    `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最近心跳时间（检测僵尸任务）',
    `expire_at` DATETIME DEFAULT NULL COMMENT '文件失效时间（定时清理任务用）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出任务表';

-- 导出教学示例：用量/账单混合场景
CREATE TABLE IF NOT EXISTS `demo_export_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_code` VARCHAR(64) NOT NULL COMMENT '租户/客户编码',
    `usage_date` DATE NOT NULL COMMENT '用量日期',
    `product_code` VARCHAR(64) NOT NULL COMMENT '产品编码',
    `product_name` VARCHAR(128) NOT NULL COMMENT '产品名称',
    `plan_tier` VARCHAR(32) NOT NULL DEFAULT 'standard' COMMENT '套餐档位',
    `region` VARCHAR(64) DEFAULT NULL COMMENT '区域/可用区',
    `usage_qty` DECIMAL(18,4) NOT NULL COMMENT '用量数量（支持小数）',
    `unit` VARCHAR(32) NOT NULL COMMENT '用量单位，例如 GB、req',
    `unit_price` DECIMAL(12,4) NOT NULL COMMENT '单价',
    `amount` DECIMAL(14,4) NOT NULL COMMENT '小计金额',
    `currency` CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `tax_rate` DECIMAL(5,4) DEFAULT 0 COMMENT '税率，如 0.0600=6%',
    `is_billable` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否计费',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UNBILLED' COMMENT '状态：UNBILLED/BILLED/ADJUSTED',
    `metadata` JSON DEFAULT NULL COMMENT '扩展信息（JSON）',
    `description` TEXT COMMENT '描述信息（TextArea 多行文本）',
    `priority` VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '优先级（Radio 单选）：LOW/MEDIUM/HIGH/URGENT',
    `tags` JSON COMMENT '标签（Checkbox 多选，JSON 数组）',
    `usage_time` TIME COMMENT '使用时间（TimePicker 时间选择）',
    `quality_score` TINYINT DEFAULT 0 COMMENT '质量评分（Rate 评分，0-5）',
    `discount_percentage` DECIMAL(5,2) DEFAULT 0 COMMENT '折扣百分比（Slider 滑块，0-100）',
    `category_path` VARCHAR(200) COMMENT '分类路径（Cascader 级联选择，如：/云服务/存储/对象存储）',
    `department_id` BIGINT COMMENT '部门ID（TreeSelect 树形选择）',
    `customer_name` VARCHAR(128) COMMENT '客户名称（AutoComplete 自动完成）',
    `theme_color` VARCHAR(7) DEFAULT '#1890ff' COMMENT '主题颜色（ColorPicker 颜色选择器，HEX格式）',
    `api_key` VARCHAR(128) COMMENT 'API密钥（Input.Password 密码输入）',
    `search_keyword` VARCHAR(128) COMMENT '搜索关键词（Input.Search 搜索输入）',
    `start_date` DATE COMMENT '开始日期（RangePicker 日期范围开始）',
    `end_date` DATE COMMENT '结束日期（RangePicker 日期范围结束）',
    `selected_features` JSON COMMENT '选中的功能（Select multiple 多选下拉，JSON 数组）',
    `min_value` DECIMAL(18,4) COMMENT '最小值（数字范围输入）',
    `max_value` DECIMAL(18,4) COMMENT '最大值（数字范围输入）',
    `start_time` TIME COMMENT '开始时间（时间范围开始）',
    `end_time` TIME COMMENT '结束时间（时间范围结束）',
    `payment_method` VARCHAR(32) COMMENT '支付方式（Radio.Group 单选组）：ALIPAY/WECHAT/CREDIT_CARD/BANK_TRANSFER',
    `notes` TEXT COMMENT '备注（TextArea 文本域）',
    `attachment_info` JSON COMMENT '附件信息（Upload 文件上传，JSON 格式存储文件信息）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_demo_usage_tenant_date` (`tenant_code`, `usage_date`),
    KEY `idx_demo_usage_product` (`product_code`),
    KEY `idx_demo_usage_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出教学示例表：用量/账单型数据（扩展版，覆盖所有表单组件类型）';

-- 生成 demo_export_usage 测试数据的存储过程
-- 注意：
-- 下面这段存储过程定义在 Spring Boot 启动时通过 schema.sql 执行会有两个问题：
-- 1）`DROP PROCEDURE` 会在每次重启时删除已有存储过程；
-- 2）`DELIMITER` 语法 JDBC 不支持，导致 `CREATE PROCEDURE` 无法成功执行。
-- 结果就是：每次应用重启后，存储过程都会被删掉且无法重新创建，只能手工在 Navicat 中执行。
-- 为避免这个问题，我们不再由 Spring 自动执行这段定义，而是建议在数据库里手动执行一次并持久化。
-- 如需重新创建或更新存储过程，请在 Navicat 中执行下方 SQL（去掉前面的注释符号）：
-- DROP PROCEDURE IF EXISTS `sp_generate_demo_export_usage`;
-- DELIMITER $$
-- CREATE PROCEDURE `sp_generate_demo_export_usage`(
--     IN p_days INT,
--     IN p_rows_per_day INT,
--     IN p_target_rows INT,
--     IN p_clear_existing TINYINT(1)
-- )
-- BEGIN
--     DECLARE d INT DEFAULT 0;
--     DECLARE i INT;
--     DECLARE usage_dt DATE;
--     DECLARE tenant_code VARCHAR(64);
--     DECLARE product_code VARCHAR(64);
--     DECLARE product_name VARCHAR(128);
--     DECLARE plan_tier VARCHAR(32);
--     DECLARE region VARCHAR(64);
--     DECLARE unit VARCHAR(32);
--     DECLARE unit_price DECIMAL(12,4);
--     DECLARE qty DECIMAL(18,4);
--     DECLARE status_val VARCHAR(20);
--     DECLARE currency_val CHAR(3);
--     DECLARE tax_val DECIMAL(5,4);
--     DECLARE target_rows INT;
--     DECLARE inserted_rows INT DEFAULT 0;
--
--     -- 如果 p_clear_existing 为 1（true），则先清空表中的所有数据
--     IF p_clear_existing IS NOT NULL AND p_clear_existing = 1 THEN
--         TRUNCATE TABLE `demo_export_usage`;
--     END IF;
--
--     IF p_rows_per_day IS NULL OR p_rows_per_day <= 0 THEN SET p_rows_per_day = 2000; END IF;
--     IF p_days IS NULL OR p_days <= 0 THEN SET p_days = 7; END IF;
--
--     IF p_target_rows IS NOT NULL AND p_target_rows > 0 THEN
--         SET target_rows = p_target_rows;
--     ELSE
--         SET target_rows = p_days * p_rows_per_day;
--     END IF;
--
--     WHILE inserted_rows < target_rows DO
--         SET d = inserted_rows DIV p_rows_per_day;
--         SET usage_dt = CURDATE() - INTERVAL d DAY;
--         SET i = 0;
--         WHILE i < p_rows_per_day AND inserted_rows < target_rows DO
--             SET tenant_code = ELT(1 + FLOOR(RAND() * 3), 'tenant-alpha', 'tenant-beta', 'tenant-gamma');
--             SET product_code = ELT(1 + FLOOR(RAND() * 5), 'cdn', 'oss', 'api', 'mq', 'db');
--             SET product_name = CASE product_code
--                 WHEN 'cdn' THEN 'CDN 流量'
--                 WHEN 'oss' THEN '对象存储'
--                 WHEN 'api' THEN 'API 调用'
--                 WHEN 'mq'  THEN '消息队列'
--                 ELSE '托管数据库'
--             END;
--             SET plan_tier = ELT(1 + FLOOR(RAND() * 3), 'basic', 'standard', 'enterprise');
--             SET region = ELT(1 + FLOOR(RAND() * 4), 'cn-north-1', 'ap-southeast-1', 'us-west-1', 'eu-central-1');
--             SET unit = CASE product_code
--                 WHEN 'cdn' THEN 'GB'
--                 WHEN 'oss' THEN 'GB'
--                 WHEN 'api' THEN 'req'
--                 WHEN 'mq'  THEN 'msg'
--                 ELSE 'hours'
--             END;
--             SET unit_price = CASE product_code
--                 WHEN 'cdn' THEN 0.1200
--                 WHEN 'oss' THEN 0.0800
--                 WHEN 'api' THEN 0.0008
--                 WHEN 'mq'  THEN 0.0005
--                 ELSE 2.8000
--             END;
--             SET qty = ROUND(
--                 CASE unit
--                     WHEN 'GB' THEN (50 + RAND() * 150)
--                     WHEN 'req' THEN (50000 + RAND() * 150000)
--                     WHEN 'msg' THEN (10000 + RAND() * 80000)
--                     ELSE (10 + RAND() * 80)
--                 END, 4);
--             SET currency_val = ELT(1 + FLOOR(RAND() * 2), 'CNY', 'USD');
--             SET tax_val = CASE currency_val WHEN 'USD' THEN 0.0725 ELSE 0.0600 END;
--             SET status_val = ELT(1 + FLOOR(RAND() * 3), 'UNBILLED', 'BILLED', 'ADJUSTED');
--
--             INSERT INTO `demo_export_usage` (
--                 `tenant_code`, `usage_date`, `product_code`, `product_name`, `plan_tier`, `region`,
--                 `usage_qty`, `unit`, `unit_price`, `amount`, `currency`, `tax_rate`, `is_billable`,
--                 `status`, `metadata`, `created_at`
--             ) VALUES (
--                 tenant_code,
--                 usage_dt,
--                 product_code,
--                 product_name,
--                 plan_tier,
--                 region,
--                 qty,
--                 unit,
--                 unit_price,
--                 ROUND(qty * unit_price, 4),
--                 currency_val,
--                 tax_val,
--                 ELT(1 + FLOOR(RAND() * 2), TRUE, FALSE),
--                 status_val,
--                 JSON_OBJECT(
--                     'run', CONCAT('day-', d, '-row-', i),
--                     'tier', plan_tier,
--                     'region', region
--                 ),
--                 NOW() - INTERVAL d DAY
--             );
--             SET i = i + 1;
--             SET inserted_rows = inserted_rows + 1;
--         END WHILE;
--     END WHILE;
-- END$$
-- DELIMITER ;

-- ========================================================================
-- 企业级 DAG 调度系统（带 scheduling_ 前缀、无外键）
-- 基于 Quartz + Spring Boot，支持多租户、版本化、分布式执行
-- ========================================================================

-- 1) scheduling_task_type：任务类型表
CREATE TABLE IF NOT EXISTS `scheduling_task_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID（多租户场景可用）',
  `code` VARCHAR(128) NOT NULL COMMENT '类型唯一编码（租户范围内唯一）',
  `name` VARCHAR(128) NOT NULL COMMENT '类型名称，用于展示',
  `description` TEXT DEFAULT NULL COMMENT '类型描述，说明用途与注意事项',
  `executor` VARCHAR(255) DEFAULT NULL COMMENT '执行器标识（如 Spring Bean 名、镜像引用、脚本路径）',
  `param_schema` JSON DEFAULT NULL COMMENT 'JSON Schema：参数校验规则，便于 UI 自动生成表单',
  `default_timeout_sec` INT DEFAULT 0 COMMENT '默认超时时间（秒），0 表示无限制',
  `default_max_retry` INT DEFAULT 0 COMMENT '默认最大重试次数，0 表示不重试',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用（1=启用，0=禁用）',
  `created_by` VARCHAR(128) DEFAULT NULL COMMENT '创建者标识',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheduling_task_type_tenant_code` (`tenant_id`,`code`),
  KEY `idx_scheduling_task_type_executor` (`executor`),
  KEY `idx_scheduling_task_type_enabled` (`enabled`),
  KEY `idx_scheduling_task_type_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务类型：定义任务能力/模板（无外键）';

-- 2) scheduling_task：任务实例定义表
CREATE TABLE IF NOT EXISTS `scheduling_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID（如适用）',
  `type_id` BIGINT NOT NULL COMMENT '引用 scheduling_task_type.id（应用层保证存在）',
  `code` VARCHAR(128) DEFAULT NULL COMMENT '业务编码（租户范围内可唯一标识）',
  `name` VARCHAR(128) NOT NULL COMMENT '任务名称，用于展示',
  `description` TEXT DEFAULT NULL COMMENT '任务描述，说明职责、注意事项',
  `params` JSON DEFAULT NULL COMMENT '默认参数模板（可以被 DAG 覆盖）',
  `timeout_sec` INT DEFAULT NULL COMMENT '任务超时时间（秒），若为空使用 task_type.default_timeout_sec',
  `max_retry` INT DEFAULT 0 COMMENT '最大重试次数，优先使用本字段，若为 0 则使用 task_type.default_max_retry',
  `retry_policy` JSON DEFAULT NULL COMMENT '重试策略（JSON），例如 { "strategy":"fixed","interval_sec":60 }',
  `concurrency_policy` VARCHAR(32) DEFAULT 'PARALLEL' COMMENT '并发策略：PARALLEL/SEQUENTIAL/SINGLETON/KEYED',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用该任务定义',
  `created_by` VARCHAR(128) DEFAULT NULL COMMENT '创建者',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_task_type_id` (`type_id`),
  KEY `idx_scheduling_task_tenant_code` (`tenant_id`,`code`),
  KEY `idx_scheduling_task_enabled` (`enabled`),
  KEY `idx_scheduling_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务定义表：参数化的任务实例模板（无外键）';

-- 3) scheduling_dag：DAG 主表
CREATE TABLE IF NOT EXISTS `scheduling_dag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'DAG 主键ID',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID（如适用）',
  `code` VARCHAR(128) DEFAULT NULL COMMENT 'DAG 编码（租户内唯一）',
  `name` VARCHAR(128) NOT NULL COMMENT 'DAG 名称',
  `description` TEXT DEFAULT NULL COMMENT 'DAG 描述',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用该 DAG（1=启用）',
  `created_by` VARCHAR(128) DEFAULT NULL COMMENT '创建者',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheduling_dag_tenant_code` (`tenant_id`,`code`),
  KEY `idx_scheduling_dag_enabled` (`enabled`),
  KEY `idx_scheduling_dag_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DAG 表：流程元数据（无外键）';

-- 4) scheduling_dag_version：DAG 版本表
CREATE TABLE IF NOT EXISTS `scheduling_dag_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本ID，自增',
  `dag_id` BIGINT NOT NULL COMMENT '引用 scheduling_dag.id（应用层保证存在）',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号，递增',
  `status` VARCHAR(32) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/ARCHIVED',
  `definition` JSON DEFAULT NULL COMMENT 'JSON：包含 nodes/edges/metadata，便于回滚与预览',
  `created_by` VARCHAR(128) DEFAULT NULL COMMENT '版本创建者',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
  `activated_at` DATETIME DEFAULT NULL COMMENT '激活时间（若 status=ACTIVE）',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_dag_version_dag` (`dag_id`),
  KEY `idx_scheduling_dag_version_status` (`status`),
  KEY `idx_scheduling_dag_version_dag_status` (`dag_id`, `status`),
  UNIQUE KEY `uk_scheduling_dag_version_dag_version` (`dag_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DAG 版本表：用于版本化 DAG 定义（无外键）';

-- 5) scheduling_dag_task：DAG 版本节点表
CREATE TABLE IF NOT EXISTS `scheduling_dag_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点主键ID',
  `dag_version_id` BIGINT NOT NULL COMMENT '引用 scheduling_dag_version.id（由业务保证一致性）',
  `node_code` VARCHAR(128) NOT NULL COMMENT '版本内唯一的节点编码（用于 edges 引用）',
  `task_id` BIGINT NOT NULL COMMENT '引用 scheduling_task.id（执行逻辑），应用层需保证存在',
  `name` VARCHAR(128) DEFAULT NULL COMMENT '节点显示名称',
  `override_params` JSON DEFAULT NULL COMMENT '节点覆盖的参数（优先级高于 task.params）',
  `timeout_sec` INT DEFAULT NULL COMMENT '节点级超时（秒）',
  `max_retry` INT DEFAULT NULL COMMENT '节点级最大重试',
  `parallel_group` VARCHAR(64) DEFAULT NULL COMMENT '并行组标识（同组可并行）',
  `meta` JSON DEFAULT NULL COMMENT '扩展字段，供 UI 或插件存放自定义信息',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scheduling_dag_task_version_node` (`dag_version_id`,`node_code`),
  KEY `idx_scheduling_dag_task_task` (`task_id`),
  KEY `idx_scheduling_dag_task_dag_version` (`dag_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DAG 节点表：版本化节点定义（无外键）';

-- 6) scheduling_dag_edge：DAG 边表
CREATE TABLE IF NOT EXISTS `scheduling_dag_edge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dag_version_id` BIGINT NOT NULL COMMENT '引用 scheduling_dag_version.id',
  `from_node_code` VARCHAR(128) NOT NULL COMMENT '上游节点编码',
  `to_node_code` VARCHAR(128) NOT NULL COMMENT '下游节点编码',
  `condition` JSON DEFAULT NULL COMMENT '可选条件表达式 JSON',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_dag_edge_version` (`dag_version_id`),
  KEY `idx_scheduling_dag_edge_from` (`from_node_code`),
  KEY `idx_scheduling_dag_edge_to` (`to_node_code`),
  KEY `idx_scheduling_dag_edge_version_from_to` (`dag_version_id`, `from_node_code`, `to_node_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DAG 边表：版本级依赖关系（无外键）';

-- 7) scheduling_dag_run：DAG 运行实例
CREATE TABLE IF NOT EXISTS `scheduling_dag_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `dag_id` BIGINT NOT NULL COMMENT '引用 scheduling_dag.id',
  `dag_version_id` BIGINT DEFAULT NULL COMMENT '引用当时使用的 scheduling_dag_version.id',
  `run_no` VARCHAR(128) DEFAULT NULL COMMENT '外部可见的 run 编号（幂等用）',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `trigger_type` VARCHAR(32) DEFAULT 'MANUAL' COMMENT '触发类型：MANUAL / SCHEDULE / RETRY',
  `triggered_by` VARCHAR(128) DEFAULT NULL COMMENT '触发人或触发器标识',
  `status` VARCHAR(32) DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/RUNNING/SUCCESS/FAILED/CANCELLED/PARTIAL_FAILED',
  `start_time` DATETIME DEFAULT NULL COMMENT '实际开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `metrics` JSON DEFAULT NULL COMMENT '聚合指标（可选）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_dag_run_dag` (`dag_id`),
  KEY `idx_scheduling_dag_run_status` (`status`),
  KEY `idx_scheduling_dag_run_dag_status` (`dag_id`, `status`),
  KEY `idx_scheduling_dag_run_created_at` (`created_at`),
  UNIQUE KEY `uk_scheduling_dag_run_run_no` (`run_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DAG 运行实例表：记录每次 DAG 触发与运行（无外键）';

-- 8) scheduling_task_instance：任务实例（调度队列项）
CREATE TABLE IF NOT EXISTS `scheduling_task_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '实例ID，自增',
  `dag_run_id` BIGINT DEFAULT NULL COMMENT '所属 scheduling_dag_run.id',
  `dag_id` BIGINT DEFAULT NULL COMMENT '所属 scheduling_dag.id',
  `dag_version_id` BIGINT DEFAULT NULL COMMENT '所属 scheduling_dag_version.id',
  `node_code` VARCHAR(128) DEFAULT NULL COMMENT '节点编码，对应 dag_version 中的 node_code',
  `task_id` BIGINT NOT NULL COMMENT '引用 scheduling_task.id',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `attempt_no` INT DEFAULT 1 COMMENT '本次尝试序号',
  `status` VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/RESERVED/RUNNING/SUCCESS/FAILED/SKIPPED',
  `scheduled_at` DATETIME DEFAULT NULL COMMENT '计划执行时间',
  `locked_by` VARCHAR(128) DEFAULT NULL COMMENT '被哪个 worker 锁定',
  `lock_time` DATETIME DEFAULT NULL COMMENT '锁定时间',
  `next_retry_at` DATETIME DEFAULT NULL COMMENT '下一次重试时间',
  `params` JSON DEFAULT NULL COMMENT '执行参数快照',
  `result` JSON DEFAULT NULL COMMENT '执行结果快照',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_task_instance_status` (`status`),
  KEY `idx_scheduling_task_instance_scheduled` (`scheduled_at`),
  KEY `idx_scheduling_task_instance_task` (`task_id`),
  KEY `idx_scheduling_task_instance_node` (`node_code`),
  KEY `idx_scheduling_task_instance_dag_run` (`dag_run_id`),
  KEY `idx_scheduling_task_instance_status_scheduled` (`status`, `scheduled_at`),
  KEY `idx_scheduling_task_instance_locked_by` (`locked_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务实例表：调度队列与 Worker 抢占（无外键）';

-- 9) scheduling_task_history：任务执行历史
CREATE TABLE IF NOT EXISTS `scheduling_task_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史ID，自增，不可修改',
  `task_instance_id` BIGINT DEFAULT NULL COMMENT '来源 scheduling_task_instance.id',
  `dag_run_id` BIGINT DEFAULT NULL COMMENT '所属 scheduling_dag_run.id',
  `dag_id` BIGINT DEFAULT NULL COMMENT '所属 scheduling_dag.id',
  `node_code` VARCHAR(128) DEFAULT NULL COMMENT '节点编码',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务 ID',
  `attempt_no` INT DEFAULT 1 COMMENT '执行尝试序号',
  `status` VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
  `params` JSON DEFAULT NULL COMMENT '执行参数快照',
  `result` JSON DEFAULT NULL COMMENT '执行结果快照',
  `error_message` TEXT DEFAULT NULL COMMENT '错误摘要',
  `stack_trace` LONGTEXT DEFAULT NULL COMMENT '完整堆栈',
  `log_path` VARCHAR(512) DEFAULT NULL COMMENT '日志存放路径',
  `worker_id` VARCHAR(128) DEFAULT NULL COMMENT '执行 worker 标识',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_task_history_task` (`task_id`),
  KEY `idx_scheduling_task_history_dagrun` (`dag_run_id`),
  KEY `idx_scheduling_task_history_status` (`status`),
  KEY `idx_scheduling_task_history_task_instance` (`task_instance_id`),
  KEY `idx_scheduling_task_history_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务执行历史表（无外键）';

-- 10) scheduling_audit：操作审计表
CREATE TABLE IF NOT EXISTS `scheduling_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `object_type` VARCHAR(64) NOT NULL COMMENT '对象类型，如 dag/task/task_instance/task_history',
  `object_id` VARCHAR(128) DEFAULT NULL COMMENT '对象ID或业务标识',
  `action` VARCHAR(64) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/TRIGGER/RETRY/CANCEL/ACTIVATE',
  `performed_by` VARCHAR(128) DEFAULT NULL COMMENT '执行者',
  `detail` JSON DEFAULT NULL COMMENT '操作详情或快照',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_audit_object` (`object_type`,`object_id`),
  KEY `idx_scheduling_audit_action` (`action`),
  KEY `idx_scheduling_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审计表（无外键）';

-- 11) scheduling_task_param：任务默认参数表（可选，兼容旧系统）
CREATE TABLE IF NOT EXISTS `scheduling_task_param` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` BIGINT NOT NULL COMMENT '引用 scheduling_task.id',
  `param_key` VARCHAR(128) NOT NULL COMMENT '参数名',
  `param_value` TEXT DEFAULT NULL COMMENT '参数默认值',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_scheduling_task_param_task` (`task_id`),
  UNIQUE KEY `uk_scheduling_task_param_task_key` (`task_id`, `param_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务参数表（键值对形式，兼容旧系统需求）';

-- ========================================================================
-- Quartz 调度器数据库表（JDBC JobStore 必需）
-- 基于 Quartz 2.x/3.x 标准表结构，用于持久化 Job 和 Trigger
-- ========================================================================

-- QRTZ_JOB_DETAILS：存储 Job 详情
CREATE TABLE IF NOT EXISTS `QRTZ_JOB_DETAILS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `JOB_NAME` VARCHAR(190) NOT NULL COMMENT 'Job 名称',
  `JOB_GROUP` VARCHAR(190) NOT NULL COMMENT 'Job 组名',
  `DESCRIPTION` VARCHAR(250) DEFAULT NULL COMMENT 'Job 描述',
  `JOB_CLASS_NAME` VARCHAR(250) NOT NULL COMMENT 'Job 实现类全限定名',
  `IS_DURABLE` VARCHAR(1) NOT NULL COMMENT '是否持久化（Y/N）',
  `IS_NONCONCURRENT` VARCHAR(1) NOT NULL COMMENT '是否非并发（Y/N）',
  `IS_UPDATE_DATA` VARCHAR(1) NOT NULL COMMENT '是否更新数据（Y/N）',
  `REQUESTS_RECOVERY` VARCHAR(1) NOT NULL COMMENT '是否请求恢复（Y/N）',
  `JOB_DATA` BLOB DEFAULT NULL COMMENT 'Job 数据（BLOB）',
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz Job 详情表';

-- QRTZ_TRIGGERS：存储 Trigger 信息
CREATE TABLE IF NOT EXISTS `QRTZ_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `JOB_NAME` VARCHAR(190) NOT NULL COMMENT '关联的 Job 名称',
  `JOB_GROUP` VARCHAR(190) NOT NULL COMMENT '关联的 Job 组名',
  `DESCRIPTION` VARCHAR(250) DEFAULT NULL COMMENT 'Trigger 描述',
  `NEXT_FIRE_TIME` BIGINT(13) DEFAULT NULL COMMENT '下次触发时间（时间戳）',
  `PREV_FIRE_TIME` BIGINT(13) DEFAULT NULL COMMENT '上次触发时间（时间戳）',
  `PRIORITY` INTEGER DEFAULT NULL COMMENT '优先级',
  `TRIGGER_STATE` VARCHAR(16) NOT NULL COMMENT 'Trigger 状态（WAITING/ACQUIRED/EXECUTING/COMPLETE/PAUSED/BLOCKED/ERROR/DELETED）',
  `TRIGGER_TYPE` VARCHAR(8) NOT NULL COMMENT 'Trigger 类型（CRON/SIMPLE/BLOB）',
  `START_TIME` BIGINT(13) NOT NULL COMMENT '开始时间（时间戳）',
  `END_TIME` BIGINT(13) DEFAULT NULL COMMENT '结束时间（时间戳）',
  `CALENDAR_NAME` VARCHAR(190) DEFAULT NULL COMMENT '关联的日历名称',
  `MISFIRE_INSTR` SMALLINT(2) DEFAULT NULL COMMENT 'Misfire 策略',
  `JOB_DATA` BLOB DEFAULT NULL COMMENT 'Trigger 数据（BLOB）',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_J` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_C` (`SCHED_NAME`,`CALENDAR_NAME`),
  KEY `IDX_QRTZ_T_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_STATE` (`SCHED_NAME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_STATE` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_G_STATE` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NEXT_FIRE_TIME` (`SCHED_NAME`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST` (`SCHED_NAME`,`TRIGGER_STATE`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  CONSTRAINT `QRTZ_TRIGGERS_IBFK_1` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `QRTZ_JOB_DETAILS` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz Trigger 表';

-- QRTZ_SIMPLE_TRIGGERS：简单触发器（固定间隔触发）
CREATE TABLE IF NOT EXISTS `QRTZ_SIMPLE_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `REPEAT_COUNT` BIGINT(7) NOT NULL COMMENT '重复次数（-1 表示无限）',
  `REPEAT_INTERVAL` BIGINT(12) NOT NULL COMMENT '重复间隔（毫秒）',
  `TIMES_TRIGGERED` BIGINT(10) NOT NULL COMMENT '已触发次数',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_IBFK_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 简单触发器表';

-- QRTZ_CRON_TRIGGERS：Cron 触发器（基于 Cron 表达式）
CREATE TABLE IF NOT EXISTS `QRTZ_CRON_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `CRON_EXPRESSION` VARCHAR(120) NOT NULL COMMENT 'Cron 表达式',
  `TIME_ZONE_ID` VARCHAR(80) DEFAULT NULL COMMENT '时区 ID',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_CRON_TRIGGERS_IBFK_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz Cron 触发器表';

-- QRTZ_SIMPROP_TRIGGERS：属性触发器（支持更复杂的触发规则）
CREATE TABLE IF NOT EXISTS `QRTZ_SIMPROP_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `STR_PROP_1` VARCHAR(512) DEFAULT NULL COMMENT '字符串属性 1',
  `STR_PROP_2` VARCHAR(512) DEFAULT NULL COMMENT '字符串属性 2',
  `STR_PROP_3` VARCHAR(512) DEFAULT NULL COMMENT '字符串属性 3',
  `INT_PROP_1` INT DEFAULT NULL COMMENT '整数属性 1',
  `INT_PROP_2` INT DEFAULT NULL COMMENT '整数属性 2',
  `LONG_PROP_1` BIGINT DEFAULT NULL COMMENT '长整数属性 1',
  `LONG_PROP_2` BIGINT DEFAULT NULL COMMENT '长整数属性 2',
  `DEC_PROP_1` NUMERIC(13,4) DEFAULT NULL COMMENT '小数属性 1',
  `DEC_PROP_2` NUMERIC(13,4) DEFAULT NULL COMMENT '小数属性 2',
  `BOOL_PROP_1` VARCHAR(1) DEFAULT NULL COMMENT '布尔属性 1',
  `BOOL_PROP_2` VARCHAR(1) DEFAULT NULL COMMENT '布尔属性 2',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_IBFK_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 属性触发器表';

-- QRTZ_BLOB_TRIGGERS：Blob 触发器（用于序列化的自定义触发器）
CREATE TABLE IF NOT EXISTS `QRTZ_BLOB_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `BLOB_DATA` BLOB DEFAULT NULL COMMENT '序列化的 Trigger 数据',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_BLOB_TRIGGERS` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `QRTZ_BLOB_TRIGGERS_IBFK_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz Blob 触发器表';

-- QRTZ_CALENDARS：日历表（用于排除特定日期）
CREATE TABLE IF NOT EXISTS `QRTZ_CALENDARS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `CALENDAR_NAME` VARCHAR(190) NOT NULL COMMENT '日历名称',
  `CALENDAR` BLOB NOT NULL COMMENT '序列化的日历对象',
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 日历表';

-- QRTZ_PAUSED_TRIGGER_GRPS：暂停的触发器组
CREATE TABLE IF NOT EXISTS `QRTZ_PAUSED_TRIGGER_GRPS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 暂停的触发器组表';

-- QRTZ_FIRED_TRIGGERS：正在执行的触发器（运行时信息）
CREATE TABLE IF NOT EXISTS `QRTZ_FIRED_TRIGGERS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `ENTRY_ID` VARCHAR(95) NOT NULL COMMENT '条目 ID（唯一标识）',
  `TRIGGER_NAME` VARCHAR(190) NOT NULL COMMENT 'Trigger 名称',
  `TRIGGER_GROUP` VARCHAR(190) NOT NULL COMMENT 'Trigger 组名',
  `INSTANCE_NAME` VARCHAR(190) NOT NULL COMMENT '调度器实例名称',
  `FIRED_TIME` BIGINT(13) NOT NULL COMMENT '触发时间（时间戳）',
  `SCHED_TIME` BIGINT(13) NOT NULL COMMENT '计划执行时间（时间戳）',
  `PRIORITY` INTEGER NOT NULL COMMENT '优先级',
  `STATE` VARCHAR(16) NOT NULL COMMENT '状态（ACQUIRED/EXECUTING/COMPLETE/BLOCKED/ERROR）',
  `JOB_NAME` VARCHAR(190) DEFAULT NULL COMMENT '关联的 Job 名称',
  `JOB_GROUP` VARCHAR(190) DEFAULT NULL COMMENT '关联的 Job 组名',
  `IS_NONCONCURRENT` VARCHAR(1) DEFAULT NULL COMMENT '是否非并发（Y/N）',
  `REQUESTS_RECOVERY` VARCHAR(1) DEFAULT NULL COMMENT '是否请求恢复（Y/N）',
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_TG` (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 正在执行的触发器表';

-- QRTZ_SCHEDULER_STATE：调度器状态（集群模式使用）
CREATE TABLE IF NOT EXISTS `QRTZ_SCHEDULER_STATE` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `INSTANCE_NAME` VARCHAR(190) NOT NULL COMMENT '调度器实例名称',
  `LAST_CHECKIN_TIME` BIGINT(13) NOT NULL COMMENT '最后检查时间（时间戳）',
  `CHECKIN_INTERVAL` BIGINT(13) NOT NULL COMMENT '检查间隔（毫秒）',
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 调度器状态表（集群模式）';

-- QRTZ_LOCKS：锁表（集群模式使用，防止并发冲突）
CREATE TABLE IF NOT EXISTS `QRTZ_LOCKS` (
  `SCHED_NAME` VARCHAR(120) NOT NULL COMMENT '调度器名称',
  `LOCK_NAME` VARCHAR(40) NOT NULL COMMENT '锁名称',
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quartz 锁表（集群模式）';