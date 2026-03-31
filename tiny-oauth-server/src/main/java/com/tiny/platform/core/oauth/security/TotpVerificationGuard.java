package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Component
public class TotpVerificationGuard {

    private static final Logger logger = LoggerFactory.getLogger(TotpVerificationGuard.class);

    public static final String FAILED_ATTEMPTS_KEY = "totpFailedAttempts";
    public static final String LAST_FAILED_AT_KEY = "totpLastFailedAt";
    public static final String LOCKED_UNTIL_KEY = "totpLockedUntil";

    private final UserAuthenticationMethodRepository authenticationMethodRepository;
    private final MfaProperties mfaProperties;
    private final TotpService totpService;

    public TotpVerificationGuard(UserAuthenticationMethodRepository authenticationMethodRepository,
                                 MfaProperties mfaProperties,
                                 TotpService totpService) {
        this.authenticationMethodRepository = authenticationMethodRepository;
        this.mfaProperties = mfaProperties;
        this.totpService = totpService;
    }

    public void verifyOrThrow(String username,
                              UserAuthenticationMethod method,
                              String secret,
                              String totpCode,
                              String invalidCodeMessage) {
        Map<String, Object> config = mutableConfig(method);
        LocalDateTime now = LocalDateTime.now();

        clearExpiredLockIfNeeded(method, config, now);

        LocalDateTime lockedUntil = parseDateTime(config.get(LOCKED_UNTIL_KEY));
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            throw new BadCredentialsException(buildLockedMessage(lockedUntil, now));
        }

        boolean verified = totpService.verify(secret, totpCode);
        if (!verified) {
            onFailure(username, method, config, now, invalidCodeMessage);
            return;
        }

        clearFailureState(config);
        method.setAuthenticationConfiguration(config);
    }

    private void onFailure(String username,
                           UserAuthenticationMethod method,
                           Map<String, Object> config,
                           LocalDateTime now,
                           String invalidCodeMessage) {
        int maxAttempts = Math.max(1, mfaProperties.getTotpMaxFailedAttempts());
        int windowMinutes = Math.max(1, mfaProperties.getTotpFailureWindowMinutes());
        int lockMinutes = Math.max(1, mfaProperties.getTotpLockMinutes());

        LocalDateTime lastFailedAt = parseDateTime(config.get(LAST_FAILED_AT_KEY));
        int failedAttempts = asInt(config.get(FAILED_ATTEMPTS_KEY));
        if (lastFailedAt == null || lastFailedAt.plusMinutes(windowMinutes).isBefore(now)) {
            failedAttempts = 0;
        }

        failedAttempts++;
        config.put(FAILED_ATTEMPTS_KEY, failedAttempts);
        config.put(LAST_FAILED_AT_KEY, now.toString());

        String message = invalidCodeMessage;
        if (failedAttempts >= maxAttempts) {
            LocalDateTime lockedUntil = now.plusMinutes(lockMinutes);
            config.put(LOCKED_UNTIL_KEY, lockedUntil.toString());
            message = buildLockedMessage(lockedUntil, now);
            logger.warn("用户 {} 的 TOTP 验证失败达到阈值，已锁定到 {}", username, lockedUntil);
        } else {
            logger.warn("用户 {} 的 TOTP 验证失败，当前失败次数={}", username, failedAttempts);
        }

        method.setAuthenticationConfiguration(config);
        save(method);
        throw new BadCredentialsException(message);
    }

    private void clearExpiredLockIfNeeded(UserAuthenticationMethod method,
                                          Map<String, Object> config,
                                          LocalDateTime now) {
        LocalDateTime lockedUntil = parseDateTime(config.get(LOCKED_UNTIL_KEY));
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            clearFailureState(config);
            method.setAuthenticationConfiguration(config);
            save(method);
        }
    }

    private void clearFailureState(Map<String, Object> config) {
        config.remove(FAILED_ATTEMPTS_KEY);
        config.remove(LAST_FAILED_AT_KEY);
        config.remove(LOCKED_UNTIL_KEY);
    }

    private Map<String, Object> mutableConfig(UserAuthenticationMethod method) {
        Map<String, Object> config = method.getAuthenticationConfiguration();
        if (config == null) {
            return new HashMap<>();
        }
        return new HashMap<>(config);
    }

    private void save(UserAuthenticationMethod method) {
        // tenant_id 可为 NULL：表示用户级全局认证方式，仍需持久化 TOTP 锁定/失败计数等状态
        if (method.getUserId() == null
                || method.getAuthenticationProvider() == null || method.getAuthenticationType() == null) {
            return;
        }
        authenticationMethodRepository.save(method);
    }

    private LocalDateTime parseDateTime(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String raw = String.valueOf(rawValue);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String buildLockedMessage(LocalDateTime lockedUntil, LocalDateTime now) {
        long seconds = java.time.Duration.between(now, lockedUntil).getSeconds();
        long minutes = Math.max(1, (seconds + 59) / 60);
        return "TOTP 验证尝试过多，请 " + minutes + " 分钟后重试";
    }
}
