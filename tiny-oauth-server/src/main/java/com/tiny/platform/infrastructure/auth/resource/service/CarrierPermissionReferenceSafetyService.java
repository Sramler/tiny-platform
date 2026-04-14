package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import org.springframework.stereotype.Service;

/**
 * 删除或解绑权限前，检查权限是否仍被 menu / ui_action / api_endpoint 或其 requirement 行引用，
 * 避免产生悬空授权数据。
 *
 * <p><strong>非 compatibility 主链</strong>：不承担 requirement compatibility 或运行时兜底（CARD-13B 已移除）。
 * 仅保留权限引用安全检查（CARD-14B）。</p>
 */
@Service
public class CarrierPermissionReferenceSafetyService {

    private final MenuEntryRepository menuEntryRepository;
    private final UiActionEntryRepository uiActionEntryRepository;
    private final ApiEndpointEntryRepository apiEndpointEntryRepository;
    private final MenuPermissionRequirementRepository menuPermissionRequirementRepository;
    private final UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository;
    private final ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    public CarrierPermissionReferenceSafetyService(MenuEntryRepository menuEntryRepository,
                                                   UiActionEntryRepository uiActionEntryRepository,
                                                   ApiEndpointEntryRepository apiEndpointEntryRepository,
                                                   MenuPermissionRequirementRepository menuPermissionRequirementRepository,
                                                   UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository,
                                                   ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository) {
        this.menuEntryRepository = menuEntryRepository;
        this.uiActionEntryRepository = uiActionEntryRepository;
        this.apiEndpointEntryRepository = apiEndpointEntryRepository;
        this.menuPermissionRequirementRepository = menuPermissionRequirementRepository;
        this.uiActionPermissionRequirementRepository = uiActionPermissionRequirementRepository;
        this.apiEndpointPermissionRequirementRepository = apiEndpointPermissionRequirementRepository;
    }

    public boolean existsPermissionReference(Long permissionId, Long tenantId) {
        if (permissionId == null) {
            return false;
        }
        return menuEntryRepository.existsByRequiredPermissionIdAndTenantScope(permissionId, tenantId)
            || uiActionEntryRepository.existsByRequiredPermissionIdAndTenantScope(permissionId, tenantId)
            || apiEndpointEntryRepository.existsByRequiredPermissionIdAndTenantScope(permissionId, tenantId)
            || menuPermissionRequirementRepository.existsByPermissionIdAndTenantScope(permissionId, tenantId)
            || uiActionPermissionRequirementRepository.existsByPermissionIdAndTenantScope(permissionId, tenantId)
            || apiEndpointPermissionRequirementRepository.existsByPermissionIdAndTenantScope(permissionId, tenantId);
    }
}
