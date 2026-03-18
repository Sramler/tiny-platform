package com.tiny.platform.infrastructure.scheduling.config;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagVersionRepository;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后根据 scheduling_dag.cron_expression 恢复 Quartz 定时任务。
 * 保证 Cron 配置持久化在业务表后，重启或 Quartz 表丢失时仍能从 DB 恢复。
 */
@Component
@Order(100)
public class QuartzCronRecoveryRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(QuartzCronRecoveryRunner.class);

    private final SchedulingDagRepository dagRepository;
    private final SchedulingDagVersionRepository dagVersionRepository;
    private final QuartzSchedulerService quartzSchedulerService;

    public QuartzCronRecoveryRunner(SchedulingDagRepository dagRepository,
                                   SchedulingDagVersionRepository dagVersionRepository,
                                   QuartzSchedulerService quartzSchedulerService) {
        this.dagRepository = dagRepository;
        this.dagVersionRepository = dagVersionRepository;
        this.quartzSchedulerService = quartzSchedulerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<SchedulingDag> dags = dagRepository.findAllEnabledWithCron();
            if (dags.isEmpty()) {
                logger.debug("无需要恢复的 DAG Cron 配置");
                return;
            }
            int ok = 0;
            for (SchedulingDag dag : dags) {
                try {
                    // 只恢复 enabled + cronEnabled + 有 ACTIVE 版本 的 DAG（按租户过滤，避免跨租户）
                    if (dag.getTenantId() != null
                            ? dagVersionRepository.findByDagIdAndStatusAndTenantId(dag.getId(), "ACTIVE", dag.getTenantId()).isEmpty()
                            : dagVersionRepository.findByDagIdAndStatus(dag.getId(), "ACTIVE").isEmpty()) {
                        logger.debug("跳过恢复 DAG Cron（无 ACTIVE 版本）, dagId: {}", dag.getId());
                        continue;
                    }
                    quartzSchedulerService.createOrUpdateDagJob(dag, dag.getCronExpression(), dag.getCronTimezone());
                    ok++;
                } catch (SchedulerException e) {
                    logger.warn("恢复 DAG Cron 失败, dagId: {}, cron: {}, error: {}",
                            dag.getId(), dag.getCronExpression(), e.getMessage());
                }
            }
            logger.info("Cron 重启恢复完成: 共 {} 个 DAG 需恢复, 成功 {}", dags.size(), ok);
        } catch (Exception e) {
            logger.error("Cron 重启恢复过程异常", e);
        }
    }
}
