package com.tiny.platform.infrastructure.scheduling.service;

import org.quartz.JobDataMap;

import java.io.Serializable;

public final class SchedulingExecutionContext implements Serializable {

    public static final String JOB_DATA_DAG_ID = "dagId";
    public static final String JOB_DATA_DAG_RUN_ID = "dagRunId";
    public static final String JOB_DATA_DAG_VERSION_ID = "dagVersionId";
    public static final String JOB_DATA_TENANT_ID = "tenantId";
    public static final String JOB_DATA_USER_ID = "userId";
    public static final String JOB_DATA_USERNAME = "username";
    public static final String JOB_DATA_TRIGGER_TYPE = "triggerType";

    private final Long tenantId;
    private final String userId;
    private final String username;
    private final Long dagId;
    private final Long dagRunId;
    private final Long dagVersionId;
    private final String triggerType;

    private SchedulingExecutionContext(Builder builder) {
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.username = builder.username;
        this.dagId = builder.dagId;
        this.dagRunId = builder.dagRunId;
        this.dagVersionId = builder.dagVersionId;
        this.triggerType = builder.triggerType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SchedulingExecutionContext fromJobDataMap(JobDataMap jobDataMap) {
        return builder()
                .dagId(getLong(jobDataMap, JOB_DATA_DAG_ID))
                .dagRunId(getLong(jobDataMap, JOB_DATA_DAG_RUN_ID))
                .dagVersionId(getLong(jobDataMap, JOB_DATA_DAG_VERSION_ID))
                .tenantId(getLong(jobDataMap, JOB_DATA_TENANT_ID))
                .userId(getString(jobDataMap, JOB_DATA_USER_ID))
                .username(getString(jobDataMap, JOB_DATA_USERNAME))
                .triggerType(getString(jobDataMap, JOB_DATA_TRIGGER_TYPE))
                .build();
    }

    public JobDataMap toJobDataMap() {
        JobDataMap jobDataMap = new JobDataMap();
        put(jobDataMap, JOB_DATA_DAG_ID, dagId);
        put(jobDataMap, JOB_DATA_DAG_RUN_ID, dagRunId);
        put(jobDataMap, JOB_DATA_DAG_VERSION_ID, dagVersionId);
        put(jobDataMap, JOB_DATA_TENANT_ID, tenantId);
        put(jobDataMap, JOB_DATA_USER_ID, userId);
        put(jobDataMap, JOB_DATA_USERNAME, username);
        put(jobDataMap, JOB_DATA_TRIGGER_TYPE, triggerType);
        return jobDataMap;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Long getDagId() {
        return dagId;
    }

    public Long getDagRunId() {
        return dagRunId;
    }

    public Long getDagVersionId() {
        return dagVersionId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    private static void put(JobDataMap jobDataMap, String key, Object value) {
        if (value != null) {
            jobDataMap.put(key, value);
        }
    }

    private static Long getLong(JobDataMap jobDataMap, String key) {
        if (jobDataMap == null || !jobDataMap.containsKey(key)) {
            return null;
        }
        Object value = jobDataMap.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return null;
    }

    private static String getString(JobDataMap jobDataMap, String key) {
        if (jobDataMap == null || !jobDataMap.containsKey(key)) {
            return null;
        }
        Object value = jobDataMap.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public static final class Builder {
        private Long tenantId;
        private String userId;
        private String username;
        private Long dagId;
        private Long dagRunId;
        private Long dagVersionId;
        private String triggerType;

        private Builder() {}

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder dagId(Long dagId) {
            this.dagId = dagId;
            return this;
        }

        public Builder dagRunId(Long dagRunId) {
            this.dagRunId = dagRunId;
            return this;
        }

        public Builder dagVersionId(Long dagVersionId) {
            this.dagVersionId = dagVersionId;
            return this;
        }

        public Builder triggerType(String triggerType) {
            this.triggerType = triggerType;
            return this;
        }

        public SchedulingExecutionContext build() {
            return new SchedulingExecutionContext(this);
        }
    }
}
