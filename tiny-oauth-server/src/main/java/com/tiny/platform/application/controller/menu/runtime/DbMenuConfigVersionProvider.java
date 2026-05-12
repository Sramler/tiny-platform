package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.domain.MenuPermissionRequirement;
import com.tiny.platform.infrastructure.menu.runtime.MenuConfigVersionKeys;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class DbMenuConfigVersionProvider implements MenuConfigVersionProvider {

    private final MenuEntryRepository menuEntryRepository;
    private final MenuPermissionRequirementRepository requirementRepository;
    private final RuntimeVersionStore runtimeVersionStore;

    public DbMenuConfigVersionProvider(MenuEntryRepository menuEntryRepository,
                                       MenuPermissionRequirementRepository requirementRepository,
                                       RuntimeVersionStore runtimeVersionStore) {
        this.menuEntryRepository = menuEntryRepository;
        this.requirementRepository = requirementRepository;
        this.runtimeVersionStore = runtimeVersionStore;
    }

    @Override
    public String resolveMenuConfigVersion(Long activeTenantId, String activeScopeType, Long activeScopeId) {
        RuntimeVersionKey key = MenuConfigVersionKeys.fromActiveScope(activeTenantId, activeScopeType);
        Long menuTenantId = key.tenantId();
        String menuScopeType = key.scopeType();
        Long menuScopeId = key.scopeId();
        return runtimeVersionStore.find(key)
            .map(snapshot -> snapshot.versionValue())
            .orElseGet(() -> runtimeVersionStore.initializeIfAbsent(
                key,
                computeDbFingerprint(menuTenantId, menuScopeType, menuScopeId),
                "menu_config_initial_fingerprint",
                null
            ).versionValue());
    }

    private String computeDbFingerprint(Long menuTenantId, String menuScopeType, Long menuScopeId) {
        List<Integer> menuTypes = List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode());
        List<MenuEntry> menus = menuTenantId == null
            ? menuEntryRepository.findByTenantIdIsNullAndTypeInOrderBySortAsc(menuTypes)
            : menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(menuTenantId, menuTypes);
        List<MenuPermissionRequirement> requirements =
            requirementRepository.findByTenantScopeOrderByStableFields(menuTenantId);

        StringBuilder fingerprint = new StringBuilder(4096)
            .append("scopeType=").append(menuScopeType)
            .append("|tenantId=").append(menuTenantId)
            .append("|scopeId=").append(menuScopeId)
            .append("|menus=");
        menus.stream()
            .sorted((left, right) -> Long.compare(nullToZero(left.getId()), nullToZero(right.getId())))
            .forEach(menu -> appendMenu(fingerprint, menu));
        fingerprint.append("|requirements=");
        requirements.forEach(requirement -> appendRequirement(fingerprint, requirement));
        return sha256Hex(fingerprint.toString());
    }

    private static void appendMenu(StringBuilder out, MenuEntry menu) {
        out.append("[")
            .append(nullToZero(menu.getId())).append("~")
            .append(nullToZero(menu.getTenantId())).append("~")
            .append(s(menu.getResourceLevel())).append("~")
            .append(s(menu.getName())).append("~")
            .append(s(menu.getTitle())).append("~")
            .append(s(menu.getPath())).append("~")
            .append(s(menu.getIcon())).append("~")
            .append(Boolean.TRUE.equals(menu.getShowIcon())).append("~")
            .append(n(menu.getSort())).append("~")
            .append(s(menu.getComponent())).append("~")
            .append(s(menu.getRedirect())).append("~")
            .append(Boolean.TRUE.equals(menu.getHidden())).append("~")
            .append(Boolean.TRUE.equals(menu.getKeepAlive())).append("~")
            .append(s(menu.getPermission())).append("~")
            .append(nullToZero(menu.getRequiredPermissionId())).append("~")
            .append(n(menu.getType())).append("~")
            .append(nullToZero(menu.getParentId())).append("~")
            .append(Boolean.TRUE.equals(menu.getEnabled())).append("~")
            .append(epochMillis(menu.getUpdatedAt()))
            .append("]");
    }

    private static void appendRequirement(StringBuilder out, MenuPermissionRequirement requirement) {
        out.append("[")
            .append(nullToZero(requirement.getId())).append("~")
            .append(nullToZero(requirement.getTenantId())).append("~")
            .append(nullToZero(requirement.getMenuId())).append("~")
            .append(n(requirement.getRequirementGroup())).append("~")
            .append(n(requirement.getSortOrder())).append("~")
            .append(nullToZero(requirement.getPermissionId())).append("~")
            .append(Boolean.TRUE.equals(requirement.getNegated())).append("~")
            .append(epochMillis(requirement.getUpdatedAt()))
            .append("]");
    }

    private static String s(String value) {
        return value == null ? "" : value.trim();
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }

    private static long epochMillis(LocalDateTime value) {
        return value == null ? 0L : value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
