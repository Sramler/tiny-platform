package com.tiny.platform.infrastructure.scheduling.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingErrorSanitizerTest {

    @Test
    void sanitizeForPersistenceReturnsFallbackWhenNull() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence(null))
                .isEqualTo("任务执行失败");
    }

    @Test
    void sanitizeForPersistenceReturnsFallbackWhenEmpty() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence(""))
                .isEqualTo("任务执行失败");
    }

    @Test
    void sanitizeForPersistenceKeepsTimeoutMessage() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence("TIMEOUT: 任务执行超时（超过 60 秒）"))
                .isEqualTo("TIMEOUT: 任务执行超时（超过 60 秒）");
    }

    @Test
    void sanitizeForPersistenceKeepsCancelledMessage() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence("CANCELLED: 任务已取消"))
                .isEqualTo("CANCELLED: 任务已取消");
    }

    @Test
    void sanitizeForPersistenceReturnsFallbackWhenStackLike() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence("java.lang.NullPointerException at com.foo.Bar.baz(Bar.java:10)"))
                .isEqualTo("任务执行失败");
    }

    @Test
    void sanitizeForPersistenceReturnsFallbackWhenContainsExceptionAndPackage() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence("Connection failed: com.mysql.cj.jdbc.ConnectionException"))
                .isEqualTo("任务执行失败");
    }

    @Test
    void sanitizeForPersistenceKeepsShortSafeMessage() {
        assertThat(SchedulingErrorSanitizer.sanitizeForPersistence("参数不能为空"))
                .isEqualTo("参数不能为空");
    }
}
