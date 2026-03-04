package com.tiny.platform.core.oauth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestLoggingPropertiesTest {

    @Test
    void shouldSupportGettersSettersAndFallbackStrategyResolution() {
        HttpRequestLoggingProperties p = new HttpRequestLoggingProperties();

        p.setEnabled(false);
        p.setIncludeRequestBody(true);
        p.setIncludeResponseBody(true);
        p.setMaxBodyLength(2048);
        p.setExcludedPathPrefixes(List.of("/a", "/b"));
        p.setTraceIdQueryParamAllowedPathFragments(List.of("/oauth2/", "/cb"));
        p.setTraceIdFallbackStrategy(" generated ");

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isIncludeRequestBody()).isTrue();
        assertThat(p.isIncludeResponseBody()).isTrue();
        assertThat(p.getMaxBodyLength()).isEqualTo(2048);
        assertThat(p.getExcludedPathPrefixes()).containsExactly("/a", "/b");
        assertThat(p.getTraceIdQueryParamAllowedPathFragments()).containsExactly("/oauth2/", "/cb");
        assertThat(p.getTraceIdFallbackStrategy()).isEqualTo(" generated ");
        assertThat(p.resolveTraceIdFallbackStrategy())
                .isEqualTo(HttpRequestLoggingProperties.TraceIdFallbackStrategy.GENERATED);
    }

    @Test
    void shouldFallbackToRequestIdStrategyWhenBlankOrInvalid() {
        HttpRequestLoggingProperties p = new HttpRequestLoggingProperties();

        p.setTraceIdFallbackStrategy(null);
        assertThat(p.resolveTraceIdFallbackStrategy())
                .isEqualTo(HttpRequestLoggingProperties.TraceIdFallbackStrategy.REQUEST_ID);

        p.setTraceIdFallbackStrategy("   ");
        assertThat(p.resolveTraceIdFallbackStrategy())
                .isEqualTo(HttpRequestLoggingProperties.TraceIdFallbackStrategy.REQUEST_ID);

        p.setTraceIdFallbackStrategy("request-id");
        assertThat(p.resolveTraceIdFallbackStrategy())
                .isEqualTo(HttpRequestLoggingProperties.TraceIdFallbackStrategy.REQUEST_ID);

        p.setTraceIdFallbackStrategy("bad_value");
        assertThat(p.resolveTraceIdFallbackStrategy())
                .isEqualTo(HttpRequestLoggingProperties.TraceIdFallbackStrategy.REQUEST_ID);
    }
}
