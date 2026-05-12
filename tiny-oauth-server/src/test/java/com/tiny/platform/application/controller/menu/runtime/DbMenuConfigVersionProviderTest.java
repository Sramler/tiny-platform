package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionSnapshot;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbMenuConfigVersionProviderTest {

    @Test
    void shouldReadPersistedVersionWithoutScanningMenuTables() {
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        MenuPermissionRequirementRepository requirementRepository = mock(MenuPermissionRequirementRepository.class);
        RuntimeVersionStore runtimeVersionStore = mock(RuntimeVersionStore.class);
        DbMenuConfigVersionProvider provider = new DbMenuConfigVersionProvider(
            menuEntryRepository,
            requirementRepository,
            runtimeVersionStore
        );
        RuntimeVersionKey key = RuntimeVersionKey.of("MENU_CONFIG", null, "PLATFORM", null);
        when(runtimeVersionStore.find(key)).thenReturn(Optional.of(new RuntimeVersionSnapshot(
            1L,
            key,
            "persisted-v1",
            7L,
            "menu_update",
            null,
            9L
        )));

        String version = provider.resolveMenuConfigVersion(null, "PLATFORM", null);

        assertThat(version).isEqualTo("persisted-v1");
        verify(menuEntryRepository, never()).findByTenantIdIsNullAndTypeInOrderBySortAsc(any());
        verify(requirementRepository, never()).findByTenantScopeOrderByStableFields(any());
    }

    @Test
    void shouldInitializePersistentVersionFromDbFingerprintWhenMissing() {
        MenuEntryRepository menuEntryRepository = mock(MenuEntryRepository.class);
        MenuPermissionRequirementRepository requirementRepository = mock(MenuPermissionRequirementRepository.class);
        RuntimeVersionStore runtimeVersionStore = mock(RuntimeVersionStore.class);
        DbMenuConfigVersionProvider provider = new DbMenuConfigVersionProvider(
            menuEntryRepository,
            requirementRepository,
            runtimeVersionStore
        );
        RuntimeVersionKey key = RuntimeVersionKey.of("MENU_CONFIG", 2L, "TENANT", 2L);
        MenuEntry menu = new MenuEntry();
        menu.setId(10L);
        menu.setTenantId(2L);
        menu.setName("system");
        menu.setTitle("System");
        menu.setPath("/system");
        menu.setType(ResourceType.DIRECTORY.getCode());
        menu.setEnabled(true);

        when(runtimeVersionStore.find(key)).thenReturn(Optional.empty());
        when(menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(
            eq(2L),
            eq(List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()))
        )).thenReturn(List.of(menu));
        when(requirementRepository.findByTenantScopeOrderByStableFields(2L)).thenReturn(List.of());
        when(runtimeVersionStore.initializeIfAbsent(eq(key), any(), eq("menu_config_initial_fingerprint"), eq(null)))
            .thenAnswer(invocation -> new RuntimeVersionSnapshot(
                1L,
                key,
                invocation.getArgument(1),
                1L,
                "menu_config_initial_fingerprint",
                null,
                null
            ));

        String version = provider.resolveMenuConfigVersion(2L, "TENANT", 2L);

        assertThat(version).hasSize(64);
        ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);
        verify(runtimeVersionStore).initializeIfAbsent(eq(key), versionCaptor.capture(), eq("menu_config_initial_fingerprint"), eq(null));
        assertThat(versionCaptor.getValue()).isEqualTo(version);
    }
}
