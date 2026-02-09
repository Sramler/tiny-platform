package com.tiny.platform.infrastructure.scheduling.dto;

import java.io.Serializable;

/**
 * DAG 运行统计（Run 级别：scheduling_dag_run）
 */
public class SchedulingDagStatsDto implements Serializable {

    private long total;
    private long success;
    private long failed;
    private Long avgDurationMs;
    private Long p95DurationMs;
    private Long p99DurationMs;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public Long getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(Long avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

    public Long getP95DurationMs() {
        return p95DurationMs;
    }

    public void setP95DurationMs(Long p95DurationMs) {
        this.p95DurationMs = p95DurationMs;
    }

    public Long getP99DurationMs() {
        return p99DurationMs;
    }

    public void setP99DurationMs(Long p99DurationMs) {
        this.p99DurationMs = p99DurationMs;
    }
}
