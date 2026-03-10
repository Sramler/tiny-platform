package com.tiny.platform.infrastructure.scheduling.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzCustomizationConfigTest {

    @Test
    void schedulerContextCustomizerShouldSetApplicationContextKey() {
        QuartzCustomizationConfig config = new QuartzCustomizationConfig();
        SchedulerFactoryBean factoryBean = new SchedulerFactoryBean();

        config.schedulerContextCustomizer().customize(factoryBean);

        assertThat(ReflectionTestUtils.getField(factoryBean, "applicationContextSchedulerContextKey"))
                .isEqualTo("applicationContext");
    }
}
