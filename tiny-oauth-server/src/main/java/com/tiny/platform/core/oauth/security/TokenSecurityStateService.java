package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

@Service
public class TokenSecurityStateService {

    private static final String GLOBAL_SCOPE_TYPE = "GLOBAL";
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public TokenSecurityStateService(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    TokenSecurityStateService(NamedParameterJdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public TokenSecurityState resolveEffectiveState(Long userId,
                                                    Long activeTenantId,
                                                    String activeScopeType,
                                                    Long activeScopeId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        TokenSecurityScope globalScope = TokenSecurityScope.global(userId);
        TokenSecurityScope scoped = TokenSecurityScope.of(userId, activeTenantId, activeScopeType, activeScopeId);
        TokenSecurityRow global = initializeIfAbsent(globalScope);
        TokenSecurityRow scope = initializeIfAbsent(scoped);
        LocalDateTime notBefore = max(global.tokenNotBefore(), scope.tokenNotBefore());
        String version = sha256Hex(String.join("|",
            "token-security-v1",
            String.valueOf(userId),
            scoped.scopeType(),
            String.valueOf(normalizeId(scoped.tenantId())),
            String.valueOf(normalizeId(scoped.scopeId())),
            String.valueOf(global.tokenVersion()),
            String.valueOf(scope.tokenVersion()),
            toEpochSecond(global.tokenNotBefore()),
            toEpochSecond(scope.tokenNotBefore())
        ));
        return new TokenSecurityState(
            userId,
            scoped.tenantId(),
            scoped.scopeType(),
            scoped.scopeId(),
            version,
            notBefore,
            global.tokenVersion(),
            scope.tokenVersion()
        );
    }

    public TokenSecurityState revokeAllUserTokens(Long userId, String reason, Long actorUserId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        bump(TokenSecurityScope.global(userId), reason, actorUserId);
        return resolveEffectiveState(userId, null, GLOBAL_SCOPE_TYPE, null);
    }

    public TokenSecurityState revokeCurrentScopeTokens(Long userId, String reason, Long actorUserId) {
        return revokeScopedTokens(
            userId,
            TenantContext.isPlatformScope() ? null : TenantContext.getActiveTenantId(),
            TenantContext.isPlatformScope()
                ? TenantContextContract.SCOPE_TYPE_PLATFORM
                : TenantContextContract.SCOPE_TYPE_TENANT,
            TenantContext.isPlatformScope() ? null : TenantContext.getActiveTenantId(),
            reason,
            actorUserId
        );
    }

    public TokenSecurityState revokeScopedTokens(Long userId,
                                                 Long activeTenantId,
                                                 String activeScopeType,
                                                 Long activeScopeId,
                                                 String reason,
                                                 Long actorUserId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        TokenSecurityScope scope = TokenSecurityScope.of(userId, activeTenantId, activeScopeType, activeScopeId);
        bump(scope, reason, actorUserId);
        return resolveEffectiveState(userId, activeTenantId, scope.scopeType(), scope.scopeId());
    }

    private TokenSecurityRow initializeIfAbsent(TokenSecurityScope scope) {
        Optional<TokenSecurityRow> existing = find(scope);
        if (existing.isPresent()) {
            return existing.get();
        }
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO `user_token_security_state` (
              `user_id`,
              `tenant_id`,
              `scope_type`,
              `scope_id`,
              `token_version`,
              `token_not_before`,
              `reason`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            ) VALUES (
              :userId,
              :tenantId,
              :scopeType,
              :scopeId,
              1,
              :tokenNotBefore,
              'initial',
              NULL,
              NOW(),
              NULL,
              NOW()
            )
            """,
            params(scope).addValue("tokenNotBefore", EPOCH)
        );
        return find(scope).orElseThrow(() -> new IllegalStateException("token security state initialization failed"));
    }

    private TokenSecurityRow bump(TokenSecurityScope scope, String reason, Long actorUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
            """
            INSERT INTO `user_token_security_state` (
              `user_id`,
              `tenant_id`,
              `scope_type`,
              `scope_id`,
              `token_version`,
              `token_not_before`,
              `reason`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            ) VALUES (
              :userId,
              :tenantId,
              :scopeType,
              :scopeId,
              2,
              :tokenNotBefore,
              :reason,
              :actorUserId,
              NOW(),
              :actorUserId,
              NOW()
            )
            ON DUPLICATE KEY UPDATE
              `token_version` = `token_version` + 1,
              `token_not_before` = VALUES(`token_not_before`),
              `reason` = VALUES(`reason`),
              `updated_by` = VALUES(`updated_by`),
              `updated_at` = NOW()
            """,
            params(scope)
                .addValue("tokenNotBefore", now)
                .addValue("reason", normalizeReason(reason))
                .addValue("actorUserId", actorUserId)
        );
        return find(scope).orElseThrow(() -> new IllegalStateException("token security state bump failed"));
    }

    private Optional<TokenSecurityRow> find(TokenSecurityScope scope) {
        return jdbcTemplate.query(
                """
                SELECT `token_version`, `token_not_before`
                FROM `user_token_security_state`
                WHERE `user_id` = :userId
                  AND `normalized_tenant_id` = :normalizedTenantId
                  AND `scope_type` = :scopeType
                  AND `normalized_scope_id` = :normalizedScopeId
                LIMIT 1
                """,
                params(scope),
                (rs, rowNum) -> new TokenSecurityRow(
                    rs.getLong("token_version"),
                    rs.getObject("token_not_before", LocalDateTime.class)
                )
            )
            .stream()
            .findFirst();
    }

    private MapSqlParameterSource params(TokenSecurityScope scope) {
        return new MapSqlParameterSource()
            .addValue("userId", scope.userId())
            .addValue("tenantId", scope.tenantId())
            .addValue("normalizedTenantId", normalizeId(scope.tenantId()))
            .addValue("scopeType", scope.scopeType())
            .addValue("scopeId", scope.scopeId())
            .addValue("normalizedScopeId", normalizeId(scope.scopeId()));
    }

    private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right == null ? EPOCH : right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static long normalizeId(Long value) {
        return value == null ? 0L : value;
    }

    private static String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "unspecified";
        }
        String normalized = reason.trim();
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private static String toEpochSecond(LocalDateTime value) {
        LocalDateTime safe = value == null ? EPOCH : value;
        return String.valueOf(safe.toInstant(ZoneOffset.UTC).getEpochSecond());
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

    private record TokenSecurityScope(Long userId, Long tenantId, String scopeType, Long scopeId) {
        static TokenSecurityScope global(Long userId) {
            return new TokenSecurityScope(userId, null, GLOBAL_SCOPE_TYPE, null);
        }

        static TokenSecurityScope of(Long userId, Long activeTenantId, String activeScopeType, Long activeScopeId) {
            String scopeType = StringUtils.hasText(activeScopeType)
                ? activeScopeType.trim().toUpperCase(Locale.ROOT)
                : activeTenantId == null || activeTenantId <= 0
                    ? TenantContextContract.SCOPE_TYPE_PLATFORM
                    : TenantContextContract.SCOPE_TYPE_TENANT;
            if (GLOBAL_SCOPE_TYPE.equals(scopeType) || TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
                return new TokenSecurityScope(userId, null, TenantContextContract.SCOPE_TYPE_PLATFORM, null);
            }
            Long tenantId = activeTenantId;
            Long scopeId = activeScopeId;
            if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)) {
                scopeId = scopeId != null && scopeId > 0 ? scopeId : tenantId;
            }
            return new TokenSecurityScope(userId, tenantId, scopeType, scopeId);
        }
    }

    private record TokenSecurityRow(long tokenVersion, LocalDateTime tokenNotBefore) {
    }
}
