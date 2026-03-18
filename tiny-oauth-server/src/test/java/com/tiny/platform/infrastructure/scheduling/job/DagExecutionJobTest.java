package com.tiny.platform.infrastructure.scheduling.job;

import com.tiny.platform.infrastructure.scheduling.service.SchedulingExecutionContext;
import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DagExecutionJobTest {

    @Test
    void executeShouldRestoreExecutionContextFromJobDataMap() throws Exception {
        SchedulingService schedulingService = mock(SchedulingService.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(SchedulingService.class)).thenReturn(schedulingService);

        SchedulerContext schedulerContext = new SchedulerContext();
        schedulerContext.put("applicationContext", applicationContext);

        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getContext()).thenReturn(schedulerContext);

        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = SchedulingExecutionContext.builder()
                .executionTenantId(88L)
                .userId("8")
                .username("alice")
                .dagId(10L)
                .dagRunId(77L)
                .dagVersionId(3L)
                .triggerType("MANUAL")
                .build()
                .toJobDataMap();
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);

        JobExecutionContext quartzContext = mock(JobExecutionContext.class);
        when(quartzContext.getJobDetail()).thenReturn(jobDetail);
        when(quartzContext.getScheduler()).thenReturn(scheduler);

        new DagExecutionJob().execute(quartzContext);

        ArgumentCaptor<SchedulingExecutionContext> executionContextCaptor =
                ArgumentCaptor.forClass(SchedulingExecutionContext.class);
        verify(schedulingService).executeDag(executionContextCaptor.capture());
        SchedulingExecutionContext executionContext = executionContextCaptor.getValue();
        assertThat(executionContext.getExecutionTenantId()).isEqualTo(88L);
        assertThat(executionContext.getUserId()).isEqualTo("8");
        assertThat(executionContext.getUsername()).isEqualTo("alice");
        assertThat(executionContext.getDagId()).isEqualTo(10L);
        assertThat(executionContext.getDagRunId()).isEqualTo(77L);
        assertThat(executionContext.getDagVersionId()).isEqualTo(3L);
        assertThat(executionContext.getTriggerType()).isEqualTo("MANUAL");
    }
}
