package com.tiny.platform.infrastructure.export.demo;

import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DemoExportUsageDataProvider —— 直接查询导出行，避免实体长期驻留在 Hibernate 会话中
 *
 * <p>优势：</p>
 * <ul>
 *   <li>✅ 避免导出链路实例化大量托管 Entity，降低一阶缓存压力</li>
 *   <li>✅ 使用 JPA Specification，类型安全，自动复用过滤逻辑</li>
 *   <li>✅ 返回 Map，可被导出框架直接按字段名取值</li>
 * </ul>
 *
 * <p>注意：通过 ThreadLocal 存储当前线程的过滤条件，配合 FilterAwareDataProvider 接口使用。</p>
 */
@Component("demo_export_usage")
public class DemoExportUsageDataProvider implements FilterAwareDataProvider<Map<String, Object>> {

    private static final List<String> EXPORT_FIELDS = List.of(
            "id",
            "tenantId",
            "usageDate",
            "productCode",
            "productName",
            "planTier",
            "region",
            "usageQty",
            "unit",
            "unitPrice",
            "amount",
            "currency",
            "taxRate",
            "billable",
            "status",
            "metadata",
            "description",
            "priority",
            "tags",
            "usageTime",
            "qualityScore",
            "discountPercentage",
            "categoryPath",
            "departmentId",
            "customerName",
            "themeColor",
            "apiKey",
            "searchKeyword",
            "startDate",
            "endDate",
            "selectedFeatures",
            "minValue",
            "maxValue",
            "startTime",
            "endTime",
            "paymentMethod",
            "notes",
            "attachmentInfo",
            "createdAt"
    );

    private final EntityManager entityManager;

    // 使用 ThreadLocal 存储当前线程的过滤条件
    private static final ThreadLocal<Map<String, Object>> FILTERS_HOLDER = new ThreadLocal<>();

    public DemoExportUsageDataProvider(EntityManager entityManager) {
        this.entityManager = entityManager;
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
        List<String> selectedFields = resolveSelectedFields(filters);

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

            int firstResult = (page - 1) * pageSizeForPage;
            return queryPageByOffset(spec, selectedFields, firstResult, pageSizeForPage).iterator();
        }

