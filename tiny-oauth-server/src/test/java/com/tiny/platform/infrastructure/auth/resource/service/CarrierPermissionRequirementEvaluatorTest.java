package com.tiny.platform.infrastructure.auth.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CarrierPermissionRequirementEvaluatorTest {

    private MenuPermissionRequirementRepository menuPermissionRequirementRepository;
    private UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository;
    private ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;
    private CarrierPermissionRequirementEvaluator evaluator;

    @BeforeEach
    void setUp() {
        menuPermissionRequirementRepository = Mockito.mock(MenuPermissionRequirementRepository.class);
        uiActionPermissionRequirementRepository = Mockito.mock(UiActionPermissionRequirementRepository.class);
        apiEndpointPermissionRequirementRepository = Mockito.mock(ApiEndpointPermissionRequirementRepository.class);
        evaluator = new CarrierPermissionRequirementEvaluator(
            menuPermissionRequirementRepository,
            uiActionPermissionRequirementRepository,
            apiEndpointPermissionRequirementRepository
        );
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of());
        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of());
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void shouldFailClosedWhenNoRequirementRowsExist() {
        MenuEntry menu = new MenuEntry();
        menu.setId(10L);
        menu.setPermission("system:user:list");

        assertThat(evaluator.isMenuAllowed(menu, Set.of("system:user:list"))).isFalse();
        assertThat(evaluator.isMenuAllowed(menu, Set.of("system:role:list"))).isFalse();
    }

    @Test
    void shouldSupportOrAcrossGroupsAndNegatedWithinGroup() {
        MenuEntry menu = new MenuEntry();
        menu.setId(11L);
        menu.setPermission("system:audit:auth:view");
        when(menuPermissionRequirementRepository.findRowsByMenuIdIn(anyCollection())).thenReturn(List.of(
            row(11L, 1, 1, "system:audit:auth:view", false),
            row(11L, 1, 2, "system:tenant:list", false),
            row(11L, 2, 1, "system:user:list", false),
            row(11L, 2, 2, "system:audit:auth:view", true)
        ));

        assertThat(evaluator.isMenuAllowed(menu, Set.of("system:user:list"))).isTrue();
        assertThat(evaluator.isMenuAllowed(menu, Set.of("system:tenant:list"))).isFalse();
        assertThat(evaluator.isMenuAllowed(menu, Set.of("system:audit:auth:view", "system:tenant:list"))).isTrue();
    }

    @Test
    void shouldEvaluateUiActionAndApiEndpointWithRequirementRows() {
        UiActionEntry uiAction = new UiActionEntry();
        uiAction.setId(20L);
        uiAction.setPermission("system:user:create");
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(30L);
        endpoint.setPermission("system:user:delete");

        when(uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(anyCollection())).thenReturn(List.of(
            row(20L, 1, 1, "system:user:create", false),
            row(20L, 1, 2, "system:user:delete", true)
        ));
        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of(
            row(30L, 1, 1, "system:user:delete", false)
        ));

        assertThat(evaluator.isUiActionAllowed(uiAction, Set.of("system:user:create"))).isTrue();
        assertThat(evaluator.isUiActionAllowed(uiAction, Set.of("system:user:create", "system:user:delete"))).isFalse();
        assertThat(evaluator.isApiEndpointAllowed(endpoint, Set.of("system:user:delete"))).isTrue();
        assertThat(evaluator.isApiEndpointAllowed(endpoint, Set.of("system:user:create"))).isFalse();
    }

    @Test
    void apiEndpoint_shouldFailClosed_when_permissionDisabled() {
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(31L);
        endpoint.setPermission("system:user:delete");
        endpoint.setRequiredPermissionId(9001L);

        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of(
            rowWithEnabled(31L, 0, 1, "system:user:delete", false, false)
        ));

        var detail = evaluator.evaluateApiEndpointRequirementDetail(endpoint, Set.of("system:user:delete"));
        assertThat(detail.decision()).isEqualTo("DENY");
        assertThat(detail.reason()).isEqualTo("REQUIREMENT_PERMISSION_DISABLED");
    }

    @Test
    void apiEndpoint_moduleWildcard_shouldSatisfySpecificPermissionRequirement() {
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(32L);
        endpoint.setPermission("scheduling:console:config");
        endpoint.setRequiredPermissionId(9002L);

        when(apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(anyCollection())).thenReturn(List.of(
            row(32L, 0, 1, "scheduling:console:config", false)
        ));

        var detail = evaluator.evaluateApiEndpointRequirementDetail(endpoint, Set.of("scheduling:*"));
        assertThat(detail.decision()).isEqualTo("ALLOW");
        assertThat(detail.reason()).isEqualTo("REQUIREMENT_GROUP_SATISFIED");
    }

    @Test
    void menu_shouldFailClosedWithoutRequirementRows_evenWithModuleWildcardAuthority() {
        MenuEntry menu = new MenuEntry();
        menu.setId(12L);
        menu.setPermission("scheduling:console:view");

        assertThat(evaluator.isMenuAllowed(menu, Set.of("scheduling:*"))).isFalse();
        assertThat(evaluator.isMenuAllowed(menu, Set.of("workflow:*"))).isFalse();
    }

    @Test
    void menu_requirement_detail_should_mark_rows_missing_fail_closed_when_rows_missing() {
        MenuEntry menu = new MenuEntry();
        menu.setId(13L);
        menu.setPermission("system:user:list");

        var detail = evaluator.resolveMenuRequirementDetails(List.of(menu), Set.of("system:user:list")).get(13L);
        assertThat(detail).isNotNull();
        assertThat(detail.reason()).isEqualTo("REQUIREMENT_ROWS_MISSING_FAIL_CLOSED");
        assertThat(detail.decision()).isEqualTo("DENY");
    }

    private CarrierPermissionRequirementRow row(Long carrierId, Integer group, Integer sortOrder, String permissionCode, boolean negated) {
        return rowWithEnabled(carrierId, group, sortOrder, permissionCode, negated, true);
    }

    private CarrierPermissionRequirementRow rowWithEnabled(Long carrierId,
                                                           Integer group,
                                                           Integer sortOrder,
                                                           String permissionCode,
                                                           boolean negated,
                                                           boolean enabled) {
        return new CarrierPermissionRequirementRow() {
            @Override
            public Long getCarrierId() {
                return carrierId;
            }

            @Override
            public Integer getRequirementGroup() {
                return group;
            }

            @Override
            public Integer getSortOrder() {
                return sortOrder;
            }

            @Override
            public String getPermissionCode() {
                return permissionCode;
            }

            @Override
            public Boolean getNegated() {
                return negated;
            }

            @Override
            public Boolean getPermissionEnabled() {
                return enabled;
            }
        };
    }
}
