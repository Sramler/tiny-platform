package com.tiny.platform.application.controller.role;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCardinalityRuleDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCardinalityRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleConstraintViolationLogDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleHierarchyEdgeDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleHierarchyEdgeUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleMutexRuleDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleMutexRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RolePrerequisiteRuleDto;
import com.tiny.platform.infrastructure.auth.role.dto.RolePrerequisiteRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintViolationLogQueryService;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintRuleAdminService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/role-constraints")
public class RoleConstraintRuleController {

    private static final int VIOLATIONS_MAX_PAGE_SIZE = 200;

    private final RoleConstraintRuleAdminService adminService;
    private final RoleConstraintViolationLogQueryService violationLogQueryService;

    public RoleConstraintRuleController(
        RoleConstraintRuleAdminService adminService,
        RoleConstraintViolationLogQueryService violationLogQueryService
    ) {
        this.adminService = adminService;
        this.violationLogQueryService = violationLogQueryService;
    }

    @PostMapping("/hierarchy")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> upsertHierarchy(@Valid @RequestBody RoleHierarchyEdgeUpsertDto dto) {
        Long tenantId = requiredActiveTenantId();
        adminService.upsertRoleHierarchyEdge(tenantId, dto.getChildRoleId(), dto.getParentRoleId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mutex")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> upsertMutex(@Valid @RequestBody RoleMutexRuleUpsertDto dto) {
        Long tenantId = requiredActiveTenantId();
        adminService.upsertRoleMutex(tenantId, dto.getRoleIdA(), dto.getRoleIdB());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/prerequisite")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> upsertPrerequisite(@Valid @RequestBody RolePrerequisiteRuleUpsertDto dto) {
        Long tenantId = requiredActiveTenantId();
        adminService.upsertRolePrerequisite(tenantId, dto.getRoleId(), dto.getRequiredRoleId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cardinality")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> upsertCardinality(@Valid @RequestBody RoleCardinalityRuleUpsertDto dto) {
        Long tenantId = requiredActiveTenantId();
        adminService.upsertRoleCardinality(tenantId, dto.getRoleId(), dto.getScopeType(), dto.getMaxAssignments());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hierarchy")
    @PreAuthorize("@roleManagementAccessGuard.canViewRoleConstraints(authentication)")
    public ResponseEntity<?> listHierarchy() {
        Long tenantId = requiredActiveTenantId();
        List<RoleHierarchyEdgeDto> result = adminService.listRoleHierarchyEdges(tenantId).stream()
            .map(e -> new RoleHierarchyEdgeDto(e.getChildRoleId(), e.getParentRoleId()))
            .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/hierarchy")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> deleteHierarchy(@RequestParam("childRoleId") Long childRoleId,
                                             @RequestParam("parentRoleId") Long parentRoleId) {
        Long tenantId = requiredActiveTenantId();
        adminService.deleteRoleHierarchyEdge(tenantId, childRoleId, parentRoleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mutex")
    @PreAuthorize("@roleManagementAccessGuard.canViewRoleConstraints(authentication)")
    public ResponseEntity<?> listMutex() {
        Long tenantId = requiredActiveTenantId();
        List<RoleMutexRuleDto> result = adminService.listRoleMutexRules(tenantId).stream()
            .map(r -> new RoleMutexRuleDto(r.getLeftRoleId(), r.getRightRoleId()))
            .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/mutex")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> deleteMutex(@RequestParam("roleIdA") Long roleIdA,
                                         @RequestParam("roleIdB") Long roleIdB) {
        Long tenantId = requiredActiveTenantId();
        adminService.deleteRoleMutex(tenantId, roleIdA, roleIdB);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/prerequisite")
    @PreAuthorize("@roleManagementAccessGuard.canViewRoleConstraints(authentication)")
    public ResponseEntity<?> listPrerequisite() {
        Long tenantId = requiredActiveTenantId();
        List<RolePrerequisiteRuleDto> result = adminService.listRolePrerequisiteRules(tenantId).stream()
            .map(r -> new RolePrerequisiteRuleDto(r.getRoleId(), r.getRequiredRoleId()))
            .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/prerequisite")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> deletePrerequisite(@RequestParam("roleId") Long roleId,
                                                @RequestParam("requiredRoleId") Long requiredRoleId) {
        Long tenantId = requiredActiveTenantId();
        adminService.deleteRolePrerequisite(tenantId, roleId, requiredRoleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cardinality")
    @PreAuthorize("@roleManagementAccessGuard.canViewRoleConstraints(authentication)")
    public ResponseEntity<?> listCardinality() {
        Long tenantId = requiredActiveTenantId();
        List<RoleCardinalityRuleDto> result = adminService.listRoleCardinalityRules(tenantId).stream()
            .map(r -> new RoleCardinalityRuleDto(r.getRoleId(), r.getScopeType(), r.getMaxAssignments()))
            .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/cardinality")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canManageRoleConstraints(authentication)")
    public ResponseEntity<?> deleteCardinality(@RequestParam("roleId") Long roleId,
                                               @RequestParam("scopeType") String scopeType) {
        Long tenantId = requiredActiveTenantId();
        adminService.deleteRoleCardinality(tenantId, roleId, scopeType);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/violations")
    @PreAuthorize("@roleManagementAccessGuard.canViewRoleConstraintViolations(authentication)")
    public ResponseEntity<?> listViolations(
        @RequestParam(value = "principalType", required = false) String principalType,
        @RequestParam(value = "principalId", required = false) Long principalId,
        @RequestParam(value = "scopeType", required = false) String scopeType,
        @RequestParam(value = "scopeId", required = false) Long scopeId,
        @RequestParam(value = "violationType", required = false) String violationType,
        @RequestParam(value = "violationCode", required = false) String violationCode,
        @RequestParam(value = "createdAtFrom", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime createdAtFrom,
        @RequestParam(value = "createdAtTo", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime createdAtTo,
        @NonNull Pageable pageable
    ) {
        Long tenantId = requiredActiveTenantId();

        Pageable safePageable = normalizeViolationsPageable(pageable);
        var page = violationLogQueryService
            .query(
                tenantId,
                principalType,
                principalId,
                scopeType,
                scopeId,
                violationType,
                violationCode,
                createdAtFrom,
                createdAtTo,
                safePageable
            )
            .map(l -> new RoleConstraintViolationLogDto(
                l.getId(),
                l.getPrincipalType(),
                l.getPrincipalId(),
                l.getScopeType(),
                l.getScopeId(),
                l.getViolationType(),
                l.getViolationCode(),
                l.getDirectRoleIds(),
                l.getEffectiveRoleIds(),
                l.getDetails(),
                l.getCreatedAt()
            ));
        PageResponse<RoleConstraintViolationLogDto> body = new PageResponse<>(page);
        return ResponseEntity.ok(body);
    }

    private Pageable normalizeViolationsPageable(@NonNull Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int requestedSize = pageable.getPageSize();
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, VIOLATIONS_MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort == null || sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return PageRequest.of(pageNumber, size, sort);
    }

    private Long requiredActiveTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantId;
    }
}