        // 默认：导出全部匹配记录 —— 使用 keyset（id 游标）分页，避免深分页 offset 退化
        return new KeysetRowIterator(batchSize, spec, selectedFields);
    }

    @Override
    public long estimateTotal() {
        Map<String, Object> filters = FILTERS_HOLDER.get();
        Specification<DemoExportUsageEntity> spec = buildSpecification(filters);
        long totalMatching = queryCount(spec);
        if (totalMatching <= 0) {
            return totalMatching;
        }
        if (filters != null && "page".equals(filters.get("__mode"))) {
            int page = parsePositiveInt(filters.get("__page"), 1);
            int pageSize = parsePositiveInt(filters.get("__pageSize"), 0);
            if (pageSize <= 0) {
                return totalMatching;
            }
            long firstResult = (long) Math.max(0, page - 1) * pageSize;
            if (firstResult >= totalMatching) {
                return 0L;
            }
            return Math.min(pageSize, totalMatching - firstResult);
        }
        return totalMatching;
    }

    /**
     * 构建 JPA Specification，与 DemoExportUsageService.search() 保持一致
     */
    private Specification<DemoExportUsageEntity> buildSpecification(Map<String, Object> filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (filters != null) {
                if (filters.containsKey("tenantId") && filters.get("tenantId") != null) {
                    String tenantIdRaw = filters.get("tenantId").toString().trim();
                    if (!tenantIdRaw.isEmpty()) {
                        try {
                            Long tenantId = Long.parseLong(tenantIdRaw);
                            predicates.add(cb.equal(root.get("tenantId"), tenantId));
                        } catch (NumberFormatException ignored) {
                        }
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
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * keyset 迭代器，避免 offset 深分页性能退化
     */
    private class KeysetRowIterator implements Iterator<Map<String, Object>> {
        private final int batchSize;
        private final Specification<DemoExportUsageEntity> spec;
        private final List<String> selectedFields;
        private List<Map<String, Object>> currentBatch = null;
        private int currentBatchIndex = 0;
        private boolean hasMore = true;
        private Long lastSeenId = null;

        KeysetRowIterator(int batchSize, Specification<DemoExportUsageEntity> spec, List<String> selectedFields) {
            this.batchSize = batchSize;
            this.spec = spec;
            this.selectedFields = selectedFields;
            loadNextBatch();
        }

        private void loadNextBatch() {
            currentBatch = queryBatchByKeyset(spec, selectedFields, lastSeenId, batchSize);
            currentBatchIndex = 0;

            if (currentBatch == null || currentBatch.isEmpty()) {
                hasMore = false;
                currentBatch = null;
                return;
            }
            Object lastId = currentBatch.get(currentBatch.size() - 1).get("id");
            lastSeenId = lastId instanceof Number ? ((Number) lastId).longValue() : null;
            hasMore = currentBatch.size() >= batchSize && lastSeenId != null;
        }

        @Override
        public boolean hasNext() {
            if (currentBatch == null || currentBatchIndex >= currentBatch.size()) {
                if (hasMore) {
                    loadNextBatch();
                } else {
                    currentBatch = null;
                    return false;
                }
            }
            return currentBatch != null && currentBatchIndex < currentBatch.size();
        }

        @Override
        public Map<String, Object> next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            int index = currentBatchIndex;
            Map<String, Object> row = currentBatch.get(index);
            currentBatch.set(index, null);
            currentBatchIndex++;
            if (currentBatchIndex >= currentBatch.size() && !hasMore) {
                currentBatch = null;
            }
            return row;
        }
    }

    private List<Map<String, Object>> queryBatchByKeyset(
            Specification<DemoExportUsageEntity> spec,
            List<String> selectedFields,
            Long lastSeenId,
            int batchSize
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<DemoExportUsageEntity> root = cq.from(DemoExportUsageEntity.class);

        Predicate predicate = spec == null ? cb.conjunction() : spec.toPredicate(root, cq, cb);
        if (predicate == null) {
            predicate = cb.conjunction();
        }
        if (lastSeenId != null) {
            predicate = cb.and(predicate, cb.lt(root.get("id"), lastSeenId));
        }
        cq.multiselect(buildSelections(root, selectedFields));
        cq.where(predicate);
        cq.orderBy(cb.desc(root.get("id")));

        TypedQuery<Tuple> query = entityManager.createQuery(cq);
        query.setMaxResults(batchSize);
        return mapTuples(query.getResultList(), selectedFields);
    }

    private List<Map<String, Object>> queryPageByOffset(
            Specification<DemoExportUsageEntity> spec,
            List<String> selectedFields,
            int firstResult,
            int pageSize
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<DemoExportUsageEntity> root = cq.from(DemoExportUsageEntity.class);

        Predicate predicate = spec == null ? cb.conjunction() : spec.toPredicate(root, cq, cb);
        if (predicate == null) {
            predicate = cb.conjunction();
        }

        cq.multiselect(buildSelections(root, selectedFields));
        cq.where(predicate);
        cq.orderBy(buildPageModeOrders(cb, root));

        TypedQuery<Tuple> query = entityManager.createQuery(cq);
        query.setFirstResult(Math.max(0, firstResult));
        query.setMaxResults(pageSize);
        return mapTuples(query.getResultList(), selectedFields);
    }

    private long queryCount(Specification<DemoExportUsageEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<DemoExportUsageEntity> root = cq.from(DemoExportUsageEntity.class);

        Predicate predicate = spec == null ? cb.conjunction() : spec.toPredicate(root, cq, cb);
        if (predicate == null) {
            predicate = cb.conjunction();
        }

        cq.select(cb.count(root));
        cq.where(predicate);
        Long result = entityManager.createQuery(cq).getSingleResult();
        return result == null ? 0L : result;
    }

    private List<Selection<?>> buildSelections(Root<DemoExportUsageEntity> root, List<String> selectedFields) {
        List<Selection<?>> selections = new ArrayList<>(selectedFields.size());
        for (String field : selectedFields) {
            selections.add(root.get(field).alias(field));
        }
        return selections;
    }

    private List<Order> buildPageModeOrders(CriteriaBuilder cb, Root<DemoExportUsageEntity> root) {
        return List.of(
                cb.desc(root.get("usageDate")),
                cb.asc(root.get("tenantId")),
                cb.asc(root.get("productCode"))
        );
    }

    private List<Map<String, Object>> mapTuples(List<Tuple> tuples, List<String> selectedFields) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            Map<String, Object> row = new HashMap<>(selectedFields.size());
            for (String field : selectedFields) {
                row.put(field, tuple.get(field));
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> resolveSelectedFields(Map<String, Object> filters) {
        List<String> selectedFields = new ArrayList<>();
        if (filters != null) {
            Object rawFields = filters.get("__leafFields");
            if (rawFields instanceof Iterable<?> iterable) {
                for (Object rawField : iterable) {
                    if (rawField == null) {
                        continue;
                    }
                    String field = rawField.toString().trim();
                    if (!field.isEmpty() && EXPORT_FIELDS.contains(field) && !selectedFields.contains(field)) {
                        selectedFields.add(field);
                    }
                }
            }
        }
        if (selectedFields.isEmpty()) {
            selectedFields.addAll(EXPORT_FIELDS);
        }
        if (!selectedFields.contains("id")) {
            selectedFields.add(0, "id");
        }
        return selectedFields;
    }

    private int parsePositiveInt(Object rawValue, int defaultValue) {
        if (rawValue instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : defaultValue;
        }
        if (rawValue instanceof String str) {
            try {
                int value = Integer.parseInt(str.trim());
                return value > 0 ? value : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
