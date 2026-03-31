package com.tiny.platform.infrastructure.auth.datascope.service;

import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScope;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScopeItem;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeItemRepository;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据范围解析服务。
 *
 * <p>根据用户的有效角色、业务模块、访问类型，合并计算最终的数据可见范围。
 * 多角色取最宽覆盖（ALL > ORG_AND_CHILD > ORG > DEPT_AND_CHILD > DEPT > SELF）。</p>
 *
 * <p>如果用户没有任何角色配置了指定模块的数据范围，默认返回 {@code SELF}
 * （最小权限原则）。</p>
 *
 * <p><b>Contract B — ORG active scope 与 DEPT 形规则的几何锚点（稳定产品边界，2026-03-28）</b>：
 * 当 {@code TenantContext.activeScopeType=ORG} 时，{@code role_data_scope} 中 <strong>ORG / ORG_AND_CHILD</strong> 类规则以
 * {@code activeScopeId}（活动组织单元）为几何起点；而 <strong>DEPT / DEPT_AND_CHILD</strong> 类规则仍以用户的
 * <strong>主部门</strong>（{@code user_unit} 主记录对应的 unit）为几何起点，与 TENANT 作用域下一致。
 * 这是刻意边界：仓库内不存在无歧义的「活动组织下默认部门」选取规则，若将来要改为相对活动组织解析（选项 A），须先定义产品契约并单测锁定。
 * {@code activeScopeType=DEPT} 时，ORG 与 DEPT 形规则均以活动部门 id 为锚（见 {@link #resolveGeometryAnchorUnitId}）。</p>
 */
@Service
public class DataScopeResolverService {

    private static final Logger logger = LoggerFactory.getLogger(DataScopeResolverService.class);

    private static final Map<String, Integer> SCOPE_PRIORITY = Map.of(
        "ALL", 100,
        "TENANT", 100,
        "ORG_AND_CHILD", 80,
        "ORG", 70,
        "DEPT_AND_CHILD", 60,
        "DEPT", 50,
        "CUSTOM", 40,
        "SELF", 10
    );

    private final RoleDataScopeRepository dataScopeRepository;
    private final RoleDataScopeItemRepository dataScopeItemRepository;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final OrganizationUnitRepository orgUnitRepository;
    private final UserUnitRepository userUnitRepository;

    public DataScopeResolverService(RoleDataScopeRepository dataScopeRepository,
                                    RoleDataScopeItemRepository dataScopeItemRepository,
                                    EffectiveRoleResolutionService effectiveRoleResolutionService,
                                    OrganizationUnitRepository orgUnitRepository,
                                    UserUnitRepository userUnitRepository) {
        this.dataScopeRepository = dataScopeRepository;
        this.dataScopeItemRepository = dataScopeItemRepository;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.orgUnitRepository = orgUnitRepository;
        this.userUnitRepository = userUnitRepository;
    }

    /**
     * 解析用户在某模块某访问类型下的有效数据范围。
     *
     * @param userId     用户ID
     * @param tenantId   租户ID
     * @param module     业务模块标识
     * @param accessType READ 或 WRITE
     * @return 解析后的数据范围（不可变）
     */
    @Transactional(readOnly = true)
    public ResolvedDataScope resolve(Long userId, Long tenantId, String module, String accessType) {
        List<Long> activeRoleIds = resolveEffectiveRoleIdsForDataScope(userId, tenantId);

        if (activeRoleIds.isEmpty()) {
            logger.debug("DataScope resolve: no active roles for userId={}, tenantId={}, module={} -> SELF",
                userId, tenantId, module);
            return ResolvedDataScope.selfOnly();
        }

        List<RoleDataScope> scopes = dataScopeRepository
            .findByTenantIdAndModuleAndAccessTypeAndRoleIdIn(tenantId, module, accessType, activeRoleIds);

        if (scopes.isEmpty()) {
            logger.debug("DataScope resolve: no data scope rules for userId={}, tenantId={}, module={} -> SELF",
                userId, tenantId, module);
            return ResolvedDataScope.selfOnly();
        }

        String widestScopeType = scopes.stream()
            .map(RoleDataScope::getScopeType)
            .max(Comparator.comparingInt(st -> SCOPE_PRIORITY.getOrDefault(st, 0)))
            .orElse("SELF");

        if ("ALL".equals(widestScopeType) || "TENANT".equals(widestScopeType)) {
            return ResolvedDataScope.all();
        }

        if ("SELF".equals(widestScopeType)) {
            return ResolvedDataScope.selfOnly();
        }

        Set<Long> visibleUnitIds = new HashSet<>();
        Set<Long> visibleUserIds = new HashSet<>();
        boolean includeSelf = false;

        for (RoleDataScope scope : scopes) {
            String st = scope.getScopeType();
            switch (st) {
                case "ORG" -> {
                    Long anchor = resolveGeometryAnchorUnitId(tenantId, userId, "ORG");
                    if (anchor != null) {
                        Long refUnitId = findReferenceUnit(tenantId, userId, "ORG", anchor);
                        if (refUnitId != null) {
                            visibleUnitIds.add(refUnitId);
                        }
                    }
                }
                case "DEPT" -> {
                    Long anchor = resolveGeometryAnchorUnitId(tenantId, userId, "DEPT");
                    if (anchor != null) {
                        Long refUnitId = findReferenceUnit(tenantId, userId, "DEPT", anchor);
                        if (refUnitId != null) {
                            visibleUnitIds.add(refUnitId);
                        }
                    }
                }
                case "ORG_AND_CHILD" -> {
                    Long anchor = resolveGeometryAnchorUnitId(tenantId, userId, "ORG");
                    if (anchor != null) {
                        Long refUnitId = findReferenceUnit(tenantId, userId, "ORG", anchor);
                        if (refUnitId != null) {
                            visibleUnitIds.add(refUnitId);
                            visibleUnitIds.addAll(collectChildIds(tenantId, refUnitId));
                        }
                    }
                }
                case "DEPT_AND_CHILD" -> {
                    Long anchor = resolveGeometryAnchorUnitId(tenantId, userId, "DEPT");
                    if (anchor != null) {
                        Long refUnitId = findReferenceUnit(tenantId, userId, "DEPT", anchor);
                        if (refUnitId != null) {
                            visibleUnitIds.add(refUnitId);
                            visibleUnitIds.addAll(collectChildIds(tenantId, refUnitId));
                        }
                    }
                }
                case "CUSTOM" -> {
                    List<RoleDataScopeItem> items = dataScopeItemRepository.findByRoleDataScopeId(scope.getId());
                    for (RoleDataScopeItem item : items) {
                        if ("USER".equals(item.getTargetType())) {
                            visibleUserIds.add(item.getTargetId());
                        } else {
                            visibleUnitIds.add(item.getTargetId());
                        }
                    }
                }
                case "SELF" -> includeSelf = true;
                default -> { }
            }
        }

        if (scopes.stream().anyMatch(s -> "SELF".equals(s.getScopeType()))) {
            includeSelf = true;
        }

        logger.debug("DataScope resolve: userId={}, tenantId={}, module={}, accessType={}, widest={}, units={}, users={}, self={}",
            userId, tenantId, module, accessType, widestScopeType, visibleUnitIds.size(), visibleUserIds.size(), includeSelf);

        return ResolvedDataScope.ofUnitsAndUsers(visibleUnitIds, visibleUserIds, includeSelf);
    }

    /**
     * 与 {@link TenantContext} 对齐的有效角色集合（含 ORG/DEPT active scope 下的 scope 赋权）。
     */
    private List<Long> resolveEffectiveRoleIdsForDataScope(Long userId, Long tenantId) {
        if (TenantContext.isPlatformScope()) {
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId);
        }
        String ast = TenantContext.getActiveScopeType();
        Long asid = TenantContext.getActiveScopeId();
        if ((TenantContextContract.SCOPE_TYPE_ORG.equals(ast) || TenantContextContract.SCOPE_TYPE_DEPT.equals(ast))
            && asid != null && asid > 0) {
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId, ast, asid);
        }
        return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId);
    }

    /**
     * ORG/DEPT 类数据范围的几何锚点单元 id（进入 {@link #findReferenceUnit} 前的起点）。
     *
     * <ul>
     *   <li><b>TENANT / PLATFORM / 无 ORG·DEPT scope</b>：主部门（与历史 TENANT 一致）。</li>
     *   <li><b>activeScopeType=ORG</b>：{@code ORG}、{@code ORG_AND_CHILD} 规则 → {@code activeScopeId}；
     *       {@code DEPT}、{@code DEPT_AND_CHILD} 规则 → <b>主部门</b>（Contract B，见类 Javadoc）。</li>
     *   <li><b>activeScopeType=DEPT</b>：{@code ORG}/{@code DEPT}/{@code ORG_AND_CHILD}/{@code DEPT_AND_CHILD} 均 → {@code activeScopeId}
     *       （ORG 形规则经 {@link #findReferenceUnit} 向上解析到 ORG 节点）。</li>
     * </ul>
     *
     * @param ruleUnitType 规则语义 {@code ORG} 或 {@code DEPT}（与 switch 分支对应）
     */
    private Long resolveGeometryAnchorUnitId(Long tenantId, Long userId, String ruleUnitType) {
        String ast = TenantContext.getActiveScopeType();
        Long asid = TenantContext.getActiveScopeId();
        if (asid != null && asid > 0) {
            if (TenantContextContract.SCOPE_TYPE_ORG.equals(ast)) {
                if ("ORG".equals(ruleUnitType)) {
                    return asid;
                }
                return primaryDepartmentUnitIdOrNull(tenantId, userId);
            }
            if (TenantContextContract.SCOPE_TYPE_DEPT.equals(ast)) {
                return asid;
            }
        }
        return primaryDepartmentUnitIdOrNull(tenantId, userId);
    }

    private Long primaryDepartmentUnitIdOrNull(Long tenantId, Long userId) {
        return userUnitRepository.findPrimaryByTenantIdAndUserId(tenantId, userId)
            .map(uu -> uu.getUnitId())
            .orElse(null);
    }

    /**
     * 根据 scope 类型找到参考组织/部门节点。
     * 优先使用用户主部门，如果主部门类型不匹配则向上找。
     */
    private Long findReferenceUnit(Long tenantId, Long userId, String targetType, Long primaryUnitId) {
        Optional<OrganizationUnit> unit = orgUnitRepository.findByIdAndTenantId(primaryUnitId, tenantId);
        if (unit.isEmpty()) return null;

        OrganizationUnit ou = unit.get();
        if (ou.getUnitType().equals(targetType)) {
            return ou.getId();
        }

        if ("ORG".equals(targetType)) {
            Long current = ou.getParentId();
            int depth = 0;
            while (current != null && depth < 50) {
                Optional<OrganizationUnit> parent = orgUnitRepository.findByIdAndTenantId(current, tenantId);
                if (parent.isEmpty()) break;
                if ("ORG".equals(parent.get().getUnitType())) {
                    return parent.get().getId();
                }
                current = parent.get().getParentId();
                depth++;
            }
        }
        return primaryUnitId;
    }

    /**
     * 递归收集所有下级节点 ID（BFS）。
     */
    private Set<Long> collectChildIds(Long tenantId, Long parentId) {
        Set<Long> result = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(parentId);
        int iterations = 0;
        while (!queue.isEmpty() && iterations < 1000) {
            Long current = queue.poll();
            List<Long> children = orgUnitRepository.findChildIdsByTenantIdAndParentId(tenantId, current);
            for (Long childId : children) {
                if (result.add(childId)) {
                    queue.add(childId);
                }
            }
            iterations++;
        }
        return result;
    }
}
