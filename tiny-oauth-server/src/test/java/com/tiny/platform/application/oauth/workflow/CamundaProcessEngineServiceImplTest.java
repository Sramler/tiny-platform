package com.tiny.platform.application.oauth.workflow;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamundaProcessEngineServiceImplTest {

    private RuntimeService runtimeService;
    private ProcessInstantiationBuilder builder;
    private CamundaProcessEngineServiceImpl service;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        builder = mock(ProcessInstantiationBuilder.class);
        service = new CamundaProcessEngineServiceImpl();
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
    }

    @Test
    void startProcessInstance_whenTenantProvided_usesTenantScopedDefinition() {
        ProcessInstance instance = mock(ProcessInstance.class);
        Map<String, Object> variables = Map.of("approval", true);

        when(runtimeService.createProcessInstanceByKey("process-key")).thenReturn(builder);
        when(builder.setVariables(variables)).thenReturn(builder);
        when(builder.processDefinitionTenantId("tenant-a")).thenReturn(builder);
        when(builder.execute()).thenReturn(instance);
        when(instance.getId()).thenReturn("instance-1");

        String instanceId = service.startProcessInstance("process-key", "tenant-a", variables);

        assertThat(instanceId).isEqualTo("instance-1");
        verify(runtimeService).createProcessInstanceByKey("process-key");
        verify(builder).setVariables(variables);
        verify(builder).processDefinitionTenantId("tenant-a");
        verify(builder, never()).processDefinitionWithoutTenantId();
        verify(builder).execute();
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    void startProcessInstance_whenTenantBlank_usesGlobalDefinitionAndEmptyVariables() {
        ProcessInstance instance = mock(ProcessInstance.class);

        when(runtimeService.createProcessInstanceByKey("process-key")).thenReturn(builder);
        when(builder.setVariables(Map.of())).thenReturn(builder);
        when(builder.processDefinitionWithoutTenantId()).thenReturn(builder);
        when(builder.execute()).thenReturn(instance);
        when(instance.getId()).thenReturn("instance-2");

        String instanceId = service.startProcessInstance("process-key", "   ", null);

        assertThat(instanceId).isEqualTo("instance-2");
        verify(runtimeService).createProcessInstanceByKey("process-key");
        verify(builder).setVariables(Map.of());
        verify(builder).processDefinitionWithoutTenantId();
        verify(builder, never()).processDefinitionTenantId(anyString());
        verify(builder).execute();
    }
}
