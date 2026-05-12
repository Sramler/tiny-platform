package com.tiny.platform.infrastructure.runtime.version;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRuntimeVersionStore implements RuntimeVersionStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public JdbcRuntimeVersionStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    JdbcRuntimeVersionStore(NamedParameterJdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<RuntimeVersionSnapshot> find(RuntimeVersionKey key) {
        return jdbcTemplate.query(
                """
                SELECT
                  `id`,
                  `version_domain`,
                  `tenant_id`,
                  `scope_type`,
                  `scope_id`,
                  `version_value`,
                  `version_seq`,
                  `reason`,
                  `updated_at`,
                  `updated_by`
                FROM `runtime_version_signal`
                WHERE `version_domain` = :domain
                  AND `normalized_tenant_id` = :normalizedTenantId
                  AND `scope_type` = :scopeType
                  AND `normalized_scope_id` = :normalizedScopeId
                LIMIT 1
                """,
                params(key),
                (rs, rowNum) -> new RuntimeVersionSnapshot(
                    rs.getLong("id"),
                    RuntimeVersionKey.of(
                        rs.getString("version_domain"),
                        nullableLong(rs.getObject("tenant_id")),
                        rs.getString("scope_type"),
                        nullableLong(rs.getObject("scope_id"))
                    ),
                    rs.getString("version_value"),
                    rs.getLong("version_seq"),
                    rs.getString("reason"),
                    rs.getObject("updated_at", LocalDateTime.class),
                    nullableLong(rs.getObject("updated_by"))
                )
            )
            .stream()
            .findFirst();
    }

    @Override
    public RuntimeVersionSnapshot initializeIfAbsent(RuntimeVersionKey key,
                                                     String versionValue,
                                                     String reason,
                                                     Long actorUserId) {
        String normalizedValue = normalizeVersionValue(versionValue, key);
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO `runtime_version_signal` (
              `version_domain`,
              `tenant_id`,
              `scope_type`,
              `scope_id`,
              `version_value`,
              `version_seq`,
              `reason`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            ) VALUES (
              :domain,
              :tenantId,
              :scopeType,
              :scopeId,
              :versionValue,
              1,
              :reason,
              :actorUserId,
              NOW(),
              :actorUserId,
              NOW()
            )
            """,
            params(key)
                .addValue("versionValue", normalizedValue)
                .addValue("reason", normalizeReason(reason))
                .addValue("actorUserId", actorUserId)
        );
        return find(key).orElseThrow(() -> new IllegalStateException("runtime version initialization failed"));
    }

    @Override
    public RuntimeVersionSnapshot bump(RuntimeVersionKey key, String reason, Long actorUserId) {
        String versionValue = newVersionValue(key, reason);
        jdbcTemplate.update(
            """
            INSERT INTO `runtime_version_signal` (
              `version_domain`,
              `tenant_id`,
              `scope_type`,
              `scope_id`,
              `version_value`,
              `version_seq`,
              `reason`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            ) VALUES (
              :domain,
              :tenantId,
              :scopeType,
              :scopeId,
              :versionValue,
              1,
              :reason,
              :actorUserId,
              NOW(),
              :actorUserId,
              NOW()
            )
            ON DUPLICATE KEY UPDATE
              `version_seq` = `version_seq` + 1,
              `version_value` = VALUES(`version_value`),
              `reason` = VALUES(`reason`),
              `updated_by` = VALUES(`updated_by`),
              `updated_at` = NOW()
            """,
            params(key)
                .addValue("versionValue", versionValue)
                .addValue("reason", normalizeReason(reason))
                .addValue("actorUserId", actorUserId)
        );
        return find(key).orElseThrow(() -> new IllegalStateException("runtime version bump failed"));
    }

    private MapSqlParameterSource params(RuntimeVersionKey key) {
        return new MapSqlParameterSource()
            .addValue("domain", key.domain())
            .addValue("tenantId", key.tenantId())
            .addValue("normalizedTenantId", normalizeId(key.tenantId()))
            .addValue("scopeType", key.scopeType())
            .addValue("scopeId", key.scopeId())
            .addValue("normalizedScopeId", normalizeId(key.scopeId()));
    }

    private String normalizeVersionValue(String versionValue, RuntimeVersionKey key) {
        if (StringUtils.hasText(versionValue)) {
            return versionValue.trim();
        }
        return newVersionValue(key, "initial");
    }

    private String newVersionValue(RuntimeVersionKey key, String reason) {
        Instant now = Instant.now(clock);
        return sha256Hex(String.join("|",
            key.domain(),
            String.valueOf(normalizeId(key.tenantId())),
            key.scopeType(),
            String.valueOf(normalizeId(key.scopeId())),
            normalizeReason(reason),
            now.toString(),
            UUID.randomUUID().toString()
        ));
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "unspecified";
        }
        String normalized = reason.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private long normalizeId(Long value) {
        return value == null ? 0L : value;
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
