package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import com.tiny.platform.infrastructure.menu.runtime.MenuConfigVersionInvalidator;
import com.tiny.platform.infrastructure.runtime.version.JdbcRuntimeVersionStore;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionSnapshot;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real-DB behavior guard for menu runtime versioning.
 *
 * <p>This test intentionally uses the local E2E MySQL schema instead of an in-memory
 * store. It verifies the production cache invalidation contract: first version
 * resolution persists a MENU_CONFIG version row, matching If-None-Match returns
 * not-modified, and a version bump makes the old ETag stale.</p>
 */
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
class MenuRuntimeVersionBehaviorE2eTest {

    private static final RuntimeVersionKey PLATFORM_MENU_KEY =
        RuntimeVersionKey.of("MENU_CONFIG", null, TenantContextContract.SCOPE_TYPE_PLATFORM, null);
    private static final Path PROOF_FILE =
        Path.of("target", "menu-runtime-version-behavior-proof.json");

    private RuntimeVersionStore runtimeVersionStore;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(mysqlUrl());
        dataSource.setUsername(env("E2E_DB_USER", "root"));
        dataSource.setPassword(env("E2E_DB_PASSWORD", ""));
        runtimeVersionStore = new JdbcRuntimeVersionStore(new NamedParameterJdbcTemplate(dataSource));
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        TenantContext.setActiveTenantId(null);
        TenantContext.setActiveScopeId(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void menuRuntimeVersionShouldPersistAndInvalidateOldEtagAfterBump() throws Exception {
        Optional<RuntimeVersionSnapshot> before = runtimeVersionStore.find(PLATFORM_MENU_KEY);

        DbMenuConfigVersionProvider versionProvider = new DbMenuConfigVersionProvider(
            menuEntryRepository(),
            requirementRepository(),
            runtimeVersionStore
        );
        MenuRuntimeTreeService runtimeTreeService = new MenuRuntimeTreeService(
            menuService(),
            versionProvider,
            new InMemoryMenuRuntimeTreeCacheStore(),
            mock(PermissionVersionService.class)
        );
        MenuConfigVersionInvalidator invalidator = new MenuConfigVersionInvalidator(runtimeVersionStore);

        String resolvedVersion = versionProvider.resolveMenuConfigVersion(
            null,
            TenantContextContract.SCOPE_TYPE_PLATFORM,
            null
        );
        RuntimeVersionSnapshot afterResolve = runtimeVersionStore.find(PLATFORM_MENU_KEY).orElseThrow();

        MenuRuntimeTreeSnapshot first = runtimeTreeService.loadRuntimeTree(null, null);
        MenuRuntimeTreeSnapshot notModified = runtimeTreeService.loadRuntimeTree(null, first.etag());

        RuntimeVersionSnapshot afterBump = invalidator.bumpMenuConfigVersion(
            PLATFORM_MENU_KEY,
            "menu_runtime_behavior_e2e",
            null
        );
        MenuRuntimeTreeSnapshot afterBumpWithOldEtag = runtimeTreeService.loadRuntimeTree(null, first.etag());

        assertThat(afterResolve.versionValue()).isEqualTo(resolvedVersion);
        assertThat(afterResolve.versionSeq()).isGreaterThanOrEqualTo(before.map(RuntimeVersionSnapshot::versionSeq).orElse(0L));
        assertThat(notModified.notModified()).isTrue();
        assertThat(afterBump.versionSeq()).isGreaterThan(afterResolve.versionSeq());
        assertThat(afterBump.versionValue()).isNotEqualTo(afterResolve.versionValue());
        assertThat(afterBumpWithOldEtag.notModified()).isFalse();
        assertThat(afterBumpWithOldEtag.etag()).isNotEqualTo(first.etag());

        writeProof(before, afterResolve, afterBump, first, notModified, afterBumpWithOldEtag);
    }

    private static MenuEntryRepository menuEntryRepository() {
        MenuEntryRepository repository = mock(MenuEntryRepository.class);
        when(repository.findByTenantIdIsNullAndTypeInOrderBySortAsc(anyList())).thenReturn(List.of());
        return repository;
    }

    private static MenuPermissionRequirementRepository requirementRepository() {
        MenuPermissionRequirementRepository repository = mock(MenuPermissionRequirementRepository.class);
        when(repository.findByTenantScopeOrderByStableFields(nullable(Long.class))).thenReturn(List.of());
        return repository;
    }

    private static MenuService menuService() {
        MenuService service = mock(MenuService.class);
        ResourceResponseDto menu = new ResourceResponseDto();
        menu.setId(1L);
        menu.setName("platform-runtime-proof");
        menu.setTitle("Platform Runtime Proof");
        menu.setUrl("/platform/runtime-proof");
        menu.setEnabled(true);
        menu.setType(1);
        menu.setLeaf(true);
        menu.setChildren(List.of());
        when(service.menuTree()).thenReturn(List.of(menu));
        return service;
    }

    private static String mysqlUrl() {
        return "jdbc:mysql://" + env("E2E_DB_HOST", "127.0.0.1")
            + ":" + env("E2E_DB_PORT", "3306")
            + "/" + env("E2E_DB_NAME", "tiny_web")
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&useUnicode=true"
            + "&characterEncoding=UTF-8"
            + "&serverTimezone=Asia/Shanghai";
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void writeProof(Optional<RuntimeVersionSnapshot> before,
                                   RuntimeVersionSnapshot afterResolve,
                                   RuntimeVersionSnapshot afterBump,
                                   MenuRuntimeTreeSnapshot first,
                                   MenuRuntimeTreeSnapshot notModified,
                                   MenuRuntimeTreeSnapshot afterBumpWithOldEtag) throws Exception {
        Files.createDirectories(PROOF_FILE.getParent());
        String proof = """
            {
              "domain": "MENU_CONFIG",
              "scopeType": "PLATFORM",
              "rowExistedBefore": %s,
              "versionSeqBefore": %s,
              "versionValueBefore": %s,
              "versionSeqAfterResolve": %d,
              "versionValueAfterResolve": "%s",
              "etagBeforeBump": "%s",
              "sameEtagRequestBeforeBumpNotModified": %s,
              "versionSeqAfterBump": %d,
              "versionValueAfterBump": "%s",
              "oldEtagRequestAfterBumpNotModified": %s,
              "etagAfterBump": "%s"
            }
            """.formatted(
            before.isPresent(),
            before.map(snapshot -> Long.toString(snapshot.versionSeq())).orElse("null"),
            before.map(snapshot -> "\"" + snapshot.versionValue() + "\"").orElse("null"),
            afterResolve.versionSeq(),
            afterResolve.versionValue(),
            escape(first.etag()),
            notModified.notModified(),
            afterBump.versionSeq(),
            afterBump.versionValue(),
            afterBumpWithOldEtag.notModified(),
            escape(afterBumpWithOldEtag.etag())
        );
        Files.writeString(PROOF_FILE, proof, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
