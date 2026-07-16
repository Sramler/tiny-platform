package com.tiny.platform.core.oauth.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SessionStorePolicyValidatorTest {

    @Test
    void shouldAllowMemoryForDevelopment() {
        TinySessionProperties properties = properties(TinySessionProperties.StoreType.MEMORY);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        environment.setActiveProfiles("dev");

        assertThatCode(() -> new SessionStorePolicyValidator(properties, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMemoryForProduction() {
        TinySessionProperties properties = properties(TinySessionProperties.StoreType.MEMORY);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SessionStorePolicyValidator(properties, environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdbc 或 redis");
    }

    @Test
    void shouldAllowSharedStoresForProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new SessionStorePolicyValidator(
                properties(TinySessionProperties.StoreType.JDBC), environment).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> new SessionStorePolicyValidator(
                properties(TinySessionProperties.StoreType.REDIS), environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static TinySessionProperties properties(TinySessionProperties.StoreType storeType) {
        return new TinySessionProperties(storeType, null, null, false, null);
    }
}
