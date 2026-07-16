package com.tiny.platform.core.oauth.session;

import java.util.Arrays;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

final class SessionStorePolicyValidator implements InitializingBean {

    private final TinySessionProperties properties;
    private final Environment environment;

    SessionStorePolicyValidator(TinySessionProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (production && properties.storeType() == TinySessionProperties.StoreType.MEMORY) {
            throw new IllegalStateException(
                    "prod profile 禁止使用 tiny.session.store-type=memory；请选择 jdbc 或 redis");
        }
    }
}
