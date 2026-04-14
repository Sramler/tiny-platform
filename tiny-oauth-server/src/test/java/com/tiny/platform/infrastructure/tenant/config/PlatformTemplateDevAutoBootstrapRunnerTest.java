package com.tiny.platform.infrastructure.tenant.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.tenant.service.TenantBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PlatformTemplateDevAutoBootstrapRunnerTest {

    @Test
    void run_propagatesRuntimeExceptionFromEnsurePlatformTemplatesInitialized() {
        TenantBootstrapService tenantBootstrapService = mock(TenantBootstrapService.class);
        IllegalStateException cause = new IllegalStateException("platform tenant code missing");
        when(tenantBootstrapService.ensurePlatformTemplatesInitialized()).thenThrow(cause);

        PlatformTemplateDevAutoBootstrapRunner runner = new PlatformTemplateDevAutoBootstrapRunner(tenantBootstrapService);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isSameAs(cause);

        verify(tenantBootstrapService).ensurePlatformTemplatesInitialized();
    }
}
