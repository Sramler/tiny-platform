package com.tiny.platform.application.oauth.export;

import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UserDataProvider —— 基于 user 表的导出 DataProvider 实现
 *
 * 说明：
 * - exportType = "user"（由 @Component(\"user\") 指定）
 * - 支持前端传入的 filters：
 *   - username: 按用户名精确匹配
 *   - nickname: 按昵称精确匹配
 * - 兼容导出当前页 & 导出全部：
 *   - 导出当前页：filters 中会包含 __mode=page、__page、__pageSize
 *   - 导出全部：只包含业务 filters（username/nickname），按分页迭代全量导出
 *
 * 输出数据字段（需与前端列字段一致）：
 *   id, username, nickname, enabled, accountNonExpired, accountNonLocked, credentialsNonExpired, lastLoginAt
 */
@Component("user")
public class UserDataProvider implements FilterAwareDataProvider<Map<String, Object>> {

    private final JdbcTemplate jdbcTemplate;

    private static final ThreadLocal<Map<String, Object>> FILTERS_HOLDER = new ThreadLocal<>();

    public UserDataProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void setFilters(Map<String, Object> filters) {
        FILTERS_HOLDER.set(filters);
    }

    @Override
    public void clearFilters() {
        FILTERS_HOLDER.remove();
    }

    @Override
    public Iterator<Map<String, Object>> fetchIterator(int batchSize) {
        Map<String, Object> filters = FILTERS_HOLDER.get();

        // 构建 WHERE 子句和参数
        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();

        if (filters != null) {
            if (filters.containsKey("username") && filters.get("username") != null) {
                String username = filters.get("username").toString().trim();
                if (!username.isEmpty()) {
                    whereClause.append(" AND username = ?");
                    params.add(username);
                }
            }
            if (filters.containsKey("nickname") && filters.get("nickname") != null) {
                String nickname = filters.get("nickname").toString().trim();
                if (!nickname.isEmpty()) {
                    whereClause.append(" AND nickname = ?");
                    params.add(nickname);
                }
            }
        }

        String baseSql = """
            SELECT id,
                   username,
                   nickname,
                   enabled,
                   account_non_expired,
                   account_non_locked,
                   credentials_non_expired,
                   last_login_at
              FROM user
            """;

        String whereSql = whereClause.length() > 0
            ? " WHERE " + whereClause.substring(5) // 去掉开头的 " AND "
            : "";

        String orderBySql = " ORDER BY id DESC";

        // 导出当前页：根据 __mode=page 及 __page/__pageSize 一次性返回当前页数据
        if (filters != null && "page".equals(filters.get("__mode"))) {
            int page = parseInt(filters.get("__page"), 1);
            int pageSizeForPage = parseInt(filters.get("__pageSize"), batchSize);
            if (page <= 0) {
                page = 1;
            }
            if (pageSizeForPage <= 0) {
                pageSizeForPage = batchSize;
            }
            int offset = (page - 1) * pageSizeForPage;

            String sql = baseSql + whereSql + orderBySql + " LIMIT ? OFFSET ?";
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(pageSizeForPage);
            pageParams.add(offset);

            List<Map<String, Object>> list = jdbcTemplate.query(sql, pageParams.toArray(), this::mapRowToMap);
            return list.iterator();
        }

        // 导出全部：先统计总数，再分页流式查询
        String countSql = "SELECT COUNT(*) FROM user" + whereSql;
        Long totalCount = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
        if (totalCount == null || totalCount == 0) {
            return new ArrayList<Map<String, Object>>().iterator();
        }

        return new PaginatedIterator(batchSize, totalCount.intValue(), baseSql + whereSql + orderBySql, params);
    }

    private int parseInt(Object value, int defaultVal) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private Map<String, Object> mapRowToMap(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("username", rs.getString("username"));
        row.put("nickname", rs.getString("nickname"));
        row.put("enabled", rs.getBoolean("enabled"));
        row.put("accountNonExpired", rs.getBoolean("account_non_expired"));
        row.put("accountNonLocked", rs.getBoolean("account_non_locked"));
        row.put("credentialsNonExpired", rs.getBoolean("credentials_non_expired"));
        LocalDateTime lastLoginAt = rs.getTimestamp("last_login_at") != null
            ? rs.getTimestamp("last_login_at").toLocalDateTime()
            : null;
        row.put("lastLoginAt", lastLoginAt);
        return row;
    }

    /**
     * 简单分页迭代器：每次按 batchSize 查询一批用户，直到读取完 totalCount。
     */
    private class PaginatedIterator implements Iterator<Map<String, Object>> {
        private final int batchSize;
        private final int totalCount;
        private final String baseSql;
        private final List<Object> baseParams;

        private int currentOffset = 0;
        private List<Map<String, Object>> currentBatch = new ArrayList<>();
        private int currentBatchIndex = 0;
        private boolean hasMore = true;

        PaginatedIterator(int batchSize, int totalCount, String baseSql, List<Object> baseParams) {
            this.batchSize = batchSize;
            this.totalCount = totalCount;
            this.baseSql = baseSql;
            this.baseParams = new ArrayList<>(baseParams);
            loadNextBatch();
        }

        private void loadNextBatch() {
            if (currentOffset >= totalCount) {
                hasMore = false;
                currentBatch = new ArrayList<>();
                currentBatchIndex = 0;
                return;
            }

            String sql = baseSql + " LIMIT ? OFFSET ?";
            List<Object> params = new ArrayList<>(baseParams);
            params.add(batchSize);
            params.add(currentOffset);

            currentBatch = jdbcTemplate.query(sql, params.toArray(), UserDataProvider.this::mapRowToMap);
            currentBatchIndex = 0;
            currentOffset += batchSize;

            if (currentBatch.isEmpty() || currentOffset >= totalCount) {
                hasMore = false;
            }
        }

        @Override
        public boolean hasNext() {
            if (currentBatch == null || currentBatchIndex >= currentBatch.size()) {
                if (hasMore) {
                    loadNextBatch();
                } else {
                    return false;
                }
            }
            return currentBatchIndex < currentBatch.size();
        }

        @Override
        public Map<String, Object> next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            Map<String, Object> row = currentBatch.get(currentBatchIndex);
            currentBatchIndex++;
            return row;
        }
    }
}


