package com.tiny.platform.application.oauth.workflow.model;

import java.time.LocalDateTime;
import java.util.List;

public record ProcessModelGroupDto(
    String modelKey,
    String name,
    String scopeType,
    Long recordTenantId,
    Integer latestVersion,
    Integer latestDesignVersion,
    String latestStatus,
    Integer currentRuntimeVersion,
    String currentDeploymentId,
    Boolean hasUndeployedChanges,
    Integer versionCount,
    LocalDateTime updatedAt,
    String updatedBy,
    ProcessModelDto latestModel,
    List<ProcessModelDto> versions
) {

    public static ProcessModelGroupDto from(List<ProcessModelDto> rawVersions) {
        if (rawVersions == null || rawVersions.isEmpty()) {
            throw new IllegalArgumentException("流程模型分组不能为空");
        }

        List<ProcessModelDto> versions = rawVersions.stream()
            .sorted(ProcessModelGroupDto::compareVersionDesc)
            .toList();
        ProcessModelDto latestModel = versions.get(0);
        ProcessModelDto currentRuntimeModel = versions.stream()
            .filter(ProcessModelGroupDto::isCurrentRuntime)
            .findFirst()
            .orElse(null);
        ProcessModelDto lastUpdatedModel = versions.stream()
            .max(ProcessModelGroupDto::compareUpdatedAt)
            .orElse(latestModel);
        Integer latestDesignVersion = latestModel.version();
        Integer currentRuntimeVersion = currentRuntimeModel != null ? currentRuntimeModel.version() : null;

        return new ProcessModelGroupDto(
            latestModel.modelKey(),
            latestModel.name(),
            latestModel.scopeType(),
            latestModel.recordTenantId(),
            latestModel.version(),
            latestDesignVersion,
            latestModel.status(),
            currentRuntimeVersion,
            currentRuntimeModel != null ? currentRuntimeModel.deploymentId() : null,
            hasUndeployedChanges(latestDesignVersion, currentRuntimeVersion),
            versions.size(),
            lastUpdatedModel.updatedAt(),
            lastUpdatedModel.updatedBy(),
            latestModel,
            versions
        );
    }

    private static boolean isCurrentRuntime(ProcessModelDto model) {
        return "CURRENT_RUNTIME".equals(model.runtimeState());
    }

    private static boolean hasUndeployedChanges(Integer latestDesignVersion, Integer currentRuntimeVersion) {
        if (latestDesignVersion == null) {
            return false;
        }
        return currentRuntimeVersion == null || latestDesignVersion > currentRuntimeVersion;
    }

    private static int compareVersionDesc(ProcessModelDto left, ProcessModelDto right) {
        int versionCompare = Integer.compare(nullToZero(right.version()), nullToZero(left.version()));
        if (versionCompare != 0) {
            return versionCompare;
        }
        return Long.compare(nullToZero(right.id()), nullToZero(left.id()));
    }

    private static int compareUpdatedAt(ProcessModelDto left, ProcessModelDto right) {
        LocalDateTime leftUpdatedAt = left.updatedAt();
        LocalDateTime rightUpdatedAt = right.updatedAt();
        if (leftUpdatedAt == null && rightUpdatedAt == null) {
            return Long.compare(nullToZero(left.id()), nullToZero(right.id()));
        }
        if (leftUpdatedAt == null) {
            return -1;
        }
        if (rightUpdatedAt == null) {
            return 1;
        }
        int updatedAtCompare = leftUpdatedAt.compareTo(rightUpdatedAt);
        if (updatedAtCompare != 0) {
            return updatedAtCompare;
        }
        return Long.compare(nullToZero(left.id()), nullToZero(right.id()));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
