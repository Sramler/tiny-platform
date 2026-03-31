package com.tiny.platform.infrastructure.auth.datascope.framework;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification 构建器：将 {@link ResolvedDataScope} 翻译为查询谓词。
 *
 * <p>业务模块在 Service 层中使用此工具将数据范围条件组合到现有查询中。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code @DataScope(module = "user")}
 * public Page<User> listUsers(Pageable pageable) {
 *     Specification<User> spec = (root, query, cb) -> cb.conjunction();
 *     spec = spec.and(tenantFilter(tenantId));
 *     spec = spec.and(DataScopeSpecification.apply(
 *         DataScopeContext.get(), currentUserId, "deptId", "createdBy"));
 *     return userRepository.findAll(spec, pageable);
 * }
 * </pre>
 *
 * <p>参数说明：</p>
 * <ul>
 *   <li>{@code unitField} — 实体中表示"所属部门/组织"的字段名（如 "deptId", "unitId"）</li>
 *   <li>{@code ownerField} — 实体中表示"创建者/拥有者"的字段名（如 "createdBy", "userId"）</li>
 * </ul>
 */
public final class DataScopeSpecification {

    private DataScopeSpecification() {}

    /**
     * 将已解析的数据范围转换为 JPA Specification。
     *
     * @param scope      当前数据范围（可为 null，此时不添加任何过滤）
     * @param currentUserId 当前用户 ID（用于 SELF 过滤）
     * @param unitField  实体中"所属组织/部门 ID"字段名；如果实体没有此字段则传 null
     * @param ownerField 实体中"拥有者/创建者 ID"字段名；如果实体没有此字段则传 null
     * @return JPA Specification（可直接 .and() 组合）
     */
    public static <T> Specification<T> apply(ResolvedDataScope scope, Long currentUserId,
                                              String unitField, String ownerField) {
        if (scope == null || scope.isUnrestricted()) {
            return (root, query, cb) -> cb.conjunction();
        }

        return (root, query, cb) -> {
            List<Predicate> orPredicates = new ArrayList<>();

            if (scope.isSelfOnly() || (!scope.getVisibleUnitIds().isEmpty() || !scope.getVisibleUserIds().isEmpty())) {
                if (scope.isSelfOnly() && ownerField != null) {
                    orPredicates.add(cb.equal(root.get(ownerField), currentUserId));
                }
            }

            if (!scope.getVisibleUnitIds().isEmpty() && unitField != null) {
                orPredicates.add(root.get(unitField).in(scope.getVisibleUnitIds()));
            }

            if (!scope.getVisibleUserIds().isEmpty() && ownerField != null) {
                orPredicates.add(root.get(ownerField).in(scope.getVisibleUserIds()));
            }

            if (scope.isSelfOnly() && scope.getVisibleUnitIds().isEmpty()
                && scope.getVisibleUserIds().isEmpty() && ownerField != null) {
                return cb.equal(root.get(ownerField), currentUserId);
            }

            if (orPredicates.isEmpty()) {
                return cb.equal(root.get(ownerField != null ? ownerField : "id"), currentUserId);
            }

            return cb.or(orPredicates.toArray(new Predicate[0]));
        };
    }
}
