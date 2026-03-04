package com.tiny.platform.core.oauth.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLogSanitizerTest {

    @Test
    void shouldMaskSensitiveHeaderValuesAndPreserveAuthorizationScheme() {
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("Authorization", "Bearer abc.def.ghi"))
                .isEqualTo("Bearer ***");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("Cookie", "SESSION=secret"))
                .isEqualTo("***");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("User-Agent", "Mozilla/5.0"))
                .isEqualTo("Mozilla/5.0");
    }

    @Test
    void shouldMaskSensitiveQueryStringKeysAndKeepOthers() {
        assertThat(HttpLogSanitizer.sanitizeQueryString(null)).isNull();
        assertThat(HttpLogSanitizer.sanitizeQueryString("")).isEqualTo("");
        assertThat(HttpLogSanitizer.sanitizeQueryString("client_id=web&password=secret&x=1"))
                .isEqualTo("client_id=web&password=***&x=1");
        assertThat(HttpLogSanitizer.sanitizeQueryString("nested.code=abc&profile[name]=tom"))
                .isEqualTo("nested.code=***&profile[name]=tom");
        assertThat(HttpLogSanitizer.sanitizeQueryString("=")).isEqualTo("=");
        assertThat(HttpLogSanitizer.sanitizeQueryString("\"password\"=secret"))
                .isEqualTo("\"password\"=***");
        assertThat(HttpLogSanitizer.sanitizeQueryString("otp"))
                .isEqualTo("otp");
        assertThat(HttpLogSanitizer.sanitizeQueryString("a=1&&b=2"))
                .isEqualTo("a=1&&b=2");
    }

    @Test
    void shouldMaskJsonAndFormBodiesAndKeepUnknownFormats() {
        assertThat(HttpLogSanitizer.sanitizeBody(null, "application/json")).isNull();
        assertThat(HttpLogSanitizer.sanitizeBody("base64:AAAA", "multipart/form-data"))
                .isEqualTo("base64:AAAA");
        assertThat(HttpLogSanitizer.sanitizeBody("code=abc&client_id=web", "application/x-www-form-urlencoded"))
                .isEqualTo("code=***&client_id=web");
        assertThat(HttpLogSanitizer.sanitizeBody("{\"password\":\"p1\",\"otp\":123,\"ok\":true}", "application/json"))
                .isEqualTo("{\"password\":\"***\",\"otp\":\"***\",\"ok\":true}");
        assertThat(HttpLogSanitizer.sanitizeBody("{\"refresh_token\":\"r1\"}", "text/plain"))
                .isEqualTo("{\"refresh_token\":\"***\"}");
        assertThat(HttpLogSanitizer.sanitizeBody("not-json", "text/plain"))
                .isEqualTo("not-json");
    }

    @Test
    void shouldDetectAdditionalSensitiveHeaderNamePatterns() {
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("X-Custom-Secret", "abc")).isEqualTo("***");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("X-Session-Token", "abc")).isEqualTo("***");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("X-Api-Key", "abc")).isEqualTo("***");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue(null, "abc")).isEqualTo("abc");
        assertThat(HttpLogSanitizer.sanitizeHeaderValue("Authorization", null)).isNull();
    }
}
