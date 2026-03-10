package com.tiny.platform.infrastructure.scheduling.config;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRepository;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzCronRecoveryRunnerTest {

    @Test
    void shouldSkipWhenNoCronDagNeedsRecovery() throws Exception {
        SchedulingDagRepository dagRepository = mock(SchedulingDagRepository.class);
        QuartzSchedulerService quartzSchedulerService = mock(QuartzSchedulerService.class);
        when(dagRepository.findAllEnabledWithCron()).thenReturn(List.of());

        new QuartzCronRecoveryRunner(dagRepository, quartzSchedulerService).run(new DefaultApplicationArguments());

        verify(quartzSchedulerService, never()).createOrUpdateDagJob(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRecoverOnlyCronEnabledDagAndContinueOnSchedulerException() throws Exception {
        SchedulingDagRepository dagRepository = mock(SchedulingDagRepository.class);
        QuartzSchedulerService quartzSchedulerService = mock(QuartzSchedulerService.class);
        SchedulingDag disabledCronDag = dag(1L, false, "0 0 * * * ?", "Asia/Shanghai");
        SchedulingDag okDag = dag(2L, true, "0 5 * * * ?", "UTC");
        SchedulingDag brokenDag = dag(3L, true, "0 10 * * * ?", "UTC");
        when(dagRepository.findAllEnabledWithCron()).thenReturn(List.of(disabledCronDag, okDag, brokenDag));
        org.mockito.Mockito.doThrow(new SchedulerException("boom"))
                .when(quartzSchedulerService)
                .createOrUpdateDagJob(brokenDag, brokenDag.getCronExpression(), brokenDag.getCronTimezone());

        new QuartzCronRecoveryRunner(dagRepository, quartzSchedulerService).run(new DefaultApplicationArguments());

        verify(quartzSchedulerService, never()).createOrUpdateDagJob(disabledCronDag, disabledCronDag.getCronExpression(), disabledCronDag.getCronTimezone());
        verify(quartzSchedulerService).createOrUpdateDagJob(okDag, okDag.getCronExpression(), okDag.getCronTimezone());
        verify(quartzSchedulerService, times(1)).createOrUpdateDagJob(brokenDag, brokenDag.getCronExpression(), brokenDag.getCronTimezone());
    }

    private SchedulingDag dag(Long id, boolean cronEnabled, String cron, String timezone) {
        SchedulingDag dag = new SchedulingDag();
        dag.setId(id);
        dag.setCronEnabled(cronEnabled);
        dag.setCronExpression(cron);
        dag.setCronTimezone(timezone);
        return dag;
    }
}
