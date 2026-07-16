package com.tiny.platform.core.oauth.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TinySessionProperties.class)
public class TinySessionStoreConfiguration {

    @Bean
    SessionStorePolicyValidator sessionStorePolicyValidator(
            TinySessionProperties properties,
            Environment environment) {
        return new SessionStorePolicyValidator(properties, environment);
    }

    @Bean(name = "springSessionDefaultCookieSerializer")
    @ConditionalOnProperty(prefix = "tiny.session", name = "store-type", havingValue = "jdbc")
    DefaultCookieSerializer jdbcCookieSerializer(TinySessionProperties properties) {
        return cookieSerializer(properties);
    }

    @Bean(name = "springSessionDefaultCookieSerializer")
    @ConditionalOnProperty(prefix = "tiny.session", name = "store-type", havingValue = "redis")
    DefaultCookieSerializer redisCookieSerializer(TinySessionProperties properties) {
        return cookieSerializer(properties);
    }

    private static DefaultCookieSerializer cookieSerializer(TinySessionProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(properties.cookieSecure());
        serializer.setSameSite(properties.sameSite());
        return serializer;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tiny.session", name = "store-type", havingValue = "jdbc")
    @EnableJdbcHttpSession(tableName = "${tiny.session.table-name:SPRING_SESSION}")
    static class JdbcStore {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tiny.session", name = "store-type", havingValue = "redis")
    @Import(DataRedisAutoConfiguration.class)
    @EnableRedisHttpSession(redisNamespace = "${tiny.session.namespace:tiny-platform:session}")
    static class RedisStore {
    }
}
