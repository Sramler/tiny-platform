package com.tiny.platform.infrastructure.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagVersionCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingException;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingAudit;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingDtosAndModelsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void taskCreateUpdateDtoShouldExposeDefaultsAndValidationConstraints() {
        SchedulingTaskCreateUpdateDto dto = new SchedulingTaskCreateUpdateDto();
        dto.setCode("x".repeat(129));
        dto.setName(" ");
        dto.setTypeId(null);

        assertThat(dto.getMaxRetry()).isEqualTo(0);
        assertThat(dto.getConcurrencyPolicy()).isEqualTo("PARALLEL");
        assertThat(dto.getEnabled()).isTrue();
        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("typeId", "code", "name");

        dto.setId(1L);
        dto.setTypeId(3L);
        dto.setCode("task-code");
        dto.setName("task-name");
        dto.setDescription("desc");
        dto.setParams("{\"a\":1}");
        dto.setTimeoutSec(30);
        dto.setMaxRetry(2);
        dto.setRetryPolicy("{\"delaySec\":60}");
        dto.setConcurrencyPolicy("SINGLETON");
        dto.setEnabled(false);
        dto.setCreatedBy("alice");

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTypeId()).isEqualTo(3L);
        assertThat(dto.getCode()).isEqualTo("task-code");
        assertThat(dto.getName()).isEqualTo("task-name");
        assertThat(dto.getDescription()).isEqualTo("desc");
        assertThat(dto.getParams()).isEqualTo("{\"a\":1}");
        assertThat(dto.getTimeoutSec()).isEqualTo(30);
        assertThat(dto.getMaxRetry()).isEqualTo(2);
        assertThat(dto.getRetryPolicy()).isEqualTo("{\"delaySec\":60}");
        assertThat(dto.getConcurrencyPolicy()).isEqualTo("SINGLETON");
        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void dagAndTaskTypeDtosShouldExposeDefaultsAndValidationConstraints() {
        SchedulingDagCreateUpdateDto dagDto = new SchedulingDagCreateUpdateDto();
        dagDto.setCode("y".repeat(129));
        dagDto.setName(" ");

        assertThat(dagDto.getEnabled()).isTrue();
        assertThat(dagDto.getCronEnabled()).isTrue();
        assertThat(validator.validate(dagDto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("code", "name");

        dagDto.setId(10L);
        dagDto.setCode("dag-code");
        dagDto.setName("dag-name");
        dagDto.setDescription("dag-desc");
        dagDto.setEnabled(false);
        dagDto.setCronExpression("0 0 2 * * ?");
        dagDto.setCronTimezone("UTC");
        dagDto.setCronEnabled(false);
        dagDto.setCreatedBy("bob");

        SchedulingTaskTypeCreateUpdateDto taskTypeDto = new SchedulingTaskTypeCreateUpdateDto();
        taskTypeDto.setCode(" ");
        taskTypeDto.setName(" ");

        assertThat(taskTypeDto.getDefaultTimeoutSec()).isEqualTo(0);
        assertThat(taskTypeDto.getDefaultMaxRetry()).isEqualTo(0);
        assertThat(taskTypeDto.getEnabled()).isTrue();
        assertThat(validator.validate(taskTypeDto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("code", "name");

        taskTypeDto.setId(11L);
        taskTypeDto.setCode("type-code");
        taskTypeDto.setName("type-name");
        taskTypeDto.setDescription("type-desc");
        taskTypeDto.setExecutor("loggingExecutor");
        taskTypeDto.setParamSchema("{\"type\":\"object\"}");
        taskTypeDto.setDefaultTimeoutSec(15);
        taskTypeDto.setDefaultMaxRetry(4);
        taskTypeDto.setEnabled(false);
        taskTypeDto.setCreatedBy("carol");

        assertThat(dagDto.getId()).isEqualTo(10L);
        assertThat(dagDto.getCode()).isEqualTo("dag-code");
        assertThat(dagDto.getName()).isEqualTo("dag-name");
        assertThat(dagDto.getDescription()).isEqualTo("dag-desc");
        assertThat(dagDto.getEnabled()).isFalse();
        assertThat(dagDto.getCronExpression()).isEqualTo("0 0 2 * * ?");
        assertThat(dagDto.getCronTimezone()).isEqualTo("UTC");
        assertThat(dagDto.getCronEnabled()).isFalse();
        assertThat(dagDto.getCreatedBy()).isEqualTo("bob");

        assertThat(taskTypeDto.getId()).isEqualTo(11L);
        assertThat(taskTypeDto.getCode()).isEqualTo("type-code");
        assertThat(taskTypeDto.getName()).isEqualTo("type-name");
        assertThat(taskTypeDto.getDescription()).isEqualTo("type-desc");
        assertThat(taskTypeDto.getExecutor()).isEqualTo("loggingExecutor");
        assertThat(taskTypeDto.getParamSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(taskTypeDto.getDefaultTimeoutSec()).isEqualTo(15);
        assertThat(taskTypeDto.getDefaultMaxRetry()).isEqualTo(4);
        assertThat(taskTypeDto.getEnabled()).isFalse();
        assertThat(taskTypeDto.getCreatedBy()).isEqualTo("carol");
    }

    @Test
    void dagTaskAndVersionDtosShouldExposeFieldsAndValidationConstraints() {
        SchedulingDagTaskCreateUpdateDto dagTaskDto = new SchedulingDagTaskCreateUpdateDto();
        dagTaskDto.setNodeCode(" ");
        dagTaskDto.setTaskId(null);

        assertThat(validator.validate(dagTaskDto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nodeCode", "taskId");

        dagTaskDto.setId(30L);
        dagTaskDto.setDagVersionId(31L);
        dagTaskDto.setNodeCode("merge-report");
        dagTaskDto.setTaskId(32L);
        dagTaskDto.setName("Merge Report");
        dagTaskDto.setOverrideParams("{\"a\":1}");
        dagTaskDto.setTimeoutSec(40);
        dagTaskDto.setMaxRetry(5);
        dagTaskDto.setParallelGroup("g1");
        dagTaskDto.setMeta("{\"meta\":true}");

        SchedulingDagVersionCreateUpdateDto versionDto = new SchedulingDagVersionCreateUpdateDto();
        assertThat(versionDto.getStatus()).isEqualTo("DRAFT");
        versionDto.setId(41L);
        versionDto.setDagId(42L);
        versionDto.setVersionNo(2);
        versionDto.setStatus("ACTIVE");
        versionDto.setDefinition("{\"nodes\":[]}");
        versionDto.setCreatedBy("dave");

        assertThat(dagTaskDto.getId()).isEqualTo(30L);
        assertThat(dagTaskDto.getDagVersionId()).isEqualTo(31L);
        assertThat(dagTaskDto.getNodeCode()).isEqualTo("merge-report");
        assertThat(dagTaskDto.getTaskId()).isEqualTo(32L);
        assertThat(dagTaskDto.getName()).isEqualTo("Merge Report");
        assertThat(dagTaskDto.getOverrideParams()).isEqualTo("{\"a\":1}");
        assertThat(dagTaskDto.getTimeoutSec()).isEqualTo(40);
        assertThat(dagTaskDto.getMaxRetry()).isEqualTo(5);
        assertThat(dagTaskDto.getParallelGroup()).isEqualTo("g1");
        assertThat(dagTaskDto.getMeta()).isEqualTo("{\"meta\":true}");

        assertThat(versionDto.getId()).isEqualTo(41L);
        assertThat(versionDto.getDagId()).isEqualTo(42L);
        assertThat(versionDto.getVersionNo()).isEqualTo(2);
        assertThat(versionDto.getStatus()).isEqualTo("ACTIVE");
        assertThat(versionDto.getDefinition()).isEqualTo("{\"nodes\":[]}");
        assertThat(versionDto.getCreatedBy()).isEqualTo("dave");
    }

    @Test
    void schedulingModelsShouldSerializeRecordTenantIdInsteadOfTenantId() throws Exception {
        SchedulingAudit audit = new SchedulingAudit();
        audit.setId(1L);
        audit.setTenantId(88L);
        audit.setAction("TRIGGER");

        String json = objectMapper.writeValueAsString(audit);

        assertThat(json).contains("\"recordTenantId\":88");
        assertThat(json).doesNotContain("\"tenantId\"");
    }

    @Test
    void schedulingExceptionsFactoryShouldUseExpectedErrorCodes() {
        RuntimeException cause = new RuntimeException("boom");

        SchedulingException notFound = SchedulingExceptions.notFound("dag %s missing", 10);
        SchedulingException validation = SchedulingExceptions.validation("bad %s", "input");
        SchedulingException conflict = SchedulingExceptions.conflict("dup %s", "code");
        SchedulingException forbidden = SchedulingExceptions.operationNotAllowed("deny %s", "op");
        SchedulingException system = SchedulingExceptions.systemError("sys %s", cause, "err");
        SchedulingException direct = new SchedulingException(ErrorCode.INTERNAL_ERROR, "direct", cause);

        assertThat(notFound.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(notFound).hasMessage("dag 10 missing");
        assertThat(validation.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(validation).hasMessage("bad input");
        assertThat(conflict.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(conflict).hasMessage("dup code");
        assertThat(forbidden.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(forbidden).hasMessage("deny op");
        assertThat(system.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(system).hasMessage("sys err");
        assertThat(system.getCause()).isSameAs(cause);
        assertThat(direct.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(direct).hasMessage("direct");
        assertThat(direct.getCause()).isSameAs(cause);
    }

    @Test
    void dagModelsShouldExposeFieldsAndInitializeCreateTime() {
        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setId(50L);
        edge.setDagVersionId(51L);
        edge.setFromNodeCode("extract");
        edge.setToNodeCode("merge");
        edge.setCondition("{\"all\":true}");
        ReflectionTestUtils.invokeMethod(edge, "onCreate");

        SchedulingDagTask dagTask = new SchedulingDagTask();
        dagTask.setId(60L);
        dagTask.setDagVersionId(61L);
        dagTask.setNodeCode("merge");
        dagTask.setTaskId(62L);
        dagTask.setName("Merge");
        dagTask.setOverrideParams("{\"b\":2}");
        dagTask.setTimeoutSec(120);
        dagTask.setMaxRetry(3);
        dagTask.setParallelGroup("group-a");
        dagTask.setMeta("{\"debug\":true}");
        ReflectionTestUtils.invokeMethod(dagTask, "onCreate");

        assertThat(edge.getId()).isEqualTo(50L);
        assertThat(edge.getDagVersionId()).isEqualTo(51L);
        assertThat(edge.getFromNodeCode()).isEqualTo("extract");
        assertThat(edge.getToNodeCode()).isEqualTo("merge");
        assertThat(edge.getCondition()).isEqualTo("{\"all\":true}");
        assertThat(edge.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());

        assertThat(dagTask.getId()).isEqualTo(60L);
        assertThat(dagTask.getDagVersionId()).isEqualTo(61L);
        assertThat(dagTask.getNodeCode()).isEqualTo("merge");
        assertThat(dagTask.getTaskId()).isEqualTo(62L);
        assertThat(dagTask.getName()).isEqualTo("Merge");
        assertThat(dagTask.getOverrideParams()).isEqualTo("{\"b\":2}");
        assertThat(dagTask.getTimeoutSec()).isEqualTo(120);
        assertThat(dagTask.getMaxRetry()).isEqualTo(3);
        assertThat(dagTask.getParallelGroup()).isEqualTo("group-a");
        assertThat(dagTask.getMeta()).isEqualTo("{\"debug\":true}");
        assertThat(dagTask.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }
}
