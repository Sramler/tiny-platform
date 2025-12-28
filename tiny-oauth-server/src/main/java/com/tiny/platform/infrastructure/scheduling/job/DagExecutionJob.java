package com.tiny.platform.infrastructure.scheduling.job;

import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * DAG 执行 Job
 * 由 Quartz 调度触发，执行 DAG
 */
@Component
public class DagExecutionJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DagExecutionJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        Long dagId = jobDataMap.getLong("dagId");
        Long dagRunId = jobDataMap.containsKey("dagRunId") ? jobDataMap.getLong("dagRunId") : null;
        Long dagVersionId = jobDataMap.containsKey("dagVersionId") ? jobDataMap.getLong("dagVersionId") : null;
        
        boolean isManualTrigger = (dagRunId != null && dagRunId > 0);
        logger.info("开始执行 DAG, dagId: {}, dagRunId: {}, isManualTrigger: {}", dagId, dagRunId, isManualTrigger);

        // 从 ApplicationContext 获取 Bean（因为 Job 实例由 Quartz 管理，不能使用 @Autowired）
        ApplicationContext applicationContext;
        try {
            applicationContext = (ApplicationContext) context.getScheduler()
                    .getContext().get("applicationContext");
        } catch (Exception e) {
            throw new JobExecutionException("无法获取 ApplicationContext: " + e.getMessage(), e);
        }
        if (applicationContext == null) {
            throw new JobExecutionException("无法获取 ApplicationContext");
        }

        // 获取 SchedulingService，调用带事务的方法
        SchedulingService schedulingService = applicationContext.getBean(SchedulingService.class);

        try {
            // 调用 Service 方法，确保事务一致性
            schedulingService.executeDag(dagId, dagRunId, dagVersionId);
            logger.info("DAG执行完成, dagId: {}, dagRunId: {}", dagId, dagRunId);
        } catch (Exception e) {
            logger.error("DAG执行失败, dagId: {}, dagRunId: {}", dagId, dagRunId, e);
            throw new JobExecutionException("DAG执行失败: " + e.getMessage(), e);
        }
    }
}

