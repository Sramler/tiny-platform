package com.tiny.platform.infrastructure.auth.org.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.dto.OrganizationUnitResponseDto;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 组织/部门树 CRUD 服务。
 *
 * <p>提供租户隔离的组织树管理能力，包括：</p>
 * <ul>
 *   <li>树结构 CRUD（创建/读取/更新/删除节点）</li>
 *   <li>环检测（防止 parent 形成循环引用）</li>
 *   <li>删除前检查（有子节点或有成员不允许删除）</li>
 * </ul>
 */
@Service
public class OrganizationUnitService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationUnitService.class);

    private static final Set<String> VALID_UNIT_TYPES = Set.of("ORG", "DEPT");
    private static final int MAX_TREE_DEPTH = 50;

    private final OrganizationUnitRepository orgUnitRepository;
    private final UserUnitRepository userUnitRepository;
    private final AuthorizationAuditService auditService;

    public OrganizationUnitService(OrganizationUnitRepository orgUnitRepository,
                                   UserUnitRepository userUnitRepository,
                                   AuthorizationAuditService auditService) {
        this.orgUnitRepository = orgUnitRepository;
        this.userUnitRepository = userUnitRepository;
        this.auditService = auditService;
    }

    /**
     * 获取租户内完整组织树（递归组装）。
     */
    @DataScope(module = "org")
    public List<OrganizationUnitResponseDto> getTree(Long tenantId) {
        List<OrganizationUnit> all = orgUnitRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        if (!requiresDataScopeFilter()) {
            return buildTree(all, null);
        }

        Set<Long> directlyVisibleIds = collectDirectlyVisibleUnitIds(all);
        if (directlyVisibleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> treeVisibleIds = new LinkedHashSet<>(directlyVisibleIds);
        Map<Long, OrganizationUnit> indexedUnits = indexUnits(all);
        for (Long unitId : directlyVisibleIds) {
            OrganizationUnit current = indexedUnits.get(unitId);
            Long currentParentId = current != null ? current.getParentId() : null;
            while (currentParentId != null) {
                treeVisibleIds.add(currentParentId);
                OrganizationUnit parent = indexedUnits.get(currentParentId);
                currentParentId = parent != null ? parent.getParentId() : null;
            }
        }

        List<OrganizationUnit> filteredUnits = all.stream()
            .filter(unit -> treeVisibleIds.contains(unit.getId()))
            .toList();
        return buildTree(filteredUnits, null);
    }

    /**
     * 获取扁平列表（不做树组装）。
     */
    @DataScope(module = "org")
    public List<OrganizationUnitResponseDto> list(Long tenantId) {
        List<OrganizationUnit> all = orgUnitRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        if (requiresDataScopeFilter()) {
            all = all.stream()
                .filter(this::isDirectlyVisibleForRead)
                .toList();
        }
        return all.stream().map(this::toDto).toList();
    }

    @DataScope(module = "org")
    public OrganizationUnitResponseDto getById(Long tenantId, Long id) {
        OrganizationUnit unit = orgUnitRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));
        if (requiresDataScopeFilter() && !isDirectlyVisibleForRead(unit)) {
            throw BusinessException.notFound("组织/部门节点不存在");
        }
        return toDto(unit);
    }

    @Transactional
    public OrganizationUnitResponseDto create(Long tenantId, String unitType, String code, String name,
                                              Long parentId, Integer sortOrder, Long createdBy) {
        validateUnitType(unitType);
        if (orgUnitRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw BusinessException.alreadyExists("编码已存在: " + code);
        }
        if (parentId != null) {
            orgUnitRepository.findByIdAndTenantId(parentId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("上级节点不存在"));
        }

        OrganizationUnit unit = new OrganizationUnit();
        unit.setTenantId(tenantId);
        unit.setUnitType(unitType);
        unit.setCode(code);
        unit.setName(name);
        unit.setParentId(parentId);
        unit.setSortOrder(sortOrder != null ? sortOrder : 0);
        unit.setCreatedBy(createdBy);

        unit = orgUnitRepository.save(unit);
        logger.info("Created organization unit: tenantId={}, id={}, code={}, type={}", tenantId, unit.getId(), code, unitType);
        auditService.logSuccess(AuthorizationAuditEventType.ORG_UNIT_CREATE,
            tenantId, null, null, unitType, unit.getId(),
            "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}");
        return toDto(unit);
    }

    @Transactional
    public OrganizationUnitResponseDto update(Long tenantId, Long id, String name, Long parentId,
                                              Integer sortOrder, String status) {
        OrganizationUnit unit = orgUnitRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));

        if (parentId != null && !Objects.equals(parentId, unit.getParentId())) {
            if (parentId.equals(id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "不能将自身设为上级");
            }
            orgUnitRepository.findByIdAndTenantId(parentId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("上级节点不存在"));
            detectCycle(tenantId, id, parentId);
            unit.setParentId(parentId);
        }

        if (name != null) {
            unit.setName(name);
        }
        if (sortOrder != null) {
            unit.setSortOrder(sortOrder);
        }
        if (status != null) {
            unit.setStatus(status);
        }

        unit = orgUnitRepository.save(unit);
        logger.info("Updated organization unit: tenantId={}, id={}", tenantId, id);
        auditService.logSuccess(AuthorizationAuditEventType.ORG_UNIT_UPDATE,
            tenantId, null, null, unit.getUnitType(), id, null);
        return toDto(unit);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        OrganizationUnit unit = orgUnitRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));

        if (orgUnitRepository.existsByTenantIdAndParentId(tenantId, id)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该节点下存在子节点，不允许删除");
        }
        long memberCount = userUnitRepository.countActiveByTenantIdAndUnitId(tenantId, id);
        if (memberCount > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该节点下存在成员，不允许删除");
        }

        orgUnitRepository.delete(unit);
        logger.info("Deleted organization unit: tenantId={}, id={}, code={}", tenantId, id, unit.getCode());
        auditService.logSuccess(AuthorizationAuditEventType.ORG_UNIT_DELETE,
            tenantId, null, null, unit.getUnitType(), id,
            "{\"code\":\"" + unit.getCode() + "\",\"name\":\"" + unit.getName() + "\"}");
    }

    /**
     * 环检测：将 id 挂到 newParentId 下后，从 newParentId 向上遍历祖先链，
     * 如果遇到 id 本身则形成环。
     */
    private void detectCycle(Long tenantId, Long nodeId, Long newParentId) {
        Set<Long> visited = new HashSet<>();
        Long current = newParentId;
        int depth = 0;
        while (current != null && depth < MAX_TREE_DEPTH) {
            if (current.equals(nodeId)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "移动节点会形成循环引用");
            }
            if (!visited.add(current)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "组织树存在已有的循环引用，请先修复");
            }
            Optional<OrganizationUnit> parent = orgUnitRepository.findByIdAndTenantId(current, tenantId);
            current = parent.map(OrganizationUnit::getParentId).orElse(null);
            depth++;
        }
    }

    private void validateUnitType(String unitType) {
        if (unitType == null || !VALID_UNIT_TYPES.contains(unitType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "unit_type 必须为 ORG 或 DEPT");
        }
    }

    private Map<Long, OrganizationUnit> indexUnits(List<OrganizationUnit> all) {
        Map<Long, OrganizationUnit> indexedUnits = new LinkedHashMap<>();
        for (OrganizationUnit unit : all) {
            indexedUnits.put(unit.getId(), unit);
        }
        return indexedUnits;
    }

    private Set<Long> collectDirectlyVisibleUnitIds(List<OrganizationUnit> all) {
        Set<Long> visibleIds = new LinkedHashSet<>();
        for (OrganizationUnit unit : all) {
            if (isDirectlyVisibleForRead(unit)) {
                visibleIds.add(unit.getId());
            }
        }
        return visibleIds;
    }

    private boolean isDirectlyVisibleForRead(OrganizationUnit unit) {
        if (unit == null) {
            return false;
        }
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null || scope.isUnrestricted()) {
            return true;
        }

        Long currentUserId = extractCurrentUserId();
        if (scope.isSelfOnly() && currentUserId != null && Objects.equals(currentUserId, unit.getCreatedBy())) {
            return true;
        }
        if (unit.getCreatedBy() != null && scope.getVisibleUserIds().contains(unit.getCreatedBy())) {
            return true;
        }
        return scope.getVisibleUnitIds().contains(unit.getId());
    }

    private boolean requiresDataScopeFilter() {
        ResolvedDataScope scope = DataScopeContext.get();
        return scope != null && !scope.isUnrestricted();
    }

    private Long extractCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }

    private List<OrganizationUnitResponseDto> buildTree(List<OrganizationUnit> all, Long parentId) {
        List<OrganizationUnitResponseDto> result = new ArrayList<>();
        for (OrganizationUnit unit : all) {
            if (Objects.equals(unit.getParentId(), parentId)) {
                OrganizationUnitResponseDto dto = toDto(unit);
                dto.setChildren(buildTree(all, unit.getId()));
                result.add(dto);
            }
        }
        return result;
    }

    private OrganizationUnitResponseDto toDto(OrganizationUnit unit) {
        OrganizationUnitResponseDto dto = new OrganizationUnitResponseDto();
        dto.setId(unit.getId());
        dto.setTenantId(unit.getTenantId());
        dto.setParentId(unit.getParentId());
        dto.setUnitType(unit.getUnitType());
        dto.setCode(unit.getCode());
        dto.setName(unit.getName());
        dto.setSortOrder(unit.getSortOrder());
        dto.setStatus(unit.getStatus());
        dto.setCreatedAt(unit.getCreatedAt());
        dto.setCreatedBy(unit.getCreatedBy());
        dto.setUpdatedAt(unit.getUpdatedAt());
        return dto;
    }
}
