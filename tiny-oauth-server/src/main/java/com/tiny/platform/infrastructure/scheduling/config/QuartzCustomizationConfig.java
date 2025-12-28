package com.tiny.platform.infrastructure.scheduling.config;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 Quartz 与 Spring Boot Starter 集成的补充配置。
 */
@Configuration
public class QuartzCustomizationConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerContextCustomizer() {
        return factoryBean -> factoryBean.setApplicationContextSchedulerContextKey("applicationContext");
    }
}

