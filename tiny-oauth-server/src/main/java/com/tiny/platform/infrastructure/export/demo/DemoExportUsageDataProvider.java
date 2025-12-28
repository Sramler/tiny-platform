package com.tiny.platform.infrastructure.export.demo;

import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DemoExportUsageDataProvider —— 使用 Repository 和 Entity，自动适配表结构变化
 *
 * <p>优势：</p>
 * <ul>
 *   <li>✅ 表结构变化时，只需更新 Entity，无需修改 DataProvider</li>
 *   <li>✅ 使用 JPA Specification，类型安全，自动处理字段映射</li>
 *   <li>✅ 与列表查询使用相同的 Repository，保证数据一致性</li>
 *   <li>✅ 减少代码重复，降低维护成本</li>
 * </ul>
 *
 * <p>注意：通过 ThreadLocal 存储当前线程的过滤条件，配合 FilterAwareDataProvider 接口使用。</p>
 */
@Component("demo_export_usage")
public class DemoExportUsageDataProvider implements FilterAwareDataProvider<DemoExportUsageEntity> {

    private final DemoExportUsageRepository repository;

    // 使用 ThreadLocal 存储当前线程的过滤条件
    private static final ThreadLocal<Map<String, Object>> FILTERS_HOLDER = new ThreadLocal<>();

    public DemoExportUsageDataProvider(DemoExportUsageRepository repository) {
        this.repository = repository;
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
    public Iterator<DemoExportUsageEntity> fetchIterator(int batchSize) {
        Map<String, Object> filters = FILTERS_HOLDER.get();

        // 构建 JPA Specification，与 DemoExportUsageService.search() 保持一致
        Specification<DemoExportUsageEntity> spec = buildSpecification(filters);

        // 如果显式指定了"只导出当前页"，按当前页分页一次性返回
        if (filters != null && "page".equals(filters.get("__mode"))) {
            Object pageObj = filters.get("__page");
            Object pageSizeObj = filters.get("__pageSize");
            int page = 1;
            int pageSizeForPage = batchSize;
            if (pageObj instanceof Number) {
                page = ((Number) pageObj).intValue();
            } else if (pageObj instanceof String) {
                try {
                    page = Integer.parseInt((String) pageObj);
                } catch (NumberFormatException ignored) {
                }
            }
            if (pageSizeObj instanceof Number) {
                pageSizeForPage = ((Number) pageSizeObj).intValue();
            } else if (pageSizeObj instanceof String) {
                try {
                    pageSizeForPage = Integer.parseInt((String) pageSizeObj);
                } catch (NumberFormatException ignored) {
                }
            }
            if (page <= 0) {
                page = 1;
            }
            if (pageSizeForPage <= 0) {
                pageSizeForPage = batchSize;
            }

            Pageable pageable = PageRequest.of(page - 1, pageSizeForPage);
            Page<DemoExportUsageEntity> pageResult = repository.findAll(spec, pageable);
            return pageResult.getContent().iterator();
        }

        // 默认：导出全部匹配记录 —— 创建分页迭代器
        Page<DemoExportUsageEntity> firstPage = repository.findAll(spec, PageRequest.of(0, batchSize));
        long totalCount = firstPage.getTotalElements();
        if (totalCount == 0) {
            return new ArrayList<DemoExportUsageEntity>().iterator();
        }

        return new PaginatedEntityIterator(batchSize, (int) totalCount, spec);
    }

    /**
     * 构建 JPA Specification，与 DemoExportUsageService.search() 保持一致
     */
    private Specification<DemoExportUsageEntity> buildSpecification(Map<String, Object> filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filters != null) {
                if (filters.containsKey("tenantCode") && filters.get("tenantCode") != null) {
                    String tenantCode = filters.get("tenantCode").toString().trim();
                    if (!tenantCode.isEmpty()) {
                        predicates.add(cb.equal(root.get("tenantCode"), tenantCode));
                    }
                }
                if (filters.containsKey("productCode") && filters.get("productCode") != null) {
                    String productCode = filters.get("productCode").toString().trim();
                    if (!productCode.isEmpty()) {
                        predicates.add(cb.equal(root.get("productCode"), productCode));
                    }
                }
                if (filters.containsKey("status") && filters.get("status") != null) {
                    String status = filters.get("status").toString().trim();
                    if (!status.isEmpty()) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                }
                // 可以继续添加其他过滤条件
            }
            
            // 默认排序：按 usageDate DESC, tenantCode, productCode
            query.orderBy(
                cb.desc(root.get("usageDate")),
                cb.asc(root.get("tenantCode")),
                cb.asc(root.get("productCode"))
            );
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 分页迭代器，使用 Repository 分页查询
     */
    private class PaginatedEntityIterator implements Iterator<DemoExportUsageEntity> {
        private final int batchSize;
        private final int totalCount;
        private final Specification<DemoExportUsageEntity> spec;
        private int currentPage = 0;
        private List<DemoExportUsageEntity> currentBatch = null;
        private int currentBatchIndex = 0;
        private boolean hasMore = true;

        public PaginatedEntityIterator(int batchSize, int totalCount, Specification<DemoExportUsageEntity> spec) {
            this.batchSize = batchSize;
            this.totalCount = totalCount;
            this.spec = spec;
            loadNextBatch();
        }

        private void loadNextBatch() {
            if (currentPage * batchSize >= totalCount) {
                hasMore = false;
                currentBatch = null;
                return;
            }

            Pageable pageable = PageRequest.of(currentPage, batchSize);
            Page<DemoExportUsageEntity> pageResult = repository.findAll(spec, pageable);
            currentBatch = pageResult.getContent();
            currentBatchIndex = 0;
            currentPage++;

            if (currentBatch.isEmpty() || currentPage * batchSize >= totalCount) {
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
            return currentBatch != null && currentBatchIndex < currentBatch.size();
        }

        @Override
        public DemoExportUsageEntity next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            DemoExportUsageEntity entity = currentBatch.get(currentBatchIndex);
            currentBatchIndex++;
            return entity;
        }
    }
}

