package com.tiny.platform.infrastructure.idempotent.starter.autoconfigure;

import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.repository.IdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.repository.database.DatabaseIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.repository.memory.MemoryIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.sdk.aspect.IdempotentAspect;
import com.tiny.platform.infrastructure.idempotent.sdk.facade.IdempotentFacade;
import com.tiny.platform.infrastructure.idempotent.sdk.resolver.IdempotentKeyResolver;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 幂等性自动配置类
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnProperty(prefix = "tiny.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAutoConfiguration {
    
    /**
     * 幂等性引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentEngine idempotentEngine(IdempotentRepository repository) {
        return new IdempotentEngine(repository);
    }
    
    /**
     * 数据库实现的幂等性存储（默认）
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentRepository.class)
    @ConditionalOnProperty(prefix = "tiny.idempotent", name = "store", havingValue = "database", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    public IdempotentRepository databaseIdempotentRepository(JdbcTemplate jdbcTemplate) {
        return new DatabaseIdempotentRepository(jdbcTemplate);
    }
    
    /**
     * 内存实现的幂等性存储（轻量模式）
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentRepository.class)
    @ConditionalOnProperty(prefix = "tiny.idempotent", name = "store", havingValue = "memory")
    public IdempotentRepository memoryIdempotentRepository() {
        return new MemoryIdempotentRepository();
    }
    
    /**
     * 幂等性切面
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotentEngine engine, List<IdempotentKeyResolver> keyResolvers) {
        return new IdempotentAspect(engine, keyResolvers);
    }
    
    /**
     * 幂等性 Facade（统一入口）
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentFacade idempotentFacade(IdempotentEngine engine) {
        return new IdempotentFacade(engine);
    }
}

