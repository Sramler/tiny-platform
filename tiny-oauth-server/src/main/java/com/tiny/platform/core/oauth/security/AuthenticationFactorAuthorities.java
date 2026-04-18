package com.tiny.platform.core.oauth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MFA 因子 authority 统一映射。
 *
 * 目标：
 * - 将已完成因子显式映射为 GrantedAuthority，便于 Spring 授权层消费
 * - 保持对旧 completedFactors 字段的兼容读取
 */
public final class AuthenticationFactorAuthorities {

    public static final String FACTOR_AUTHORITY_PREFIX = "FACTOR_";

    private AuthenticationFactorAuthorities() {
    }

    public static Collection<? extends GrantedAuthority> augmentAuthorities(
            Collection<? extends GrantedAuthority> authorities,
            Set<MultiFactorAuthenticationToken.AuthenticationFactorType> completedFactors) {
        Map<String, GrantedAuthority> merged = new LinkedHashMap<>();
        Instant synthesizedFactorIssuedAt = Instant.now();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                GrantedAuthority normalized = normalizeAuthority(authority, synthesizedFactorIssuedAt);
                if (normalized == null || normalized.getAuthority() == null || normalized.getAuthority().isBlank()) {
                    continue;
                }
                merged.merge(
                        normalized.getAuthority(),
                        normalized,
                        AuthenticationFactorAuthorities::preferAuthority
                );
            }
        }
        if (completedFactors != null) {
            for (MultiFactorAuthenticationToken.AuthenticationFactorType factor : completedFactors) {
                String factorAuthority = toAuthority(factor);
                if (factorAuthority != null) {
                    merged.merge(
                            factorAuthority,
                            FactorGrantedAuthority.withAuthority(factorAuthority)
                                    .issuedAt(synthesizedFactorIssuedAt)
                                    .build(),
                            AuthenticationFactorAuthorities::preferAuthority
                    );
                }
            }
        }
        return List.copyOf(merged.values());
    }

    public static boolean hasFactor(Authentication authentication,
                                    MultiFactorAuthenticationToken.AuthenticationFactorType factor) {
        if (authentication == null || factor == null || factor == MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
            return false;
        }
        return extractFactors(authentication).contains(factor);
    }

    public static boolean hasAnyFactor(Authentication authentication) {
        return !extractFactors(authentication).isEmpty();
    }

    public static EnumSet<MultiFactorAuthenticationToken.AuthenticationFactorType> extractFactors(Authentication authentication) {
        EnumSet<MultiFactorAuthenticationToken.AuthenticationFactorType> factors =
                EnumSet.noneOf(MultiFactorAuthenticationToken.AuthenticationFactorType.class);
        if (authentication == null) {
            return factors;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                MultiFactorAuthenticationToken.AuthenticationFactorType factor = fromAuthority(
                        authority == null ? null : authority.getAuthority()
                );
                if (factor != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                    factors.add(factor);
                }
            }
        }

        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            factors.addAll(mfaToken.getCompletedFactors());
        }

        return factors;
    }

    public static List<String> toAmr(Authentication authentication) {
        List<String> amr = new ArrayList<>();
        for (MultiFactorAuthenticationToken.AuthenticationFactorType factor : extractFactors(authentication)) {
            String value = toAmrValue(factor);
            if (value != null && !amr.contains(value)) {
                amr.add(value);
            }
        }
        return amr;
    }

    public static String toAuthority(MultiFactorAuthenticationToken.AuthenticationFactorType factor) {
        if (factor == null || factor == MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
            return null;
        }
        return FACTOR_AUTHORITY_PREFIX + factor.name();
    }

    public static MultiFactorAuthenticationToken.AuthenticationFactorType fromAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN;
        }
        String normalized = authority.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith(FACTOR_AUTHORITY_PREFIX)) {
            return MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN;
        }
        return MultiFactorAuthenticationToken.AuthenticationFactorType.from(
                normalized.substring(FACTOR_AUTHORITY_PREFIX.length())
        );
    }

    public static String toAmrValue(MultiFactorAuthenticationToken.AuthenticationFactorType factor) {
        if (factor == null) {
            return null;
        }
        return switch (factor) {
            case PASSWORD -> "password";
            case TOTP -> "totp";
            case OAUTH2 -> "oauth2";
            case EMAIL -> "email";
            case MFA -> "mfa";
            default -> null;
        };
    }

    private static GrantedAuthority normalizeAuthority(GrantedAuthority authority, Instant synthesizedFactorIssuedAt) {
        if (authority == null) {
            return null;
        }
        String authorityValue = authority.getAuthority();
        if (authorityValue == null || authorityValue.isBlank()) {
            return null;
        }
        if (authority instanceof FactorGrantedAuthority factorGrantedAuthority) {
            return factorGrantedAuthority;
        }
        if (fromAuthority(authorityValue) != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
            return FactorGrantedAuthority.withAuthority(authorityValue)
                    .issuedAt(synthesizedFactorIssuedAt)
                    .build();
        }
        return authority;
    }

    private static GrantedAuthority preferAuthority(GrantedAuthority existing, GrantedAuthority candidate) {
        if (existing instanceof FactorGrantedAuthority existingFactor) {
            if (candidate instanceof FactorGrantedAuthority candidateFactor
                    && candidateFactor.getIssuedAt().isAfter(existingFactor.getIssuedAt())) {
                return candidateFactor;
            }
            return existing;
        }
        if (candidate instanceof FactorGrantedAuthority) {
            return candidate;
        }
        return existing != null ? existing : candidate;
    }
}
