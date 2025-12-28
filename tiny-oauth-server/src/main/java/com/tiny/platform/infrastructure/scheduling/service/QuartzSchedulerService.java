package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import com.tiny.platform.infrastructure.scheduling.job.DagExecutionJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quartz 调度器服务
 * 负责管理 Quartz Job 和 Trigger
 */
@Service
public class QuartzSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(QuartzSchedulerService.class);

    private final Scheduler scheduler;
    private final Environment environment;

    public QuartzSchedulerService(Scheduler scheduler, Environment environment) {
        this.scheduler = scheduler;
        this.environment = environment;
    }

    /**
     * 创建或更新 DAG 的 Quartz Job（用于定时调度）
     */
    @Transactional
    public void createOrUpdateDagJob(SchedulingDag dag, String cronExpression) throws SchedulerException {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            logger.debug("DAG {} 没有配置 cron 表达式，跳过创建 Job", dag.getId());
            return;
        }

        String jobKey = "dag-" + dag.getId();
        String triggerKey = "dag-trigger-" + dag.getId();
        JobKey jobKeyObj = JobKey.jobKey(jobKey, "dag-group");
        TriggerKey triggerKeyObj = TriggerKey.triggerKey(triggerKey, "dag-trigger-group");

        // 如果 Job 已存在，先删除
        if (scheduler.checkExists(jobKeyObj)) {
            scheduler.deleteJob(jobKeyObj);
            logger.debug("删除已存在的 DAG Job, dagId: {}, jobKey: {}", dag.getId(), jobKey);
        }

        // 创建新的 JobDetail
        JobDetail jobDetail = JobBuilder.newJob(DagExecutionJob.class)
                .withIdentity(jobKeyObj)
                .usingJobData("dagId", dag.getId())
                .storeDurably(true)
                .build();

        // 创建 Cron Trigger
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKeyObj)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("创建/更新 DAG 定时调度 Job, dagId: {}, jobKey: {}, cron: {}", dag.getId(), jobKey, cronExpression);
    }

    /**
     * 立即触发 DAG 执行（创建一次性 Job）
     * @param dag DAG 对象
     * @param dagRunId DAG 运行实例 ID（手动触发时传递，定时触发时为 null）
     * @param dagVersionId DAG 版本 ID（手动触发时传递，定时触发时为 null）
     */
    @Transactional
    public void triggerDagNow(SchedulingDag dag, Long dagRunId, Long dagVersionId) throws SchedulerException {
        String jobKey = "dag-trigger-now-" + dag.getId() + "-" + System.currentTimeMillis();
        
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("dagId", dag.getId());
        if (dagRunId != null && dagRunId > 0) {
            jobDataMap.put("dagRunId", dagRunId);
        }
        if (dagVersionId != null && dagVersionId > 0) {
            jobDataMap.put("dagVersionId", dagVersionId);
        }
        
        JobDetail jobDetail = JobBuilder.newJob(DagExecutionJob.class)
                .withIdentity(jobKey, "dag-trigger-group")
                .usingJobData(jobDataMap)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + jobKey, "dag-trigger-group")
                .startNow()
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("立即触发 DAG 执行, dagId: {}, dagRunId: {}, dagVersionId: {}, jobKey: {}", 
                dag.getId(), dagRunId, dagVersionId, jobKey);
    }

    /**
     * 删除 DAG 的 Quartz Job
     */
    @Transactional
    public void deleteDagJob(Long dagId) throws SchedulerException {
        String jobKey = "dag-" + dagId;
        JobKey jobKeyObj = JobKey.jobKey(jobKey, "dag-group");
        
        if (scheduler.checkExists(jobKeyObj)) {
            scheduler.deleteJob(jobKeyObj);
            logger.info("删除 DAG Job, dagId: {}, jobKey: {}", dagId, jobKey);
        }
    }

    /**
     * 暂停 DAG 的 Quartz Job
     */
    @Transactional
    public void pauseDagJob(Long dagId) throws SchedulerException {
        String jobKey = "dag-" + dagId;
        JobKey jobKeyObj = JobKey.jobKey(jobKey, "dag-group");
        
        if (scheduler.checkExists(jobKeyObj)) {
            scheduler.pauseJob(jobKeyObj);
            logger.info("暂停 DAG Job, dagId: {}, jobKey: {}", dagId, jobKey);
        }
    }

    /**
     * 恢复 DAG 的 Quartz Job
     */
    @Transactional
    public void resumeDagJob(Long dagId) throws SchedulerException {
        String jobKey = "dag-" + dagId;
        JobKey jobKeyObj = JobKey.jobKey(jobKey, "dag-group");
        
        if (scheduler.checkExists(jobKeyObj)) {
            scheduler.resumeJob(jobKeyObj);
            logger.info("恢复 DAG Job, dagId: {}, jobKey: {}", dagId, jobKey);
        }
    }

    /**
     * 更新 DAG 的 Cron 表达式
     */
    @Transactional
    public void updateDagCron(Long dagId, String cronExpression) throws SchedulerException {
        String triggerKey = "dag-trigger-" + dagId;
        TriggerKey triggerKeyObj = TriggerKey.triggerKey(triggerKey, "dag-trigger-group");
        
        if (scheduler.checkExists(triggerKeyObj)) {
            CronTrigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKeyObj)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();
            scheduler.rescheduleJob(triggerKeyObj, newTrigger);
            logger.info("更新 DAG Cron, dagId: {}, cron: {}", dagId, cronExpression);
        }
    }

    /**
     * 检查 Quartz 是否以集群模式运行
     * 
     * @return 集群状态信息
     */
    public ClusterStatusInfo getClusterStatus() {
        try {
            SchedulerMetaData metaData = scheduler.getMetaData();
            String schedulerName = metaData.getSchedulerName();
            String schedulerInstanceId = metaData.getSchedulerInstanceId();
            int numberOfJobsExecuted = metaData.getNumberOfJobsExecuted();
            
            // 从配置中读取集群状态
            // 优先从 Spring 环境配置读取，如果不存在则尝试从 Scheduler 上下文获取
            boolean isClustered = false;
            try {
                String clusteredProperty = environment.getProperty("spring.quartz.properties.org.quartz.jobStore.isClustered");
                if (clusteredProperty != null) {
                    isClustered = Boolean.parseBoolean(clusteredProperty);
                    } else {
                        // 尝试从 Scheduler 上下文获取
                        Object clusteredObj = scheduler.getContext().get("org.quartz.jobStore.isClustered");
                        if (clusteredObj != null) {
                            isClustered = Boolean.parseBoolean(clusteredObj.toString());
                        } else {
                            logger.debug("无法从配置中获取集群状态，使用默认值 false");
                        }
                    }
            } catch (Exception e) {
                logger.warn("读取集群配置失败，默认使用 false", e);
            }
            
            // 获取启动时间
            long schedulerStarted = 0;
            try {
                if (metaData.getRunningSince() != null) {
                    schedulerStarted = metaData.getRunningSince().getTime();
                }
            } catch (Exception e) {
                logger.debug("无法获取调度器启动时间", e);
            }
            
            return new ClusterStatusInfo(
                    schedulerName,
                    schedulerInstanceId,
                    isClustered,
                    numberOfJobsExecuted,
                    schedulerStarted,
                    scheduler.isStarted(),
                    scheduler.isInStandbyMode()
            );
        } catch (SchedulerException e) {
            logger.error("获取 Quartz 集群状态失败", e);
            return new ClusterStatusInfo(
                    "UNKNOWN",
                    "UNKNOWN",
                    false,
                    0,
                    0,
                    false,
                    false
            );
        }
    }

    /**
     * Quartz 集群状态信息
     */
    public static class ClusterStatusInfo {
        private final String schedulerName;
        private final String schedulerInstanceId;
        private final boolean isClustered;
        private final int numberOfJobsExecuted;
        private final long schedulerStarted;
        private final boolean isStarted;
        private final boolean isInStandbyMode;

        public ClusterStatusInfo(String schedulerName, String schedulerInstanceId, boolean isClustered,
                                 int numberOfJobsExecuted, long schedulerStarted, boolean isStarted, boolean isInStandbyMode) {
            this.schedulerName = schedulerName;
            this.schedulerInstanceId = schedulerInstanceId;
            this.isClustered = isClustered;
            this.numberOfJobsExecuted = numberOfJobsExecuted;
            this.schedulerStarted = schedulerStarted;
            this.isStarted = isStarted;
            this.isInStandbyMode = isInStandbyMode;
        }

        public String getSchedulerName() {
            return schedulerName;
        }

        public String getSchedulerInstanceId() {
            return schedulerInstanceId;
        }

        public boolean isClustered() {
            return isClustered;
        }

        public int getNumberOfJobsExecuted() {
            return numberOfJobsExecuted;
        }

        public long getSchedulerStarted() {
            return schedulerStarted;
        }

        public boolean isStarted() {
            return isStarted;
        }

        public boolean isInStandbyMode() {
            return isInStandbyMode;
        }

        @Override
        public String toString() {
            return String.format(
                    "Quartz Scheduler Status: name=%s, instanceId=%s, clustered=%s, jobsExecuted=%d, started=%s, standby=%s",
                    schedulerName, schedulerInstanceId, isClustered, numberOfJobsExecuted, isStarted, isInStandbyMode
            );
        }
    }
}

