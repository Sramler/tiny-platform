package com.tiny.platform.infrastructure.auth.user.support;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthenticationMethodMergeTest {

    @Test
    void tenantScopedOverridesGlobalForSameProviderAndType() {
        UserAuthenticationMethod global = row(1L, null, "LOCAL", "PASSWORD", 10);
        UserAuthenticationMethod tenant = row(2L, 5L, "LOCAL", "PASSWORD", 5);
        List<UserAuthenticationMethod> merged = UserAuthenticationMethodMerge.mergePreferTenantScoped(
                List.of(tenant), List.of(global));
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getId()).isEqualTo(2L);
        assertThat(merged.get(0).getTenantId()).isEqualTo(5L);
    }

    @Test
    void keepsDistinctProviderTypeKeysFromGlobalWhenTenantListEmpty() {
        UserAuthenticationMethod globalPwd = row(1L, null, "LOCAL", "PASSWORD", 10);
        UserAuthenticationMethod globalTotp = row(2L, null, "LOCAL", "TOTP", 11);
        List<UserAuthenticationMethod> merged = UserAuthenticationMethodMerge.mergePreferTenantScoped(
                List.of(), List.of(globalPwd, globalTotp));
        assertThat(merged).hasSize(2);
    }

    @Test
    void sortsMergedMethodsByAuthenticationPriorityAscending() {
        UserAuthenticationMethod password = row(1L, 1L, "LOCAL", "PASSWORD", 20);
        UserAuthenticationMethod totp = row(2L, 1L, "LOCAL", "TOTP", 10);
        List<UserAuthenticationMethod> merged = UserAuthenticationMethodMerge.mergePreferTenantScoped(
                List.of(password, totp), List.of());
        assertThat(merged).extracting(UserAuthenticationMethod::getAuthenticationType).containsExactly("TOTP", "PASSWORD");
    }

    private static UserAuthenticationMethod row(Long id, Long tenantId, String provider, String type, int priority) {
        UserAuthenticationMethod m = new UserAuthenticationMethod();
        m.setId(id);
        m.setUserId(99L);
        m.setTenantId(tenantId);
        m.setAuthenticationProvider(provider);
        m.setAuthenticationType(type);
        m.setAuthenticationPriority(priority);
        return m;
    }
}
