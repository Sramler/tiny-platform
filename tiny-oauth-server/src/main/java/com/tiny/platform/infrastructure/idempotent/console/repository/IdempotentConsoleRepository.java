package com.tiny.platform.infrastructure.idempotent.console.repository;

import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentBlacklistDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRecordDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRuleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 幂等治理控制台数据访问
 *
 * <p>直接查询 sys_idempotent_token、sys_idempotent_rule、sys_idempotent_blacklist 表</p>
 *
 * @author tiny-platform
 * @since 1.0.0
 */
public class IdempotentConsoleRepository {

    private static final String TOKEN_TABLE = "sys_idempotent_token";
    private static final String RULE_TABLE = "sys_idempotent_rule";
    private static final String BLACKLIST_TABLE = "sys_idempotent_blacklist";

    private final JdbcTemplate jdbcTemplate;

    public IdempotentConsoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- Records (sys_idempotent_token) ----------

    public Page<IdempotentRecordDto> findRecords(String keyPrefix, String state, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT id, state, expire_time, created_time FROM ").append(TOKEN_TABLE).append(" WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(TOKEN_TABLE).append(" WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        List<Object> countParams = new java.util.ArrayList<>();

        if (StringUtils.hasText(keyPrefix)) {
            sql.append(" AND id LIKE ?");
            countSql.append(" AND id LIKE ?");
            params.add(keyPrefix + "%");
            countParams.add(keyPrefix + "%");
        }
        if (StringUtils.hasText(state)) {
            sql.append(" AND state = ?");
            countSql.append(" AND state = ?");
            params.add(state);
            countParams.add(state);
        }

        sql.append(" ORDER BY created_time DESC");

        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String pagedSql = sql + " LIMIT " + pageable.getPageSize() + " OFFSET " + pageable.getOffset();
        List<IdempotentRecordDto> content = jdbcTemplate.query(pagedSql, params.toArray(), (rs, rowNum) -> {
            IdempotentRecordDto dto = new IdempotentRecordDto();
            dto.setKey(rs.getString("id"));
            dto.setState(rs.getString("state"));
            dto.setCreatedAt(rs.getTimestamp("created_time") != null ? rs.getTimestamp("created_time").toLocalDateTime() : null);
            dto.setExpireAt(rs.getTimestamp("expire_time") != null ? rs.getTimestamp("expire_time").toLocalDateTime() : null);
            return dto;
        });

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional
    public int deleteRecordByKey(String key) {
        return jdbcTemplate.update("DELETE FROM " + TOKEN_TABLE + " WHERE id = ?", key);
    }

    // ---------- Rules (sys_idempotent_rule) ----------

    public Page<IdempotentRuleDto> findRules(String scene, String bizCode, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT id, scope, scene, biz_code, enabled, ttl_seconds, created_time, updated_time FROM ").append(RULE_TABLE).append(" WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(RULE_TABLE).append(" WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        List<Object> countParams = new java.util.ArrayList<>();

        if (StringUtils.hasText(scene)) {
            sql.append(" AND scene = ?");
            countSql.append(" AND scene = ?");
            params.add(scene);
            countParams.add(scene);
        }
        if (StringUtils.hasText(bizCode)) {
            sql.append(" AND biz_code = ?");
            countSql.append(" AND biz_code = ?");
            params.add(bizCode);
            countParams.add(bizCode);
        }

        sql.append(" ORDER BY id DESC");

        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String pagedSql = sql + " LIMIT " + pageable.getPageSize() + " OFFSET " + pageable.getOffset();
        List<IdempotentRuleDto> content = jdbcTemplate.query(pagedSql, params.toArray(), (rs, rowNum) -> {
            IdempotentRuleDto dto = new IdempotentRuleDto();
            dto.setId(rs.getLong("id"));
            dto.setScope(rs.getString("scope"));
            dto.setScene(rs.getString("scene"));
            dto.setBizCode(rs.getString("biz_code"));
            dto.setEnabled(rs.getInt("enabled") == 1);
            dto.setTtlSeconds(rs.getObject("ttl_seconds", Integer.class));
            dto.setCreatedAt(rs.getTimestamp("created_time") != null ? rs.getTimestamp("created_time").toLocalDateTime() : null);
            dto.setUpdatedAt(rs.getTimestamp("updated_time") != null ? rs.getTimestamp("updated_time").toLocalDateTime() : null);
            return dto;
        });

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional
    public IdempotentRuleDto createRule(IdempotentRuleDto dto) {
        jdbcTemplate.update(
            "INSERT INTO " + RULE_TABLE + " (scope, scene, biz_code, enabled, ttl_seconds) VALUES (?, ?, ?, ?, ?)",
            dto.getScope() != null ? dto.getScope() : "",
            dto.getScene(),
            dto.getBizCode(),
            dto.isEnabled() ? 1 : 0,
            dto.getTtlSeconds()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        dto.setId(id);
        return dto;
    }

    @Transactional
    public int updateRule(Long id, IdempotentRuleDto dto) {
        return jdbcTemplate.update(
            "UPDATE " + RULE_TABLE + " SET scope = ?, scene = ?, biz_code = ?, enabled = ?, ttl_seconds = ?, updated_time = CURRENT_TIMESTAMP WHERE id = ?",
            dto.getScope() != null ? dto.getScope() : "",
            dto.getScene(),
            dto.getBizCode(),
            dto.isEnabled() ? 1 : 0,
            dto.getTtlSeconds(),
            id
        );
    }

    @Transactional
    public int deleteRule(Long id) {
        return jdbcTemplate.update("DELETE FROM " + RULE_TABLE + " WHERE id = ?", id);
    }

    @Transactional
    public int updateRulesEnabled(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.add(enabled ? 1 : 0);
        params.addAll(ids);
        return jdbcTemplate.update("UPDATE " + RULE_TABLE + " SET enabled = ?, updated_time = CURRENT_TIMESTAMP WHERE id IN (" + placeholders + ")",
            params.toArray());
    }

    // ---------- Blacklist (sys_idempotent_blacklist) ----------

    public Page<IdempotentBlacklistDto> findBlacklist(Pageable pageable) {
        String sql = "SELECT id, key_pattern, reason, created_time FROM " + BLACKLIST_TABLE + " ORDER BY id DESC";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + BLACKLIST_TABLE, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String pagedSql = sql + " LIMIT " + pageable.getPageSize() + " OFFSET " + pageable.getOffset();
        List<IdempotentBlacklistDto> content = jdbcTemplate.query(pagedSql, (rs, rowNum) -> {
            IdempotentBlacklistDto dto = new IdempotentBlacklistDto();
            dto.setId(rs.getLong("id"));
            dto.setKeyPattern(rs.getString("key_pattern"));
            dto.setReason(rs.getString("reason"));
            dto.setCreatedAt(rs.getTimestamp("created_time") != null ? rs.getTimestamp("created_time").toLocalDateTime() : null);
            return dto;
        });
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional
    public IdempotentBlacklistDto createBlacklist(IdempotentBlacklistDto dto) {
        jdbcTemplate.update(
            "INSERT INTO " + BLACKLIST_TABLE + " (key_pattern, reason) VALUES (?, ?)",
            dto.getKeyPattern(),
            dto.getReason()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        dto.setId(id);
        return dto;
    }

    @Transactional
    public int deleteBlacklist(Long id) {
        return jdbcTemplate.update("DELETE FROM " + BLACKLIST_TABLE + " WHERE id = ?", id);
    }

    /**
     * 检查 key 是否在黑名单中（精确匹配或前缀匹配）
     */
    public boolean isBlacklisted(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            return false;
        }
        List<String> patterns = jdbcTemplate.queryForList(
            "SELECT key_pattern FROM " + BLACKLIST_TABLE,
            String.class
        );
        return isBlacklisted(fullKey, patterns);
    }

    /**
     * 检查 key 是否匹配任一黑名单模式（精确匹配或前缀匹配，% 表示通配）
     */
    public static boolean isBlacklisted(String fullKey, List<String> patterns) {
        if (fullKey == null || fullKey.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null) continue;
            if (fullKey.equals(pattern)) return true;
            if (pattern.endsWith("%")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (fullKey.startsWith(prefix)) return true;
            } else if (fullKey.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
}
