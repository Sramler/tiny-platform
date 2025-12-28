package com.tiny.platform.infrastructure.export.config;

import com.tiny.platform.infrastructure.export.core.DefaultTopInfoDecorator;
import com.tiny.platform.infrastructure.export.core.TopInfoDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Export 模块的通用配置，提供默认实现，便于业务按需覆盖。
 */
@Configuration
public class ExportSupportConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(TopInfoDecorator.class)
    public TopInfoDecorator topInfoDecorator() {
        return new DefaultTopInfoDecorator();
    }
}

