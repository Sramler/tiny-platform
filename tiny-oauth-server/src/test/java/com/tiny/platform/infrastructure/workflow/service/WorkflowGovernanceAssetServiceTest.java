package com.tiny.platform.infrastructure.workflow.service;

import tools.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionStore;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowGovernanceAssetEntity;
import com.tiny.platform.infrastructure.workflow.repository.WorkflowGovernanceAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowGovernanceAssetServiceTest {

    @Mock
    private WorkflowGovernanceAssetRepository repository;

    @Mock
    private RuntimeVersionStore runtimeVersionStore;

    @Test
    void publishConnector_createsNextAssetVersionAndBumpsRuntimeVersion() {
        WorkflowGovernanceAssetService service = new WorkflowGovernanceAssetService(
            repository,
            runtimeVersionStore,
            new ObjectMapper()
        );
        when(repository.findMaxVersionInScope(
            ProcessModelScopeType.PLATFORM,
            null,
            "CONNECTOR",
            "platformDemoConnector"
        )).thenReturn(2);
        AtomicLong ids = new AtomicLong(700L);
        when(repository.save(any(WorkflowGovernanceAssetEntity.class))).thenAnswer(invocation -> {
            WorkflowGovernanceAssetEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(ids.incrementAndGet());
            }
            return entity;
        });
        ArgumentCaptor<WorkflowGovernanceAssetEntity> entityCaptor =
            ArgumentCaptor.forClass(WorkflowGovernanceAssetEntity.class);

        Map<String, Object> result = service.publishConnector(
            Map.of(
                "connectorKey",
                "platformDemoConnector",
                "connectorName",
                "演示连接器",
                "connectorAction",
                "PUBLISH",
                "ownerTeam",
                "platform-integration",
                "securityReviewNo",
                "SEC-20260522-001"
            ),
            null,
            ProcessModelScopeType.PLATFORM,
            null
        );

        verify(repository, times(2)).save(entityCaptor.capture());
        WorkflowGovernanceAssetEntity saved = entityCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(701L);
        assertThat(saved.getScopeType()).isEqualTo(ProcessModelScopeType.PLATFORM);
        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getAssetType()).isEqualTo("CONNECTOR");
        assertThat(saved.getAssetKey()).isEqualTo("platformDemoConnector");
        assertThat(saved.getVersion()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getPayloadJson()).contains("SEC-20260522-001");
        assertThat(saved.getResultJson()).contains("platformDemoConnector", "\"version\":3");
        verify(runtimeVersionStore).bump(any(), eq("connector:platformDemoConnector"), isNull());
        assertThat(result)
            .containsEntry("governanceAssetId", 701L)
            .containsEntry("assetType", "CONNECTOR")
            .containsEntry("assetKey", "platformDemoConnector")
            .containsEntry("version", 3)
            .containsEntry("status", "PUBLISHED");
    }
}
