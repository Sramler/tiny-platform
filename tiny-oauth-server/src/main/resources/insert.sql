-- 为user表添加最后登录时间字段
ALTER TABLE user ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间';

-- 插入角色数据
INSERT INTO role (name, description) VALUES
('ROLE_ADMIN', '管理员'),
('ROLE_USER', '普通用户'),
('ROLE_GUEST', '访客');

-- 插入用户数据
INSERT INTO user (username, nickname, enabled, account_non_expired, account_non_locked, credentials_non_expired, last_login_at) VALUES
('admin', '管理员', true, true, true, true, '2024-06-25 10:00:00'),
('user001', '用户1', true, true, true, true, '2024-06-24 15:30:00'),
('user002', '用户2', true, true, true, true, '2024-06-23 14:20:00'),
('user003', '用户3', false, true, true, true, '2024-06-22 09:15:00'),
('user004', '用户4', true, false, true, true, '2024-06-21 16:45:00'),
('user005', '用户5', true, true, false, true, '2024-06-20 11:30:00'),
('user006', '用户6', true, true, true, false, '2024-06-19 13:20:00'),
('user007', '用户7', true, true, true, true, '2024-06-18 08:45:00'),
('user008', '用户8', false, false, true, true, '2024-06-17 17:10:00'),
('user009', '用户9', true, true, false, false, '2024-06-16 12:30:00'),
('user010', '用户10', false, true, true, false, '2024-06-15 10:20:00');

-- 初始化密码认证方法
INSERT INTO user_authentication_method (
    user_id,
    authentication_provider,
    authentication_type,
    authentication_configuration,
    is_primary_method,
    is_method_enabled,
    authentication_priority,
    created_at,
    updated_at
)
SELECT
    u.id,
    'LOCAL',
    'PASSWORD',
    JSON_OBJECT(
        'password', '{bcrypt}$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa',
        'password_changed_at', DATE_FORMAT(NOW(), '%Y-%m-%dT%H:%i:%sZ'),
        'hash_algorithm', 'bcrypt',
        'password_version', 1,
        'created_by', 'insert.sql'
    ),
    TRUE,
    TRUE,
    0,
    NOW(),
    NOW()
FROM user u
WHERE u.username IN ('admin','user001','user002','user003','user004','user005','user006','user007','user008','user009','user010');

-- user_role 已下线，角色赋权通过 role_assignment 管理（参考 data.sql）

INSERT INTO `tiny-web`.
    `oauth2_registered_client`
    (`id`,
     `client_id`,
     `client_id_issued_at`,
     `client_secret`,
     `client_secret_expires_at`,
     `client_name`,
     `client_authentication_methods`,
     `authorization_grant_types`,
     `redirect_uris`,
     `post_logout_redirect_uris`,
     `scopes`,
     `client_settings`,
     `token_settings`)
VALUES (
        '3eacac0e-0de9-4727-9a64-6bdd4be2ee1f',
        'oidc-client',
        '2023-07-12 07:33:42',
        '{noop}secret',
        NULL,
        '3eacac0e-0de9-4727-9a64-6bdd4be2ee1f',
        'client_secret_basic',
        'refresh_token,authorization_code',
        'http://www.baidu.com',
        'http://127.0.0.1:8080/',
        'openid,profile',
        '{\"@class\":\"java.util.Collections$UnmodifiableMap\",\"settings.client.require-proof-key\":false,\"settings.client.require-authorization-consent\":true}',
        '{\"@class\":\"java.util.Collections$UnmodifiableMap\",\"settings.token.reuse-refresh-tokens\":true,\"-token-signature-algorithm\":[\"org.springframework.security.oauth2.jose.jws.SignatureAlgorithm\",\"RS256\"],\"settings.token.access-token-time-to-live\":[\"java.time.Duration\",300.000000000],\"settings.token.access-token-format\":{\"@class\":\"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat\",\"value\":\"self-contained\"},\"settings.token.refresh-token-time-to-live\":[\"java.time.Duration\",3600.000000000],\"settings.token.authorization-code-time-to-live\":[\"java.time.Duration\",300.000000000],\"settings.token.device-code-time-to-live\":[\"java.time.Duration\",300.000000000]}');