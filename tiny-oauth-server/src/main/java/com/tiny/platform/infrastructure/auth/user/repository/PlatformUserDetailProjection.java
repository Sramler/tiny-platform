package com.tiny.platform.infrastructure.auth.user.repository;

import java.time.LocalDateTime;

public interface PlatformUserDetailProjection {

    Long getUserId();

    String getUsername();

    String getNickname();

    String getDisplayName();

    String getEmail();

    String getPhone();

    Object getUserEnabled();

    Object getAccountNonExpired();

    Object getAccountNonLocked();

    Object getCredentialsNonExpired();

    String getPlatformStatus();

    Object getHasPlatformRoleAssignment();

    LocalDateTime getLastLoginAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
