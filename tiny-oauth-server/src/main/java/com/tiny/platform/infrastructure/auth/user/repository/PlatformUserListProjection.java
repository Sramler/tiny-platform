package com.tiny.platform.infrastructure.auth.user.repository;

import java.time.LocalDateTime;

public interface PlatformUserListProjection {

    Long getUserId();

    String getUsername();

    String getNickname();

    String getDisplayName();

    Object getUserEnabled();

    String getPlatformStatus();

    Object getHasPlatformRoleAssignment();

    LocalDateTime getUpdatedAt();
}
