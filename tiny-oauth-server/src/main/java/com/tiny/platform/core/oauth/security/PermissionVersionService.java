package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.PermissionRefactorObservabilityProperties;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PermissionVersionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionVersionService.class);
    private static final String ACTIVE = "ACTIVE";

    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final PermissionAuthorityReadRepository permissionAuthorityReadRepository;
    private final PermissionVersionReadRepository permissionVersionReadRepository;
    private final PermissionRefactorObservabilityProperties observabilityProperties;
    private final Clock clock;
    private final Map<String, VersionInputDigest> latestDigestByContext = new ConcurrentHashMap<>();

    @Autowired
    public PermissionVersionService(TenantUserRepository tenantUserRepository,
                                    UserUnitRepository userUnitRepository,
                                    RoleAssignmentRepository roleAssignmentRepository,
                                    EffectiveRoleResolutionService effectiveRoleResolutionService,
                                    PermissionAuthorityReadRepository permissionAuthorityReadRepository,
                                    PermissionVersionReadRepository permissionVersionReadRepository,
                                    PermissionRefactorObservabilityProperties observabilityProperties) {
        this(tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, permissionAuthorityReadRepository, permissionVersionReadRepository, observabilityProperties, Clock.systemUTC());
    }

    PermissionVersionService(TenantUserRepository tenantUserRepository,
                             UserUnitRepository userUnitRepository,
                             RoleAssignmentRepository roleAssignmentRepository,
                             EffectiveRoleResolutionService effectiveRoleResolutionService,
                             PermissionAuthorityReadRepository permissionAuthorityReadRepository,
                             PermissionVersionReadRepository permissionVersionReadRepository,
                             PermissionRefactorObservabilityProperties observabilityProperties,
                             Clock clock) {
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.permissionAuthorityReadRepository = permissionAuthorityReadRepository;
        this.permissionVersionReadRepository = permissionVersionReadRepository;
        this.observabilityProperties = observabilityProperties;
        this.clock = clock;
    }

    public String resolvePermissionsVersion(Long userId, Long activeTenantId) {
        return resolvePermissionsVersion(userId, activeTenantId, TenantContextContract.SCOPE_TYPE_TENANT, activeTenantId);
    }

    public String resolvePermissionsVersion(Long userId, Long activeTenantId, String activeScopeType) {
        return resolvePermissionsVersion(userId, activeTenantId, activeScopeType, activeTenantId);
    }

    public String resolvePermissionsVersion(Long userId, Long activeTenantId, String activeScopeType, Long activeScopeId) {
        if (userId == null) {
            return null;
        }
        String normalizedScopeType = activeScopeType == null
            ? TenantContextContract.SCOPE_TYPE_TENANT
            : activeScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(normalizedScopeType)) {
            return resolvePlatformPermissionsVersion(userId, activeScopeId);
        }
        if ("ORG".equals(normalizedScopeType) || "DEPT".equals(normalizedScopeType)) {
            if (activeTenantId == null || activeTenantId <= 0) {
                return null;
            }
            return resolveTenantScopedPermissionsVersion(userId, activeTenantId, normalizedScopeType, activeScopeId);
        }
        if (activeTenantId == null || activeTenantId <= 0) {
            return null;
        }
        return resolveTenantScopedPermissionsVersion(
            userId,
            activeTenantId,
            TenantContextContract.SCOPE_TYPE_TENANT,
            activeTenantId
        );
    }

    private String resolveTenantScopedPermissionsVersion(Long userId,
                                                        Long activeTenantId,
                                                        String normalizedScopeType,
                                                        Long activeScopeId) {
        LocalDateTime now = LocalDateTime.now(clock);

        long membershipUpdatedAt = tenantUserRepository.findByTenantIdAndUserIdAndStatus(activeTenantId, userId, ACTIVE)
            .map(TenantUser::getUpdatedAt)
            .map(PermissionVersionService::toEpochMillis)
            .orElse(0L);
        long userUnitUpdatedAt = userUnitRepository.findByTenantIdAndUserIdAndStatus(activeTenantId, userId, ACTIVE).stream()
            .map(UserUnit::getUpdatedAt)
            .mapToLong(PermissionVersionService::toEpochMillis)
            .max()
            .orElse(0L);
        List<RoleAssignment> allAssignments = roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
                userId,
                activeTenantId,
                now
            );
        List<RoleAssignment> directAssignments = filterAssignmentsByActiveScope(allAssignments, normalizedScopeType, activeScopeId);
        long assignmentUpdatedAt = allAssignments.stream()
            .map(RoleAssignment::getUpdatedAt)
            .mapToLong(PermissionVersionService::toEpochMillis)
            .max()
            .orElse(0L);
        List<Long> effectiveRoleIds = resolveEffectiveRoleIds(userId, activeTenantId, normalizedScopeType, activeScopeId, false);
        String roleAssignmentInput = buildRoleAssignmentInput(directAssignments);
        String effectiveAuthorizationInput = buildEffectiveAuthorizationInput(effectiveRoleIds, activeTenantId);
        String newPermissionInput = buildNewPermissionInput(effectiveRoleIds, activeTenantId);
        String hierarchyInput = buildRoleHierarchyInput(effectiveRoleIds, activeTenantId);

        String fingerprint = "userId=" + userId
            + "|tenantId=" + activeTenantId
            + "|scopeType=" + normalizedScopeType
            + "|scopeId=" + activeScopeId
            + "|membershipUpdatedAt=" + membershipUpdatedAt
            + "|userUnitUpdatedAt=" + userUnitUpdatedAt
            + "|assignmentUpdatedAt=" + assignmentUpdatedAt
            + "|roleAssignmentInput=" + roleAssignmentInput
            + "|effectiveAuthorizationInput=" + effectiveAuthorizationInput
            + "|newPermissionInput=" + newPermissionInput
            + "|hierarchyInput=" + hierarchyInput;
        String permissionsVersion = sha256Hex(fingerprint);
        logPermissionVersionInputsIfNeeded(userId, activeTenantId, normalizedScopeType, activeScopeId, directAssignments, effectiveRoleIds, effectiveAuthorizationInput, newPermissionInput, hierarchyInput, permissionsVersion);
        return permissionsVersion;
    }

    private List<RoleAssignment> filterAssignmentsByActiveScope(List<RoleAssignment> assignments, String scopeType, Long scopeId) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        String normalized = scopeType == null
            ? TenantContextContract.SCOPE_TYPE_TENANT
            : scopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(normalized)) {
            return assignments.stream()
                .filter(a -> TenantContextContract.SCOPE_TYPE_TENANT.equalsIgnoreCase(a.getScopeType()))
                .toList();
        }
        if ("ORG".equals(normalized) || "DEPT".equals(normalized)) {
            return assignments.stream()
                .filter(a -> TenantContextContract.SCOPE_TYPE_TENANT.equalsIgnoreCase(a.getScopeType())
                    || (normalized.equalsIgnoreCase(a.getScopeType()) && java.util.Objects.equals(scopeId, a.getScopeId())))
                .toList();
        }
        return assignments.stream()
            .filter(a -> TenantContextContract.SCOPE_TYPE_TENANT.equalsIgnoreCase(a.getScopeType()))
            .toList();
    }

    private String resolvePlatformPermissionsVersion(Long userId, Long activeScopeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long assignmentUpdatedAt = toEpochMillis(
            roleAssignmentRepository.findLatestUpdatedAtForActiveUserInPlatform(userId, now)
        );
        List<Long> directRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInPlatform(userId, now);
        List<Long> effectiveRoleIds = resolveEffectiveRoleIds(userId, null, TenantContextContract.SCOPE_TYPE_PLATFORM, activeScopeId, true);
        String roleAssignmentInput = directRoleIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        String effectiveAuthorizationInput = buildEffectiveAuthorizationInput(effectiveRoleIds, null);
        String newPermissionInput = buildNewPermissionInput(effectiveRoleIds, null);
        String hierarchyInput = buildRoleHierarchyInput(effectiveRoleIds, null);
        String fingerprint = "userId=" + userId
            + "|scopeType=" + TenantContextContract.SCOPE_TYPE_PLATFORM
            + "|assignmentUpdatedAt=" + assignmentUpdatedAt
            + "|roleAssignmentInput=" + roleAssignmentInput
            + "|effectiveAuthorizationInput=" + effectiveAuthorizationInput
            + "|newPermissionInput=" + newPermissionInput
            + "|hierarchyInput=" + hierarchyInput;
        String permissionsVersion = sha256Hex(fingerprint);
        logPermissionVersionInputsIfNeeded(userId, null, TenantContextContract.SCOPE_TYPE_PLATFORM, activeScopeId, List.of(), effectiveRoleIds, effectiveAuthorizationInput, newPermissionInput, hierarchyInput, permissionsVersion);
        return permissionsVersion;
    }

    private List<Long> resolveEffectiveRoleIds(Long userId,
                                               Long activeTenantId,
                                               String scopeType,
                                               Long scopeId,
                                               boolean platformScope) {
        if (platformScope) {
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(userId);
        }
        String normalized = scopeType == null
            ? TenantContextContract.SCOPE_TYPE_TENANT
            : scopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(normalized)) {
            // Keep legacy behavior stable for tenant scope callers.
            return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, activeTenantId);
        }
        return effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, activeTenantId, normalized, scopeId);
    }

    private String buildRoleAssignmentInput(List<RoleAssignment> directAssignments) {
        return directAssignments.stream()
            .map(assignment -> String.join("~",
                normalizeString(assignment.getScopeType()),
                String.valueOf(assignment.getScopeId()),
                String.valueOf(assignment.getRoleId()),
                String.valueOf(assignment.getTenantId()),
                normalizeString(assignment.getStatus()),
                String.valueOf(toEpochMillis(assignment.getStartTime())),
                String.valueOf(toEpochMillis(assignment.getEndTime())),
                String.valueOf(toEpochMillis(assignment.getUpdatedAt()))
            ))
            .sorted()
            .collect(Collectors.joining(","));
    }

    private String buildEffectiveAuthorizationInput(List<Long> effectiveRoleIds, Long tenantId) {
        return loadEffectivePermissionCodeSet(effectiveRoleIds, tenantId).stream()
            .sorted()
            .collect(Collectors.joining(","));
    }

    private Set<String> loadEffectivePermissionCodeSet(List<Long> effectiveRoleIds, Long tenantId) {
        if (effectiveRoleIds == null || effectiveRoleIds.isEmpty()) {
            return Set.of();
        }
        // Old input participates in permissionsVersion only for the "effective authorization surface":
        // disabled/unknown permissions must be excluded to keep fail-closed semantics consistent.
        Set<String> codes = permissionAuthorityReadRepository.findEnabledPermissionCodesByRoleIds(new LinkedHashSet<>(effectiveRoleIds), tenantId);
        return codes == null ? Set.of() : new LinkedHashSet<>(codes);
    }

    private String buildNewPermissionInput(List<Long> effectiveRoleIds, Long tenantId) {
        return permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(new LinkedHashSet<>(effectiveRoleIds), tenantId).stream()
            .filter(snapshot -> snapshot.permissionCode() != null && !snapshot.permissionCode().isBlank())
            .filter(PermissionVersionReadRepository.PermissionSnapshot::enabled)
            .map(snapshot -> String.join("~",
                snapshot.permissionCode().trim(),
                snapshot.enabled() ? "1" : "0",
                String.valueOf(snapshot.tenantId()),
                String.valueOf(toEpochMillis(snapshot.updatedAt()))
            ))
            .sorted()
            .collect(Collectors.joining(","));
    }

    private String buildRoleHierarchyInput(List<Long> effectiveRoleIds, Long tenantId) {
        return permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(effectiveRoleIds, tenantId).stream()
            .map(snapshot -> String.join("~",
                String.valueOf(snapshot.parentRoleId()),
                String.valueOf(snapshot.childRoleId()),
                String.valueOf(snapshot.tenantId()),
                String.valueOf(toEpochMillis(snapshot.updatedAt()))
            ))
            .sorted()
            .collect(Collectors.joining(","));
    }

    private void logPermissionVersionInputsIfNeeded(Long userId,
                                                    Long tenantId,
                                                    String scopeType,
                                                    Long scopeId,
                                                    List<RoleAssignment> directAssignments,
                                                    List<Long> effectiveRoleIds,
                                                    String effectiveAuthorizationInput,
                                                    String newPermissionInput,
                                                    String hierarchyInput,
                                                    String permissionsVersion) {
        if (!observabilityProperties.isPermissionVersionDebugEnabled()
            || !observabilityProperties.isContextInGrayWindow(tenantId, scopeType)
            || !observabilityProperties.shouldSample()) {
            return;
        }
        List<Long> directRoleIds = directAssignments.stream()
            .map(RoleAssignment::getRoleId)
            .filter(id -> id != null && id > 0)
            .distinct()
            .sorted()
            .toList();
        VersionInputDigest current = new VersionInputDigest(
            sha256Hex(composeRoleAssignmentDigest(directAssignments)),
            sha256Hex(effectiveAuthorizationInput),
            sha256Hex(newPermissionInput),
            sha256Hex(hierarchyInput),
            permissionsVersion
        );
        String contextKey = userId + "|" + tenantId + "|" + scopeType + "|" + scopeId;
        VersionInputDigest previous = latestDigestByContext.put(contextKey, current);
        Set<String> reasons = resolveVersionChangeReasons(previous, current);
        String signalSource = ActiveTenantResponseSupport.resolveSignalSourceFromRequestContext();

        log.debug(
            "permissionsVersion inputs summary, signalSource={}, tenantId={}, userId={}, scopeType={}, scopeId={}, directRoleIds={}, effectiveRoleIds={}, oldPermissionCodeCount={}, newPermissionCodeCount={}, permissionEnabledDigest={}, hierarchyDigest={}, versionChangeReason={}, permissionsVersion={}",
            signalSource,
            tenantId,
            userId,
            scopeType,
            scopeId,
            directRoleIds,
            effectiveRoleIds,
            countDigestEntries(effectiveAuthorizationInput),
            countDigestEntries(newPermissionInput),
            sha256Hex(newPermissionInput),
            sha256Hex(hierarchyInput),
            reasons,
            permissionsVersion
        );
    }

    private String composeRoleAssignmentDigest(List<RoleAssignment> directAssignments) {
        return directAssignments.stream()
            .map(assignment -> String.join("~",
                normalizeString(assignment.getScopeType()),
                String.valueOf(assignment.getScopeId()),
                String.valueOf(assignment.getRoleId()),
                String.valueOf(assignment.getTenantId()),
                normalizeString(assignment.getStatus()),
                String.valueOf(toEpochMillis(assignment.getStartTime())),
                String.valueOf(toEpochMillis(assignment.getEndTime())),
                String.valueOf(toEpochMillis(assignment.getUpdatedAt()))
            ))
            .sorted()
            .collect(Collectors.joining(","));
    }

    private Set<String> resolveVersionChangeReasons(VersionInputDigest previous, VersionInputDigest current) {
        EnumSet<VersionChangeReason> reasons = EnumSet.noneOf(VersionChangeReason.class);
        if (previous == null) {
            reasons.add(VersionChangeReason.INITIAL_COMPUTE);
        } else {
            if (!previous.roleAssignmentDigest().equals(current.roleAssignmentDigest())) {
                reasons.add(VersionChangeReason.ROLE_ASSIGNMENT_CHANGED);
            }
            if (!previous.oldPermissionDigest().equals(current.oldPermissionDigest())) {
                reasons.add(VersionChangeReason.OLD_PERMISSION_INPUT_CHANGED);
            }
            if (!previous.newPermissionDigest().equals(current.newPermissionDigest())) {
                reasons.add(VersionChangeReason.ROLE_PERMISSION_CHANGED);
            }
            if (!previous.hierarchyDigest().equals(current.hierarchyDigest())) {
                reasons.add(VersionChangeReason.ROLE_HIERARCHY_CHANGED);
            }
            if (!previous.version().equals(current.version()) && reasons.isEmpty()) {
                reasons.add(VersionChangeReason.FINGERPRINT_REORDERED);
            }
        }
        return reasons.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int countDigestEntries(String digestInput) {
        if (digestInput == null || digestInput.isBlank()) {
            return 0;
        }
        return digestInput.split(",").length;
    }

    private String normalizeString(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private enum VersionChangeReason {
        INITIAL_COMPUTE,
        ROLE_ASSIGNMENT_CHANGED,
        OLD_PERMISSION_INPUT_CHANGED,
        ROLE_PERMISSION_CHANGED,
        PERMISSION_MASTER_CHANGED,
        ROLE_HIERARCHY_CHANGED,
        FINGERPRINT_REORDERED
    }

    private record VersionInputDigest(String roleAssignmentDigest,
                                      String oldPermissionDigest,
                                      String newPermissionDigest,
                                      String hierarchyDigest,
                                      String version) {
    }

    private static long toEpochMillis(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

}
