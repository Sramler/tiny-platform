package com.tiny.platform.infrastructure.scheduling.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DAG 边表实体（scheduling_dag_edge）
 */
@Entity
@Table(name = "scheduling_dag_edge")
public class SchedulingDagEdge implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dag_version_id", nullable = false)
    private Long dagVersionId;

    @Column(name = "from_node_code", nullable = false, length = 128)
    private String fromNodeCode;

    @Column(name = "to_node_code", nullable = false, length = 128)
    private String toNodeCode;

    @Column(columnDefinition = "JSON")
    private String condition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDagVersionId() {
        return dagVersionId;
    }

    public void setDagVersionId(Long dagVersionId) {
        this.dagVersionId = dagVersionId;
    }

    public String getFromNodeCode() {
        return fromNodeCode;
    }

    public void setFromNodeCode(String fromNodeCode) {
        this.fromNodeCode = fromNodeCode;
    }

    public String getToNodeCode() {
        return toNodeCode;
    }

    public void setToNodeCode(String toNodeCode) {
        this.toNodeCode = toNodeCode;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


