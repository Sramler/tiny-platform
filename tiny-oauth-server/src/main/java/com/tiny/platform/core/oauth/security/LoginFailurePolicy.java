package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.LoginProtectionProperties;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class LoginFailurePolicy {

    private final LoginProtectionProperties properties;

    public LoginFailurePolicy(LoginProtectionProperties properties) {
        this.properties = properties;
    }

    public boolean isManuallyLocked(User user) {
        return user != null && !user.isAccountNonLocked();
    }

    public boolean isTemporarilyLocked(User user, LocalDateTime now) {
        if (user == null || now == null) {
            return false;
        }
        Integer failedCount = user.getFailedLoginCount();
        LocalDateTime lastFailedLoginAt = user.getLastFailedLoginAt();
        return failedCount != null
                && failedCount >= maxFailedAttempts()
                && lastFailedLoginAt != null
                && lastFailedLoginAt.plusMinutes(lockMinutes()).isAfter(now);
    }

    public boolean shouldResetFailureWindow(User user, LocalDateTime now) {
        if (user == null || now == null) {
            return false;
        }
        Integer failedCount = user.getFailedLoginCount();
        LocalDateTime lastFailedLoginAt = user.getLastFailedLoginAt();
        return failedCount != null
                && failedCount > 0
                && lastFailedLoginAt != null
                && !lastFailedLoginAt.plusMinutes(lockMinutes()).isAfter(now);
    }

    public void recordFailure(User user, LocalDateTime now) {
        if (user == null || now == null) {
            return;
        }
        if (shouldResetFailureWindow(user, now)) {
            user.setFailedLoginCount(0);
        }
        int currentCount = user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0;
        user.setFailedLoginCount(currentCount + 1);
        user.setLastFailedLoginAt(now);
    }

    public void clearExpiredFailureWindow(User user) {
        if (user == null) {
            return;
        }
        user.setFailedLoginCount(0);
    }

    public String buildTemporaryLockMessage(User user, LocalDateTime now) {
        if (user == null || now == null || user.getLastFailedLoginAt() == null) {
            return "登录失败次数过多，请稍后重试";
        }
        long minutes = remainingLockMinutes(user, now);
        return "登录失败次数过多，请 " + minutes + " 分钟后重试";
    }

    public String buildManualLockMessage() {
        return "账号已被锁定，请联系管理员";
    }

    private int maxFailedAttempts() {
        return Math.max(1, properties.getMaxFailedAttempts());
    }

    private int lockMinutes() {
        return Math.max(1, properties.getLockMinutes());
    }

    public int remainingLockMinutes(User user, LocalDateTime now) {
        if (user == null || now == null || user.getLastFailedLoginAt() == null) {
            return 0;
        }
        LocalDateTime lockedUntil = user.getLastFailedLoginAt().plusMinutes(lockMinutes());
        long seconds = Math.max(1, Duration.between(now, lockedUntil).getSeconds());
        return (int) ((seconds + 59) / 60);
    }
}
