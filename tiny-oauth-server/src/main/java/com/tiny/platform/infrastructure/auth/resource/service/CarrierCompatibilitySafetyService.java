package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointPermissionRequirement;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionPermissionRequirement;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.menu.domain.MenuPermissionRequirement;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility safety semantics that still remain after resource/shared-id retirement.
 *
 * <p>This service keeps the last two safety behaviors explicit:
 * compatibility group (= requirement_group 0) maintenance and "last carrier"
 * permission reference checks for revoke decisions.</p>
 */
@Service
public class CarrierCompatibilitySafetyService {

    private static final String CARRIER_MENU = "MENU";
    private static final String CARRIER_UI_ACTION = "UI_ACTION";
    private static final String CARRIER_API_ENDPOINT = "API_ENDPOINT";

    private final MenuEntryRepository menuEntryRepository;
    private final UiActionEntryRepository uiActionEntryRepository;
    private final ApiEndpointEntryRepository apiEndpointEntryRepository;
    private final MenuPermissionRequirementRepository menuPermissionRequirementRepository;
    private final UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository;
    private final ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    public CarrierCompatibilitySafetyService(MenuEntryRepository menuEntryRepository,
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

    @Transactional
    public void replaceCompatibilityRequirement(CarrierCompatibilityBinding binding) {
        if (binding == null || binding.carrierSourceId() == null || binding.carrierType() == null) {
            return;
        }
        deleteCompatibilityRequirements(binding.carrierType(), binding.carrierSourceId());
        if (binding.requiredPermissionId() == null) {
            return;
        }
        upsertCompatibilityRequirement(binding);
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

    private void deleteCompatibilityRequirements(String carrierType, Long carrierSourceId) {
        switch (carrierType) {
            case CARRIER_MENU -> menuPermissionRequirementRepository.deleteByMenuIdAndRequirementGroup(carrierSourceId, 0);
            case CARRIER_UI_ACTION ->
                uiActionPermissionRequirementRepository.deleteByUiActionIdAndRequirementGroup(carrierSourceId, 0);
            case CARRIER_API_ENDPOINT ->
                apiEndpointPermissionRequirementRepository.deleteByApiEndpointIdAndRequirementGroup(carrierSourceId, 0);
            default -> {
            }
        }
    }

    private void upsertCompatibilityRequirement(CarrierCompatibilityBinding binding) {
        switch (binding.carrierType()) {
            case CARRIER_MENU -> menuPermissionRequirementRepository.save(toMenuRequirement(binding));
            case CARRIER_UI_ACTION -> uiActionPermissionRequirementRepository.save(toUiActionRequirement(binding));
            case CARRIER_API_ENDPOINT -> apiEndpointPermissionRequirementRepository.save(toApiEndpointRequirement(binding));
            default -> {
            }
        }
    }

    private MenuPermissionRequirement toMenuRequirement(CarrierCompatibilityBinding binding) {
        MenuPermissionRequirement requirement = new MenuPermissionRequirement();
        requirement.setTenantId(binding.tenantId());
        requirement.setMenuId(binding.carrierSourceId());
        requirement.setRequirementGroup(0);
        requirement.setSortOrder(1);
        requirement.setPermissionId(binding.requiredPermissionId());
        requirement.setNegated(false);
        requirement.setCreatedAt(binding.createdAt());
        requirement.setCreatedBy(binding.createdBy());
        requirement.setUpdatedAt(binding.updatedAt());
        return requirement;
    }

    private UiActionPermissionRequirement toUiActionRequirement(CarrierCompatibilityBinding binding) {
        UiActionPermissionRequirement requirement = new UiActionPermissionRequirement();
        requirement.setTenantId(binding.tenantId());
        requirement.setUiActionId(binding.carrierSourceId());
        requirement.setRequirementGroup(0);
        requirement.setSortOrder(1);
        requirement.setPermissionId(binding.requiredPermissionId());
        requirement.setNegated(false);
        requirement.setCreatedAt(binding.createdAt());
        requirement.setCreatedBy(binding.createdBy());
        requirement.setUpdatedAt(binding.updatedAt());
        return requirement;
    }

    private ApiEndpointPermissionRequirement toApiEndpointRequirement(CarrierCompatibilityBinding binding) {
        ApiEndpointPermissionRequirement requirement = new ApiEndpointPermissionRequirement();
        requirement.setTenantId(binding.tenantId());
        requirement.setApiEndpointId(binding.carrierSourceId());
        requirement.setRequirementGroup(0);
        requirement.setSortOrder(1);
        requirement.setPermissionId(binding.requiredPermissionId());
        requirement.setNegated(false);
        requirement.setCreatedAt(binding.createdAt());
        requirement.setCreatedBy(binding.createdBy());
        requirement.setUpdatedAt(binding.updatedAt());
        return requirement;
    }
}
