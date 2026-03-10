package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.job.DagExecutionJob;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.core.env.Environment;

import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzSchedulerServiceTest {

    @Test
    void createOrUpdateDagJobShouldSkipWhenCronIsBlank() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);

        quartzSchedulerService.createOrUpdateDagJob(dag, "   ", null);

        verify(scheduler, never()).checkExists(any(JobKey.class));
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void createOrUpdateDagJobShouldSkipWhenExistingCronDefinitionMatches() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);

        JobKey jobKey = JobKey.jobKey("dag-10", "dag-group");
        TriggerKey triggerKey = TriggerKey.triggerKey("dag-trigger-10", "dag-trigger-group");
        JobDetail existingJob = JobBuilder.newJob(DagExecutionJob.class)
                .withIdentity(jobKey)
                .usingJobData(SchedulingExecutionContext.builder()
                        .dagId(10L)
                        .tenantId(88L)
                        .username("Quartz Scheduler")
                        .triggerType("SCHEDULE")
                        .build()
                        .toJobDataMap())
                .storeDurably(true)
                .build();
        CronTrigger existingTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(existingJob)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai")))
                .build();

        when(scheduler.checkExists(jobKey)).thenReturn(true);
        when(scheduler.checkExists(triggerKey)).thenReturn(true);
        when(scheduler.getJobDetail(jobKey)).thenReturn(existingJob);
        when(scheduler.getTrigger(triggerKey)).thenReturn(existingTrigger);

        quartzSchedulerService.createOrUpdateDagJob(dag, "0 0 12 * * ?", "Asia/Shanghai");

        verify(scheduler, never()).deleteJob(jobKey);
        verify(scheduler, never()).unscheduleJob(triggerKey);
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void createOrUpdateDagJobShouldDeleteOrphanTriggerAndScheduleFreshJob() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);

        JobKey jobKey = JobKey.jobKey("dag-10", "dag-group");
        TriggerKey triggerKey = TriggerKey.triggerKey("dag-trigger-10", "dag-trigger-group");
        when(scheduler.checkExists(jobKey)).thenReturn(false);
        when(scheduler.checkExists(triggerKey)).thenReturn(true);

        quartzSchedulerService.createOrUpdateDagJob(dag, "0 0 12 * * ?", "");

        verify(scheduler).unscheduleJob(triggerKey);
        ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());
        assertThat(jobDetailCaptor.getValue().getJobDataMap().getLong("tenantId")).isEqualTo(88L);
        assertThat(((CronTrigger) triggerCaptor.getValue()).getTimeZone().getID()).isEqualTo(TimeZone.getDefault().getID());
    }

    @Test
    void createOrUpdateDagJobShouldTolerateConcurrentCreateWhenDefinitionAlreadyInSync() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);

        JobKey jobKey = JobKey.jobKey("dag-10", "dag-group");
        TriggerKey triggerKey = TriggerKey.triggerKey("dag-trigger-10", "dag-trigger-group");
        JobDetail existingJob = JobBuilder.newJob(DagExecutionJob.class)
                .withIdentity(jobKey)
                .usingJobData(SchedulingExecutionContext.builder()
                        .dagId(10L)
                        .tenantId(88L)
                        .build()
                        .toJobDataMap())
                .storeDurably(true)
                .build();
        CronTrigger existingTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(existingJob)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("UTC")))
                .build();

        when(scheduler.checkExists(jobKey)).thenReturn(false);
        when(scheduler.checkExists(triggerKey)).thenReturn(false);
        doThrow(new ObjectAlreadyExistsException(existingJob))
                .when(scheduler)
                .scheduleJob(any(JobDetail.class), any(Trigger.class));
        when(scheduler.getJobDetail(jobKey)).thenReturn(existingJob);
        when(scheduler.getTrigger(triggerKey)).thenReturn(existingTrigger);

        quartzSchedulerService.createOrUpdateDagJob(dag, "0 0 12 * * ?", "UTC");

        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void triggerDagNowShouldUseUuidBasedJobKey() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);

        SchedulingExecutionContext executionContext = SchedulingExecutionContext.builder()
                .tenantId(88L)
                .username("alice")
                .dagId(10L)
                .dagRunId(77L)
                .dagVersionId(3L)
                .triggerType("MANUAL")
                .build();

        quartzSchedulerService.triggerDagNow(dag, executionContext);

        ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

        JobDetail jobDetail = jobDetailCaptor.getValue();
        String jobName = jobDetail.getKey().getName();
        assertThat(jobName).startsWith("dag-trigger-now-10-");
        UUID.fromString(jobName.substring("dag-trigger-now-10-".length()));
        assertThat(jobDetail.getJobClass()).isEqualTo(DagExecutionJob.class);
        assertThat(jobDetail.getJobDataMap().getLong("tenantId")).isEqualTo(88L);
        assertThat(jobDetail.getJobDataMap().getLong("dagRunId")).isEqualTo(77L);
        assertThat(jobDetail.getJobDataMap().getLong("dagVersionId")).isEqualTo(3L);
        assertThat(triggerCaptor.getValue().getKey().getName()).startsWith("trigger-" + jobName);
    }

    @Test
    void deletePauseResumeAndUpdateCronShouldOperateOnlyWhenQuartzEntriesExist() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        JobKey jobKey = JobKey.jobKey("dag-10", "dag-group");
        TriggerKey triggerKey = TriggerKey.triggerKey("dag-trigger-10", "dag-trigger-group");
        when(scheduler.checkExists(jobKey)).thenReturn(true);
        when(scheduler.checkExists(triggerKey)).thenReturn(true);

        quartzSchedulerService.deleteDagJob(10L);
        quartzSchedulerService.pauseDagJob(10L);
        quartzSchedulerService.resumeDagJob(10L);
        quartzSchedulerService.updateDagCron(10L, "0 15 * * * ?");

        verify(scheduler).deleteJob(jobKey);
        verify(scheduler).pauseJob(jobKey);
        verify(scheduler).resumeJob(jobKey);
        verify(scheduler).rescheduleJob(eq(triggerKey), any(CronTrigger.class));
    }

    @Test
    void getClusterStatusShouldReadEnvironmentPropertyFirst() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Environment environment = mock(Environment.class);
        QuartzSchedulerService quartzSchedulerService = new QuartzSchedulerService(scheduler, environment);

        SchedulerMetaData metaData = mock(SchedulerMetaData.class);
        when(metaData.getSchedulerName()).thenReturn("main-scheduler");
        when(metaData.getSchedulerInstanceId()).thenReturn("node-1");
        when(metaData.getNumberOfJobsExecuted()).thenReturn(12);
        when(metaData.getRunningSince()).thenReturn(new java.util.Date(123456L));
        when(scheduler.getMetaData()).thenReturn(metaData);
        when(environment.getProperty("spring.quartz.properties.org.quartz.jobStore.isClustered")).thenReturn("true");
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.isInStandbyMode()).thenReturn(false);

        QuartzSchedulerService.ClusterStatusInfo status = quartzSchedulerService.getClusterStatus();

        assertThat(status.getSchedulerName()).isEqualTo("main-scheduler");
        assertThat(status.getSchedulerInstanceId()).isEqualTo("node-1");
        assertThat(status.isClustered()).isTrue();
        assertThat(status.getNumberOfJobsExecuted()).isEqualTo(12);
        assertThat(status.getSchedulerStarted()).isEqualTo(123456L);
        assertThat(status.isStarted()).isTrue();
        assertThat(status.isInStandbyMode()).isFalse();
    }
}
