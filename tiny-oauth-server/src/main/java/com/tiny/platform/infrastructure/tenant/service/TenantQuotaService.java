package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.core.oauth.repository.UserAvatarRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskRepository;
import com.tiny.platform.infrastructure.export.service.ExportTaskStatus;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TenantQuotaService {

    private static final String ACTIVE = "ACTIVE";
    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final ExportTaskRepository exportTaskRepository;

    public TenantQuotaService(TenantRepository tenantRepository,
                              TenantUserRepository tenantUserRepository,
                              UserAvatarRepository userAvatarRepository,
                              ExportTaskRepository exportTaskRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userAvatarRepository = userAvatarRepository;
        this.exportTaskRepository = exportTaskRepository;
    }

    public void validateQuotaSettingsForCreate(Integer maxUsers, Integer maxStorageGb) {
        if (maxUsers != null && maxUsers < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "新建租户的最大用户数不能小于 1");
        }
        if (maxStorageGb != null && maxStorageGb < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存储配额不能为负数");
        }
    }

    public void validateQuotaSettingsForUpdate(Integer maxUsers, Integer maxStorageGb) {
        if (maxUsers != null && maxUsers < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "最大用户数不能为负数");
        }
        if (maxStorageGb != null && maxStorageGb < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存储配额不能为负数");
        }
    }

    public void assertCanCreateUsers(Long tenantId, int increment, String operation) {
        if (increment <= 0) {
            return;
        }
        Tenant tenant = requireTenant(tenantId);
        Integer maxUsers = tenant.getMaxUsers();
        if (maxUsers == null) {
            return;
        }
        long activeUsers = tenantUserRepository.countByTenantIdAndStatus(tenantId, ACTIVE);
        if (activeUsers + increment > maxUsers) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "当前租户用户配额不足，无法执行 " + operation + "；已用 " + activeUsers + " / 上限 " + maxUsers
            );
        }
    }

    public void assertMaxUsersNotBelowCurrentUsage(Long tenantId, Integer newMaxUsers) {
        if (newMaxUsers == null) {
            return;
        }
        long activeUsers = tenantUserRepository.countByTenantIdAndStatus(tenantId, ACTIVE);
        if (newMaxUsers < activeUsers) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "最大用户数不能低于当前已用数量 " + activeUsers
            );
        }
    }

    public void assertStorageQuotaAvailable(Long tenantId, long additionalBytes, String operation) {
        if (tenantId == null || additionalBytes <= 0) {
            return;
        }
        Tenant tenant = requireTenant(tenantId);
        Integer maxStorageGb = tenant.getMaxStorageGb();
        if (maxStorageGb == null) {
            return;
        }
        long usedBytes = resolveUsedStorageBytes(tenantId);
        long limitBytes = maxStorageGb.longValue() * BYTES_PER_GB;
        if (usedBytes + additionalBytes > limitBytes) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "当前租户存储配额不足，无法执行 " + operation + "；已用 "
                    + usedBytes + " B / 上限 " + limitBytes + " B"
            );
        }
    }

    public void assertMaxStorageNotBelowCurrentUsage(Long tenantId, Integer newMaxStorageGb) {
        if (newMaxStorageGb == null) {
            return;
        }
        long usedBytes = resolveUsedStorageBytes(tenantId);
        long limitBytes = newMaxStorageGb.longValue() * BYTES_PER_GB;
        if (limitBytes < usedBytes) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "存储配额不能低于当前已用空间 " + usedBytes + " B"
            );
        }
    }

    public long resolveUsedStorageBytes(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        List<Long> userIds = tenantUserRepository.findUserIdsByTenantIdAndStatus(tenantId, ACTIVE);
        long avatarBytes = 0L;
        if (!userIds.isEmpty()) {
            avatarBytes = nullToZero(userAvatarRepository.sumFileSizeByUserIdIn(userIds));
        }
        long exportBytes = nullToZero(
            exportTaskRepository.sumFileSizeBytesByTenantIdAndStatus(tenantId, ExportTaskStatus.SUCCESS)
        );
        return avatarBytes + exportBytes;
    }

    private Tenant requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("租户", tenantId));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
