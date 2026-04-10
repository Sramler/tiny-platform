package com.tiny.web.sys.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 从 {@code user_auth_credential} + {@code user_auth_scope_policy} 解析 LOCAL+PASSWORD 配置（与 oauth-server 新模型对齐）。
 */
@Service
public class UserAuthPasswordLookupService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthPasswordLookupService.class);

    private static final String LOCAL_PASSWORD_CONFIG_SQL = """
            SELECT c.authentication_configuration
            FROM user_auth_credential c
            INNER JOIN user_auth_scope_policy p ON p.credential_id = c.id
            WHERE c.user_id = ?
              AND c.authentication_provider = 'LOCAL'
              AND c.authentication_type = 'PASSWORD'
              AND (p.is_method_enabled IS NULL OR p.is_method_enabled = TRUE)
            ORDER BY
              CASE p.scope_type WHEN 'TENANT' THEN 0 WHEN 'PLATFORM' THEN 1 ELSE 2 END,
              p.authentication_priority ASC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UserAuthPasswordLookupService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @return 认证配置 JSON（含 {@code password} 哈希等），无可用行时为空
     */
    public Optional<Map<String, Object>> findLocalPasswordConfiguration(long userId) {
        try {
            String json = jdbcTemplate.queryForObject(LOCAL_PASSWORD_CONFIG_SQL, String.class, userId);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse authentication_configuration JSON for userId={}", userId, e);
            return Optional.empty();
        }
    }
}
