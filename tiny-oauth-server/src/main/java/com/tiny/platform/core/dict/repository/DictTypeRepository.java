package com.tiny.platform.core.dict.repository;

import com.tiny.platform.core.dict.model.DictType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 字典类型仓储接口
 */
@Repository
public interface DictTypeRepository extends JpaRepository<DictType, Long> {

    Optional<DictType> findByDictCodeAndTenantId(String dictCode, Long tenantId);

    /** 平台字典查询：tenant_id IS NULL */
    Optional<DictType> findByDictCodeAndTenantIdIsNull(String dictCode);

    /**
     * 查询当前租户可见的字典类型（平台 + 当前租户）
     */
    @Query("SELECT d FROM DictType d " +
           "WHERE d.tenantId IS NULL OR d.tenantId = :tenantId " +
           "ORDER BY CASE WHEN d.tenantId IS NULL THEN 0 ELSE 1 END ASC, d.sortOrder ASC, d.id ASC")
    List<DictType> findVisibleByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 分页查询当前租户可见的字典类型（平台 + 当前租户）
     */
    @Query("SELECT d FROM DictType d WHERE " +
           "(d.tenantId IS NULL OR d.tenantId = :tenantId) AND " +
           "(:dictCode IS NULL OR d.dictCode LIKE %:dictCode%) AND " +
           "(:dictName IS NULL OR d.dictName LIKE %:dictName%) AND " +
           "(:enabled IS NULL OR d.enabled = :enabled)")
    Page<DictType> findVisibleByConditions(
            @Param("dictCode") String dictCode,
            @Param("dictName") String dictName,
            @Param("tenantId") Long tenantId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    @Query("SELECT d FROM DictType d WHERE " +
           "(d.tenantId IS NULL OR d.tenantId = :tenantId) AND " +
           "(:dictCode IS NULL OR d.dictCode LIKE %:dictCode%) AND " +
           "(:dictName IS NULL OR d.dictName LIKE %:dictName%) AND " +
           "(:enabled IS NULL OR d.enabled = :enabled)")
    List<DictType> findVisibleByConditions(
            @Param("dictCode") String dictCode,
            @Param("dictName") String dictName,
            @Param("tenantId") Long tenantId,
            @Param("enabled") Boolean enabled,
            Sort sort
    );

    boolean existsByDictCodeAndTenantIdAndIdNot(String dictCode, Long tenantId, Long id);

    boolean existsByDictCodeAndTenantId(String dictCode, Long tenantId);

    /**
     * 分页查询平台字典类型（tenant_id IS NULL）。
     */
    @Query("SELECT d FROM DictType d WHERE " +
           "d.tenantId IS NULL AND " +
           "(:dictCode IS NULL OR d.dictCode LIKE %:dictCode%) AND " +
           "(:dictName IS NULL OR d.dictName LIKE %:dictName%) AND " +
           "(:enabled IS NULL OR d.enabled = :enabled)")
    Page<DictType> findPlatformByConditions(
            @Param("dictCode") String dictCode,
            @Param("dictName") String dictName,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /** 平台字典唯一性检查：tenant_id IS NULL */
    boolean existsByDictCodeAndTenantIdIsNull(String dictCode);

    /** 平台字典唯一性检查（排除指定 ID）：tenant_id IS NULL */
    boolean existsByDictCodeAndTenantIdIsNullAndIdNot(String dictCode, Long id);
}
