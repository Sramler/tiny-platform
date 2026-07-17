package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelStatus;
import com.tiny.platform.infrastructure.workflow.repository.ProcessModelRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessModelRuntimeBusinessAccessService {

    private static final String WORKFLOW_WILDCARD = "workflow:*";

    private final ProcessModelRepository repository;
    private final ProcessModelBusinessValidationService businessValidationService;

    public ProcessModelRuntimeBusinessAccessService(
        ProcessModelRepository repository,
        ProcessModelBusinessValidationService businessValidationService
    ) {
        this.repository = repository;
        this.businessValidationService = businessValidationService;
    }

    public void assertCanStartProcess(String processKey, Authentication authentication) {
        ScopeContext scope = currentScope();
        if (scope == null || processKey == null || processKey.isBlank()) {
            return;
        }

        List<ProcessModelEntity> candidates = repository.findRuntimeCandidatesInScope(
            scope.scopeType(),
            scope.tenantId(),
            processKey,
            ProcessModelStatus.DEPLOYED
        );
        if (candidates.isEmpty()) {
            return;
        }

        ProcessModelBusinessValidationService.BusinessMetadata metadata =
            businessValidationService.readBusinessMetadata(candidates.get(0).getBpmnXml());
        String requiredPermission = metadata.startPermission();
        if (requiredPermission == null || requiredPermission.isBlank()) {
            return;
        }
        if (!hasAuthority(authentication, requiredPermission)) {
            throw BusinessException.forbidden("缺少流程发起权限: " + requiredPermission);
        }
    }

    private static ScopeContext currentScope() {
        if (TenantContext.isPlatformScope()) {
            return new ScopeContext(ProcessModelScopeType.PLATFORM, null);
        }
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId == null || activeTenantId <= 0) {
            return null;
        }
        return new ScopeContext(ProcessModelScopeType.TENANT, activeTenantId);
    }

    private static boolean hasAuthority(Authentication authentication, String requiredPermission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority -> WORKFLOW_WILDCARD.equals(authority) || requiredPermission.equals(authority));
    }

    private record ScopeContext(ProcessModelScopeType scopeType, Long tenantId) {
    }
}
