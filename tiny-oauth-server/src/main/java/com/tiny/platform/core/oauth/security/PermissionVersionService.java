package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PermissionVersionService {

    private static final String ACTIVE = "ACTIVE";

    private final TenantUserRepository tenantUserRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final Clock clock;

    @Autowired
    public PermissionVersionService(TenantUserRepository tenantUserRepository,
                                    RoleAssignmentRepository roleAssignmentRepository,
                                    EffectiveRoleResolutionService effectiveRoleResolutionService) {
        this(tenantUserRepository, roleAssignmentRepository, effectiveRoleResolutionService, Clock.systemUTC());
    }

    PermissionVersionService(TenantUserRepository tenantUserRepository,
                             RoleAssignmentRepository roleAssignmentRepository,
                             EffectiveRoleResolutionService effectiveRoleResolutionService,
                             Clock clock) {
        this.tenantUserRepository = tenantUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.clock = clock;
    }

    public String resolvePermissionsVersion(Long userId, Long activeTenantId) {
        if (userId == null || activeTenantId == null || activeTenantId <= 0) {
            return null;
        }

        long membershipUpdatedAt = tenantUserRepository.findByTenantIdAndUserIdAndStatus(activeTenantId, userId, ACTIVE)
            .map(TenantUser::getUpdatedAt)
            .map(PermissionVersionService::toEpochMillis)
            .orElse(0L);
        long assignmentUpdatedAt = toEpochMillis(
            roleAssignmentRepository.findLatestUpdatedAtForActiveUserInTenant(
                userId,
                activeTenantId,
                LocalDateTime.now(clock)
            )
        );
        String normalizedAuthorities = effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(userId, activeTenantId).stream()
            .flatMap(role -> Stream.concat(
                authorityStream(role.getCode()),
                role.getResources().stream().flatMap(resource -> authorityStream(resource.getPermission()))
            ))
            .sorted()
            .collect(Collectors.joining(","));

        String fingerprint = userId
            + "|"
            + activeTenantId
            + "|"
            + membershipUpdatedAt
            + "|"
            + assignmentUpdatedAt
            + "|"
            + normalizedAuthorities;
        return sha256Hex(fingerprint);
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

    private static Stream<String> authorityStream(String candidate) {
        Set<String> values = new LinkedHashSet<>();
        addAuthorityValue(values, candidate);
        return values.stream();
    }

    private static void addAuthorityValue(Set<String> values, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            values.add(normalized);
        }
    }
}
