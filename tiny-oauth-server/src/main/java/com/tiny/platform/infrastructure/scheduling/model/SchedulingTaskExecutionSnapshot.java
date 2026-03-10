package com.tiny.platform.infrastructure.scheduling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * 任务实例执行快照。
 * 在任务入队时固化执行期依赖的 task/taskType 配置，避免后续配置变更污染已入队实例。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchedulingTaskExecutionSnapshot implements Serializable {

    private TaskSnapshot task;

    private TaskTypeSnapshot taskType;

    public static SchedulingTaskExecutionSnapshot from(
            SchedulingTask task,
            SchedulingTaskType taskType) {
        SchedulingTaskExecutionSnapshot snapshot = new SchedulingTaskExecutionSnapshot();

        TaskSnapshot taskSnapshot = new TaskSnapshot();
        taskSnapshot.setTaskId(task.getId());
        taskSnapshot.setTaskTypeId(task.getTypeId());
        taskSnapshot.setCode(task.getCode());
        taskSnapshot.setName(task.getName());
        taskSnapshot.setParams(task.getParams());
        taskSnapshot.setTimeoutSec(task.getTimeoutSec());
        taskSnapshot.setMaxRetry(task.getMaxRetry());
        taskSnapshot.setRetryPolicy(task.getRetryPolicy());
        taskSnapshot.setConcurrencyPolicy(task.getConcurrencyPolicy());
        taskSnapshot.setEnabled(task.getEnabled());
        snapshot.setTask(taskSnapshot);

        TaskTypeSnapshot taskTypeSnapshot = new TaskTypeSnapshot();
        taskTypeSnapshot.setTaskTypeId(taskType.getId());
        taskTypeSnapshot.setCode(taskType.getCode());
        taskTypeSnapshot.setName(taskType.getName());
        taskTypeSnapshot.setExecutor(taskType.getExecutor());
        taskTypeSnapshot.setParamSchema(taskType.getParamSchema());
        taskTypeSnapshot.setDefaultTimeoutSec(taskType.getDefaultTimeoutSec());
        taskTypeSnapshot.setDefaultMaxRetry(taskType.getDefaultMaxRetry());
        taskTypeSnapshot.setEnabled(taskType.getEnabled());
        snapshot.setTaskType(taskTypeSnapshot);

        return snapshot;
    }

    public TaskSnapshot getTask() {
        return task;
    }

    public void setTask(TaskSnapshot task) {
        this.task = task;
    }

    public TaskTypeSnapshot getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskTypeSnapshot taskType) {
        this.taskType = taskType;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskSnapshot implements Serializable {
        private Long taskId;
        private Long taskTypeId;
        private String code;
        private String name;
        private String params;
        private Integer timeoutSec;
        private Integer maxRetry;
        private String retryPolicy;
        private String concurrencyPolicy;
        private Boolean enabled;

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public Long getTaskTypeId() {
            return taskTypeId;
        }

        public void setTaskTypeId(Long taskTypeId) {
            this.taskTypeId = taskTypeId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParams() {
            return params;
        }

        public void setParams(String params) {
            this.params = params;
        }

        public Integer getTimeoutSec() {
            return timeoutSec;
        }

        public void setTimeoutSec(Integer timeoutSec) {
            this.timeoutSec = timeoutSec;
        }

        public Integer getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(Integer maxRetry) {
            this.maxRetry = maxRetry;
        }

        public String getRetryPolicy() {
            return retryPolicy;
        }

        public void setRetryPolicy(String retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        public String getConcurrencyPolicy() {
            return concurrencyPolicy;
        }

        public void setConcurrencyPolicy(String concurrencyPolicy) {
            this.concurrencyPolicy = concurrencyPolicy;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskTypeSnapshot implements Serializable {
        private Long taskTypeId;
        private String code;
        private String name;
        private String executor;
        private String paramSchema;
        private Integer defaultTimeoutSec;
        private Integer defaultMaxRetry;
        private Boolean enabled;

        public Long getTaskTypeId() {
            return taskTypeId;
        }

        public void setTaskTypeId(Long taskTypeId) {
            this.taskTypeId = taskTypeId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getExecutor() {
            return executor;
        }

        public void setExecutor(String executor) {
            this.executor = executor;
        }

        public String getParamSchema() {
            return paramSchema;
        }

        public void setParamSchema(String paramSchema) {
            this.paramSchema = paramSchema;
        }

        public Integer getDefaultTimeoutSec() {
            return defaultTimeoutSec;
        }

        public void setDefaultTimeoutSec(Integer defaultTimeoutSec) {
            this.defaultTimeoutSec = defaultTimeoutSec;
        }

        public Integer getDefaultMaxRetry() {
            return defaultMaxRetry;
        }

        public void setDefaultMaxRetry(Integer defaultMaxRetry) {
            this.defaultMaxRetry = defaultMaxRetry;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
