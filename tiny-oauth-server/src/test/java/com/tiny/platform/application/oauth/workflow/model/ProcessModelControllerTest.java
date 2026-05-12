package com.tiny.platform.application.oauth.workflow.model;

import com.tiny.platform.infrastructure.workflow.service.ProcessModelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessModelControllerTest {

    @Test
    void list_returnsExplicitDtoList() {
        ProcessModelService service = mock(ProcessModelService.class);
        ProcessModelDto dto = new ProcessModelDto(
            1L,
            "leave_process",
            "Leave Process",
            null,
            "TENANT",
            9L,
            "DRAFT",
            "NOT_DEPLOYED",
            1,
            "<bpmn/>",
            null,
            "NOT_VALIDATED",
            null,
            null,
            null,
            null,
            null,
            "alice",
            null,
            "alice",
            null,
            null,
            null,
            0L
        );
        when(service.listCurrentScope()).thenReturn(List.of(dto));
        ProcessModelController controller = new ProcessModelController(service);

        ResponseEntity<List<ProcessModelDto>> response = controller.list();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(dto);
    }

    @Test
    void listGroups_returnsProcessAssetGroups() {
        ProcessModelService service = mock(ProcessModelService.class);
        ProcessModelDto latest = new ProcessModelDto(
            2L,
            "leave_process",
            "Leave Process",
            null,
            "TENANT",
            9L,
            "DRAFT",
            "NOT_DEPLOYED",
            2,
            "<bpmn/>",
            null,
            "NOT_VALIDATED",
            null,
            null,
            null,
            null,
            null,
            "alice",
            null,
            "alice",
            null,
            null,
            null,
            0L
        );
        ProcessModelGroupDto group = ProcessModelGroupDto.from(List.of(latest));
        when(service.listGroupsCurrentScope()).thenReturn(List.of(group));
        ProcessModelController controller = new ProcessModelController(service);

        ResponseEntity<List<ProcessModelGroupDto>> response = controller.listGroups();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(group);
    }

    @Test
    void create_passesPrincipalAsActor() {
        ProcessModelService service = mock(ProcessModelService.class);
        ProcessModelRequests.Create request = new ProcessModelRequests.Create("k", "n", null, null, "<bpmn/>", null);
        ProcessModelDto dto = new ProcessModelDto(
            1L,
            "k",
            "n",
            null,
            "PLATFORM",
            null,
            "DRAFT",
            "NOT_DEPLOYED",
            1,
            "<bpmn/>",
            null,
            "NOT_VALIDATED",
            null,
            null,
            null,
            null,
            null,
            "alice",
            null,
            "alice",
            null,
            null,
            null,
            0L
        );
        Principal principal = () -> "alice";
        when(service.create(request, "alice")).thenReturn(dto);
        ProcessModelController controller = new ProcessModelController(service);

        ResponseEntity<ProcessModelDto> response = controller.create(request, principal);

        assertThat(response.getBody()).isEqualTo(dto);
        verify(service).create(request, "alice");
    }
}
