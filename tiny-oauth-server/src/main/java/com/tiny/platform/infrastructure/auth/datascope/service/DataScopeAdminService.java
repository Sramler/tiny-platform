package com.tiny.platform.infrastructure.auth.datascope.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScope;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScopeItem;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeItemRepository;
import com.tiny.platform.infrastructure.auth.datascope.repository.RoleDataScopeRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 数据范围规则管理服务（CRUD）。
 *
 * <p>提供角色-模块级数据范围规则的创建、查询、更新和删除能力。
 * 当 {@code scope_type = CUSTOM} 时，需同时管理 {@link RoleDataScopeItem} 明细。</p>
 */
@Service
public class DataScopeAdminService {

    private static final Logger logger = LoggerFactory.getLogger(DataScopeAdminService.class);

    private static final Set<String> VALID_SCOPE_TYPES = Set.of(
        "ALL", "TENANT", "ORG", "ORG_AND_CHILD", "DEPT", "DEPT_AND_CHILD", "SELF", "CUSTOM"
    );
    private static final Set<String> VALID_ACCESS_TYPES = Set.of("READ", "WRITE");
    private static final Set<String> VALID_TARGET_TYPES = Set.of("ORG", "DEPT", "USER");

    private final RoleDataScopeRepository dataScopeRepository;
    private final RoleDataScopeItemRepository dataScopeItemRepository;
    private final AuthorizationAuditService auditService;

    public DataScopeAdminService(RoleDataScopeRepository dataScopeRepository,
                                 RoleDataScopeItemRepository dataScopeItemRepository,
                                 AuthorizationAuditService auditService) {
        this.dataScopeRepository = dataScopeRepository;
        this.dataScopeItemRepository = dataScopeItemRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleDataScope> listByTenant(Long tenantId) {
        return dataScopeRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<RoleDataScope> listByRole(Long tenantId, Long roleId) {
        return dataScopeRepository.findByTenantIdAndRoleId(tenantId, roleId);
    }

    @Transactional(readOnly = true)
    public List<RoleDataScopeItem> listItems(Long roleDataScopeId) {
        return dataScopeItemRepository.findByRoleDataScopeId(roleDataScopeId);
    }

    /**
     * 创建或更新数据范围规则（upsert 语义）。
     * 同一 {@code (tenant_id, role_id, module, access_type)} 只保留一条。
     */
    @Transactional
    public RoleDataScope upsert(Long tenantId, Long roleId, String module, String scopeType,
                                String accessType, Long createdBy) {
        validateScopeType(scopeType);
        validateAccessType(accessType);
        if (module == null || module.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "module 不能为空");
        }

        dataScopeRepository.findByTenantIdAndRoleIdAndModuleAndAccessType(tenantId, roleId, module, accessType)
            .ifPresent(existing -> {
                dataScopeItemRepository.deleteByRoleDataScopeId(existing.getId());
                dataScopeRepository.delete(existing);
            });

        RoleDataScope rds = new RoleDataScope();
        rds.setTenantId(tenantId);
        rds.setRoleId(roleId);
        rds.setModule(module);
        rds.setScopeType(scopeType);
        rds.setAccessType(accessType);
        rds.setCreatedBy(createdBy);

        rds = dataScopeRepository.save(rds);
        logger.info("Upserted data scope: tenantId={}, roleId={}, module={}, scopeType={}, accessType={}",
            tenantId, roleId, module, scopeType, accessType);
        auditService.log(AuthorizationAuditEventType.DATA_SCOPE_UPSERT,
            tenantId, null, roleId, null, null, module, null,
            "{\"scopeType\":\"" + scopeType + "\",\"accessType\":\"" + accessType + "\"}",
            "SUCCESS", null);
        return rds;
    }

    /**
     * 为 CUSTOM 数据范围添加明细条目。
     */
    @Transactional
    public RoleDataScopeItem addItem(Long roleDataScopeId, String targetType, Long targetId) {
        validateTargetType(targetType);

        RoleDataScope parent = dataScopeRepository.findById(roleDataScopeId)
            .orElseThrow(() -> BusinessException.notFound("数据范围规则不存在"));
        if (!"CUSTOM".equals(parent.getScopeType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅 CUSTOM 类型支持添加明细条目");
        }

        RoleDataScopeItem item = new RoleDataScopeItem();
        item.setRoleDataScopeId(roleDataScopeId);
        item.setTargetType(targetType);
        item.setTargetId(targetId);
        return dataScopeItemRepository.save(item);
    }

    /**
     * 批量设置 CUSTOM 明细（先清空再写入）。
     */
    @Transactional
    public void replaceItems(Long roleDataScopeId, List<RoleDataScopeItem> items) {
        RoleDataScope parent = dataScopeRepository.findById(roleDataScopeId)
            .orElseThrow(() -> BusinessException.notFound("数据范围规则不存在"));
        if (!"CUSTOM".equals(parent.getScopeType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅 CUSTOM 类型支持设置明细条目");
        }

        dataScopeItemRepository.deleteByRoleDataScopeId(roleDataScopeId);
        for (RoleDataScopeItem item : items) {
            validateTargetType(item.getTargetType());
            item.setRoleDataScopeId(roleDataScopeId);
            dataScopeItemRepository.save(item);
        }
    }

    @Transactional
    public void delete(Long tenantId, Long roleId, String module, String accessType) {
        dataScopeRepository.findByTenantIdAndRoleIdAndModuleAndAccessType(tenantId, roleId, module, accessType)
            .ifPresent(existing -> {
                dataScopeItemRepository.deleteByRoleDataScopeId(existing.getId());
                dataScopeRepository.delete(existing);
                logger.info("Deleted data scope: tenantId={}, roleId={}, module={}, accessType={}",
                    tenantId, roleId, module, accessType);
                auditService.log(AuthorizationAuditEventType.DATA_SCOPE_DELETE,
                    tenantId, null, roleId, null, null, module, null,
                    "{\"scopeType\":\"" + existing.getScopeType() + "\",\"accessType\":\"" + accessType + "\"}",
                    "SUCCESS", null);
            });
    }

    private void validateScopeType(String scopeType) {
        if (scopeType == null || !VALID_SCOPE_TYPES.contains(scopeType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                "scope_type 必须为 ALL/TENANT/ORG/ORG_AND_CHILD/DEPT/DEPT_AND_CHILD/SELF/CUSTOM");
        }
    }

    private void validateAccessType(String accessType) {
        if (accessType == null || !VALID_ACCESS_TYPES.contains(accessType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "access_type 必须为 READ 或 WRITE");
        }
    }

    private void validateTargetType(String targetType) {
        if (targetType == null || !VALID_TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "target_type 必须为 ORG/DEPT/USER");
        }
    }
}
