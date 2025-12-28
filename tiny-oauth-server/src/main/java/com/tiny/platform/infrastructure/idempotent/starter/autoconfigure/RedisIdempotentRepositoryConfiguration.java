package com.tiny.platform.infrastructure.idempotent.starter.autoconfigure;

import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis 幂等性存储配置
 * 
 * <p>当 classpath 中存在 StringRedisTemplate 且配置为 redis 存储时生效</p>
 * <p>使用字符串形式的类名避免直接 import，防止在没有 Redis 依赖时类加载失败</p>
 * 
 * <p><strong>注意：</strong>只有在明确配置 {@code tiny.idempotent.store=redis} 时，
 * 才会导入 Redis 自动配置并启用 Redis 存储。默认使用 database 存储，无需 Redis。</p>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "tiny.idempotent", name = "store", havingValue = "redis")
@Import(RedisAutoConfiguration.class) // 只有在需要 Redis 时才导入自动配置
public class RedisIdempotentRepositoryConfiguration {
    
    /**
     * Redis 实现的幂等性存储
     * 
     * <p>注意：由于使用了字符串形式的 @ConditionalOnClass，当 Redis 类不存在时，
     * 此配置类不会被加载，因此方法参数可以使用正常的类型（通过反射获取）</p>
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentRepository.class)
    @SuppressWarnings("unchecked")
    public IdempotentRepository redisIdempotentRepository(Object redisTemplate) throws Exception {
        // 使用反射方式创建 RedisIdempotentRepository，避免直接 import
        Class<?> repositoryClass = Class.forName("com.tiny.platform.infrastructure.idempotent.repository.redis.RedisIdempotentRepository");
        return (IdempotentRepository) repositoryClass
            .getConstructor(redisTemplate.getClass())
            .newInstance(redisTemplate);
    }
}

