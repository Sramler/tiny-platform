package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierPermissionRequirementRow;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionPermissionRequirementRepository;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuPermissionRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CarrierPermissionRequirementEvaluator {

    private final MenuPermissionRequirementRepository menuPermissionRequirementRepository;
    private final UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository;
    private final ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository;

    public CarrierPermissionRequirementEvaluator(
        MenuPermissionRequirementRepository menuPermissionRequirementRepository,
        UiActionPermissionRequirementRepository uiActionPermissionRequirementRepository,
        ApiEndpointPermissionRequirementRepository apiEndpointPermissionRequirementRepository
    ) {
        this.menuPermissionRequirementRepository = menuPermissionRequirementRepository;
        this.uiActionPermissionRequirementRepository = uiActionPermissionRequirementRepository;
        this.apiEndpointPermissionRequirementRepository = apiEndpointPermissionRequirementRepository;
    }

    public Set<Long> resolveAllowedMenuIds(Collection<MenuEntry> menus, Collection<String> authorityCodes) {
        if (menus == null || menus.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        Map<Long, List<CarrierPermissionRequirementRow>> requirementMap = buildRequirementMap(
            menuPermissionRequirementRepository.findRowsByMenuIdIn(
                menus.stream().map(MenuEntry::getId).filter(Objects::nonNull).toList()
            )
        );
        return resolveAllowedCarrierIds(
            menus.stream().collect(Collectors.toMap(MenuEntry::getId, MenuEntry::getPermission, (left, right) -> left, LinkedHashMap::new)),
            normalizedAuthorities,
            requirementMap
        );
    }

    public Set<Long> resolveAllowedUiActionIds(Collection<UiActionEntry> entries, Collection<String> authorityCodes) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        Map<Long, List<CarrierPermissionRequirementRow>> requirementMap = buildRequirementMap(
            uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(
                entries.stream().map(UiActionEntry::getId).filter(Objects::nonNull).toList()
            )
        );
        return resolveAllowedCarrierIds(
            entries.stream().collect(Collectors.toMap(UiActionEntry::getId, UiActionEntry::getPermission, (left, right) -> left, LinkedHashMap::new)),
            normalizedAuthorities,
            requirementMap
        );
    }

    public Set<Long> resolveAllowedApiEndpointIds(Collection<ApiEndpointEntry> entries, Collection<String> authorityCodes) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        Map<Long, List<CarrierPermissionRequirementRow>> requirementMap = buildRequirementMap(
            apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(
                entries.stream().map(ApiEndpointEntry::getId).filter(Objects::nonNull).toList()
            )
        );
        return resolveAllowedCarrierIds(
            entries.stream().collect(Collectors.toMap(ApiEndpointEntry::getId, ApiEndpointEntry::getPermission, (left, right) -> left, LinkedHashMap::new)),
            normalizedAuthorities,
            requirementMap
        );
    }

    public boolean isMenuAllowed(MenuEntry menu, Collection<String> authorityCodes) {
        return menu != null && menu.getId() != null && resolveAllowedMenuIds(List.of(menu), authorityCodes).contains(menu.getId());
    }

    public boolean isUiActionAllowed(UiActionEntry entry, Collection<String> authorityCodes) {
        return entry != null && entry.getId() != null && resolveAllowedUiActionIds(List.of(entry), authorityCodes).contains(entry.getId());
    }

    public boolean isApiEndpointAllowed(ApiEndpointEntry entry, Collection<String> authorityCodes) {
        return entry != null && entry.getId() != null && resolveAllowedApiEndpointIds(List.of(entry), authorityCodes).contains(entry.getId());
    }

    public Map<Long, RequirementAwareAuditDetail> resolveMenuRequirementDetails(Collection<MenuEntry> menus,
                                                                                   Collection<String> authorityCodes) {
        if (menus == null || menus.isEmpty()) {
            return Map.of();
        }
        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        List<Long> menuIds = menus.stream().map(MenuEntry::getId).filter(Objects::nonNull).toList();
        Map<Long, List<CarrierPermissionRequirementRow>> requirementMap = buildRequirementMap(
            menuPermissionRequirementRepository.findRowsByMenuIdIn(menuIds)
        );

        Map<Long, RequirementAwareAuditDetail> details = new LinkedHashMap<>();
        for (MenuEntry menu : menus) {
            if (menu == null || menu.getId() == null) {
                continue;
            }
            List<CarrierPermissionRequirementRow> rows = requirementMap.get(menu.getId());
            details.put(menu.getId(), evaluateCarrierWithDetail(
                "menu",
                menu.getId(),
                menu.getPermission(),
                rows,
                normalizedAuthorities,
                false
            ));
        }
        return details;
    }

    public Map<Long, RequirementAwareAuditDetail> resolveUiActionRequirementDetails(Collection<UiActionEntry> entries,
                                                                                        Collection<String> authorityCodes) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        List<Long> uiActionIds = entries.stream().map(UiActionEntry::getId).filter(Objects::nonNull).toList();
        Map<Long, List<CarrierPermissionRequirementRow>> requirementMap = buildRequirementMap(
            uiActionPermissionRequirementRepository.findRowsByUiActionIdIn(uiActionIds)
        );

        Map<Long, RequirementAwareAuditDetail> details = new LinkedHashMap<>();
        for (UiActionEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            List<CarrierPermissionRequirementRow> rows = requirementMap.get(entry.getId());
            details.put(entry.getId(), evaluateCarrierWithDetail(
                "ui_action",
                entry.getId(),
                entry.getPermission(),
                rows,
                normalizedAuthorities,
                false
            ));
        }
        return details;
    }

    public RequirementAwareAuditDetail evaluateApiEndpointRequirementDetail(ApiEndpointEntry endpoint,
                                                                                 Collection<String> authorityCodes) {
        if (endpoint == null) {
            return new RequirementAwareAuditDetail(
                "api_endpoint",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "DENY",
                "ENDPOINT_NULL"
            );
        }

        Set<String> normalizedAuthorities = normalizeAuthorities(authorityCodes);
        Long carrierId = endpoint.getId();
        String fallbackPermission = endpoint.getPermission();
        if (carrierId == null) {
            return new RequirementAwareAuditDetail(
                "api_endpoint",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "DENY",
                "ENDPOINT_ID_NULL"
            );
        }

        if (endpoint.getRequiredPermissionId() == null) {
            boolean hasPermission = StringUtils.hasText(fallbackPermission);
            String normalized = hasPermission ? fallbackPermission.trim() : null;
            boolean present = hasPermission && authorityImpliesPermission(normalizedAuthorities, normalized);
            List<String> matched = present ? List.of(normalized) : List.of();
            List<String> missing = (!present && hasPermission) ? List.of(normalized) : List.of();
            return new RequirementAwareAuditDetail(
                "api_endpoint",
                carrierId,
                null,
                matched,
                missing,
                List.of(),
                "DENY",
                "MISSING_REQUIRED_PERMISSION_ID"
            );
        }

        List<CarrierPermissionRequirementRow> rows = apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(List.of(carrierId));
        if (rows == null || rows.isEmpty()) {
            boolean hasPermission = StringUtils.hasText(fallbackPermission);
            String normalized = hasPermission ? fallbackPermission.trim() : null;
            boolean present = hasPermission && authorityImpliesPermission(normalizedAuthorities, normalized);
            List<String> matched = present ? List.of(normalized) : List.of();
            List<String> missing = (!present && hasPermission) ? List.of(normalized) : List.of();
            return new RequirementAwareAuditDetail(
                "api_endpoint",
                carrierId,
                null,
                matched,
                missing,
                List.of(),
                "DENY",
                "REQUIREMENT_ROWS_MISSING_FAIL_CLOSED"
            );
        }

        // For api endpoint, when requirement rows exist: it must match requirements (fail-closed otherwise).
        return evaluateCarrierWithDetail(
            "api_endpoint",
            carrierId,
            fallbackPermission,
            rows,
            normalizedAuthorities,
            true
        );
    }

    private Set<Long> resolveAllowedCarrierIds(Map<Long, String> carrierPermissionMap,
                                               Set<String> authorityCodes,
                                               Map<Long, List<CarrierPermissionRequirementRow>> requirementMap) {
        LinkedHashSet<Long> allowedIds = new LinkedHashSet<>();
        for (Map.Entry<Long, String> entry : carrierPermissionMap.entrySet()) {
            Long carrierId = entry.getKey();
            if (carrierId != null && isAllowed(carrierId, entry.getValue(), authorityCodes, requirementMap)) {
                allowedIds.add(carrierId);
            }
        }
        return allowedIds;
    }

    private boolean isAllowed(Long carrierId,
                              String fallbackPermission,
                              Set<String> authorityCodes,
                              Map<Long, List<CarrierPermissionRequirementRow>> requirementMap) {
        List<CarrierPermissionRequirementRow> rows = requirementMap.get(carrierId);
        if (rows == null || rows.isEmpty()) {
            return !StringUtils.hasText(fallbackPermission)
                || authorityImpliesPermission(authorityCodes, fallbackPermission.trim());
        }

        Map<Integer, List<CarrierPermissionRequirementRow>> groups = new LinkedHashMap<>();
        for (CarrierPermissionRequirementRow row : rows) {
            if (row == null || row.getRequirementGroup() == null) {
                continue;
            }
            groups.computeIfAbsent(row.getRequirementGroup(), ignored -> new java.util.ArrayList<>()).add(row);
        }

        for (List<CarrierPermissionRequirementRow> groupRows : groups.values()) {
            boolean groupSatisfied = groupRows.stream().allMatch(row -> requirementSatisfied(row, authorityCodes));
            if (groupSatisfied) {
                return true;
            }
        }
        return false;
    }

    private boolean requirementSatisfied(CarrierPermissionRequirementRow row, Set<String> authorityCodes) {
        if (row == null || !StringUtils.hasText(row.getPermissionCode())) {
            return false;
        }
        if (!Boolean.TRUE.equals(row.getPermissionEnabled())) {
            return false;
        }
        boolean present = authorityImpliesPermission(authorityCodes, row.getPermissionCode().trim());
        return Boolean.TRUE.equals(row.getNegated()) ? !present : present;
    }

    public boolean hasApiEndpointRequirements(Collection<Long> apiEndpointIds) {
        if (apiEndpointIds == null || apiEndpointIds.isEmpty()) {
            return false;
        }
        List<CarrierPermissionRequirementRow> rows =
            apiEndpointPermissionRequirementRepository.findRowsByApiEndpointIdIn(apiEndpointIds);
        return rows != null && !rows.isEmpty();
    }

    private RequirementAwareAuditDetail evaluateCarrierWithDetail(String carrierType,
                                                                   Long carrierId,
                                                                   String fallbackPermission,
                                                                   List<CarrierPermissionRequirementRow> rows,
                                                                   Set<String> authorityCodes,
                                                                   boolean enforceRequirementRowsWhenMissing) {
        if (carrierId == null) {
            return new RequirementAwareAuditDetail(
                carrierType,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "DENY",
                "CARRIER_ID_NULL"
            );
        }
        if (rows == null || rows.isEmpty()) {
            if (enforceRequirementRowsWhenMissing) {
                return new RequirementAwareAuditDetail(
                    carrierType,
                    carrierId,
                    null,
                    authorityIntersectionCodes(List.of(), authorityCodes),
                    computeFallbackMissingCodes(fallbackPermission, authorityCodes),
                    List.of(),
                    "DENY",
                    "REQUIREMENT_ROWS_MISSING_FAIL_CLOSED"
                );
            }
            return compatibilityFallbackDetail(carrierType, carrierId, fallbackPermission, authorityCodes);
        }

        Map<Integer, List<CarrierPermissionRequirementRow>> groups = new LinkedHashMap<>();
        for (CarrierPermissionRequirementRow row : rows) {
            if (row == null || row.getRequirementGroup() == null) {
                continue;
            }
            groups.computeIfAbsent(row.getRequirementGroup(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        if (groups.isEmpty()) {
            return enforceRequirementRowsWhenMissing
                ? new RequirementAwareAuditDetail(
                    carrierType,
                    carrierId,
                    null,
                    List.of(),
                    computeFallbackMissingCodes(fallbackPermission, authorityCodes),
                    List.of(),
                    "DENY",
                    "REQUIREMENT_GROUPS_MISSING_FAIL_CLOSED"
                )
                : compatibilityFallbackDetail(carrierType, carrierId, fallbackPermission, authorityCodes);
        }

        for (Map.Entry<Integer, List<CarrierPermissionRequirementRow>> entry : groups.entrySet()) {
            Integer groupId = entry.getKey();
            List<CarrierPermissionRequirementRow> groupRows = entry.getValue();
            boolean groupSatisfied = groupRows.stream().allMatch(row -> requirementSatisfied(row, authorityCodes));
            if (groupSatisfied) {
                return buildGroupDetail(carrierType, carrierId, groupId, groupRows, authorityCodes, "ALLOW");
            }
        }

        Integer firstGroupId = groups.keySet().iterator().next();
        List<CarrierPermissionRequirementRow> firstGroupRows = groups.get(firstGroupId);
        String reason = reasonForDeniedGroup(firstGroupRows);
        RequirementAwareAuditDetail denied = buildGroupDetail(carrierType, carrierId, firstGroupId, firstGroupRows, authorityCodes, "DENY");
        return new RequirementAwareAuditDetail(
            denied.carrierType(),
            denied.carrierId(),
            denied.requirementGroup(),
            denied.matchedPermissionCodes(),
            denied.missingPermissionCodes(),
            denied.negatedPermissionCodes(),
            denied.decision(),
            reason
        );
    }

    private RequirementAwareAuditDetail buildGroupDetail(String carrierType,
                                                          Long carrierId,
                                                          Integer requirementGroup,
                                                          List<CarrierPermissionRequirementRow> groupRows,
                                                          Set<String> authorityCodes,
                                                          String decision) {
        Set<String> permissionCodesInGroup = groupRows.stream()
            .map(CarrierPermissionRequirementRow::getPermissionCode)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> matchedPermissionCodes = permissionCodesInGroup.stream()
            .filter(code -> authorityImpliesPermission(authorityCodes, code))
            .toList();

        List<String> negatedPermissionCodes = groupRows.stream()
            .filter(row -> row != null && Boolean.TRUE.equals(row.getNegated()))
            .map(CarrierPermissionRequirementRow::getPermissionCode)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        List<String> missingPermissionCodes = groupRows.stream()
            .filter(row -> row != null && !Boolean.TRUE.equals(row.getNegated()))
            .filter(row -> {
                String code = row.getPermissionCode();
                boolean present = StringUtils.hasText(code) && authorityImpliesPermission(authorityCodes, code.trim());
                boolean enabled = Boolean.TRUE.equals(row.getPermissionEnabled());
                return !present || !enabled;
            })
            .map(CarrierPermissionRequirementRow::getPermissionCode)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();

        String reason = "ALLOW".equals(decision)
            ? "REQUIREMENT_GROUP_SATISFIED"
            : "REQUIREMENT_NOT_SATISFIED";

        return new RequirementAwareAuditDetail(
            carrierType,
            carrierId,
            requirementGroup,
            matchedPermissionCodes,
            missingPermissionCodes,
            negatedPermissionCodes,
            decision,
            reason
        );
    }

    private String reasonForDeniedGroup(List<CarrierPermissionRequirementRow> groupRows) {
        if (groupRows == null || groupRows.isEmpty()) {
            return "REQUIREMENT_NOT_SATISFIED";
        }
        boolean hasDisabledPositiveRow = groupRows.stream()
            .filter(row -> row != null && !Boolean.TRUE.equals(row.getNegated()))
            .anyMatch(row -> !Boolean.TRUE.equals(row.getPermissionEnabled()));
        if (hasDisabledPositiveRow) {
            return "REQUIREMENT_PERMISSION_DISABLED";
        }
        return "REQUIREMENT_NOT_SATISFIED";
    }

    private RequirementAwareAuditDetail compatibilityFallbackDetail(String carrierType,
                                                                       Long carrierId,
                                                                       String fallbackPermission,
                                                                       Set<String> authorityCodes) {
        boolean hasPermission = StringUtils.hasText(fallbackPermission);
        String normalized = hasPermission ? fallbackPermission.trim() : null;
        boolean present = hasPermission && authorityImpliesPermission(authorityCodes, normalized);
        boolean allowed = !hasPermission || present;

        List<String> matched = present ? List.of(normalized) : List.of();
        List<String> missing = (!allowed && hasPermission) ? List.of(normalized) : List.of();

        String reason;
        if (!hasPermission) {
            reason = allowed ? "COMPATIBILITY_NO_PERMISSION_CONSTRAINT" : "COMPATIBILITY_NO_PERMISSION_CONSTRAINT";
        } else {
            reason = "COMPATIBILITY_FALLBACK_SINGLE_PERMISSION";
        }

        return new RequirementAwareAuditDetail(
            carrierType,
            carrierId,
            null,
            matched,
            missing,
            List.of(),
            allowed ? "ALLOW" : "DENY",
            reason
        );
    }

    private List<String> computeFallbackMissingCodes(String fallbackPermission, Set<String> authorityCodes) {
        if (!StringUtils.hasText(fallbackPermission)) {
            return List.of();
        }
        String normalized = fallbackPermission.trim();
        boolean present = authorityImpliesPermission(authorityCodes, normalized);
        return present ? List.of() : List.of(normalized);
    }

    private List<String> authorityIntersectionCodes(List<String> candidates, Set<String> authorityCodes) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(authorityCodes::contains)
            .distinct()
            .toList();
    }

    private Map<Long, List<CarrierPermissionRequirementRow>> buildRequirementMap(List<CarrierPermissionRequirementRow> rows) {
        Map<Long, List<CarrierPermissionRequirementRow>> grouped = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return grouped;
        }
        for (CarrierPermissionRequirementRow row : rows) {
            if (row == null || row.getCarrierId() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.getCarrierId(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Set<String> normalizeAuthorities(Collection<String> authorityCodes) {
        if (authorityCodes == null || authorityCodes.isEmpty()) {
            return Set.of();
        }
        return authorityCodes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * True if {@code authorityCodes} grants {@code requiredPermission}: exact match, or a
     * {@code module:*} wildcard where {@code module} is the substring before the first ':' in the
     * required code (aligned with {@code SchedulingAccessGuard} / {@code WorkflowAccessGuard}).
     */
    private static boolean authorityImpliesPermission(Set<String> authorityCodes, String requiredPermission) {
        if (authorityCodes == null || authorityCodes.isEmpty() || !StringUtils.hasText(requiredPermission)) {
            return false;
        }
        String required = requiredPermission.trim();
        if (authorityCodes.contains(required)) {
            return true;
        }
        int colon = required.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String moduleWildcard = required.substring(0, colon) + ":*";
        return authorityCodes.contains(moduleWildcard);
    }
}
