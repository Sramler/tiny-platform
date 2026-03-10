package com.tiny.platform.infrastructure.idempotent.console;

import com.tiny.platform.infrastructure.idempotent.console.repository.IdempotentConsoleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 幂等治理控制台配置
 *
 * <p>仅在数据库存储模式下启用，依赖 sys_idempotent_token、sys_idempotent_rule、sys_idempotent_blacklist 表。</p>
 *
 * @author tiny-platform
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "tiny.idempotent", name = "store", havingValue = "database", matchIfMissing = true)
public class IdempotentConsoleConfiguration {

    @Bean
    public IdempotentConsoleRepository idempotentConsoleRepository(JdbcTemplate jdbcTemplate) {
        return new IdempotentConsoleRepository(jdbcTemplate);
    }

    @Bean
    public IdempotentBlacklistChecker idempotentBlacklistChecker(IdempotentConsoleRepository consoleRepository) {
        return new IdempotentBlacklistChecker(consoleRepository);
    }
}
