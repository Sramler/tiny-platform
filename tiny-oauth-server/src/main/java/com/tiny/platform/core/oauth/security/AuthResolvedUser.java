package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;

import java.util.Set;

public record AuthResolvedUser(
    User user,
    Long activeTenantId,
    Set<Role> effectiveRoles
) {
}
