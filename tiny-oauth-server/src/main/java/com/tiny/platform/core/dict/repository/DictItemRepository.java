package com.tiny.platform.core.dict.repository;

import com.tiny.platform.core.dict.model.DictItem;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 字典项仓储接口
 */
@Repository
public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    Optional<DictItem> findByDictTypeIdAndValueAndTenantId(Long dictTypeId, String value, Long tenantId);

    /** 平台字典项查询：tenant_id IS NULL */
    Optional<DictItem> findByDictTypeIdAndValueAndTenantIdIsNull(Long dictTypeId, String value);

    /**
     * 查询当前租户在指定字典类型下可见的字典项。
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE di.dictTypeId = :dictTypeId " +
           "AND ((dt.tenantId IS NULL AND (di.tenantId IS NULL OR di.tenantId = :tenantId)) " +
           "  OR (dt.tenantId = :tenantId AND di.tenantId = :tenantId)) " +
           "ORDER BY CASE WHEN di.tenantId IS NULL THEN 0 ELSE 1 END ASC, di.sortOrder ASC, di.id ASC")
    List<DictItem> findVisibleByDictTypeId(@Param("dictTypeId") Long dictTypeId, @Param("tenantId") Long tenantId);

    /**
     * 分页查询当前租户可见的字典项（平台 + 当前租户）
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE ((dt.tenantId IS NULL AND (di.tenantId IS NULL OR di.tenantId = :tenantId)) " +
           "   OR (dt.tenantId = :tenantId AND di.tenantId = :tenantId)) AND " +
           "(:dictTypeId IS NULL OR di.dictTypeId = :dictTypeId) AND " +
           "(:value IS NULL OR di.value LIKE %:value%) AND " +
           "(:label IS NULL OR di.label LIKE %:label%) AND " +
           "(:enabled IS NULL OR di.enabled = :enabled)")
    Page<DictItem> findVisibleByConditions(
            @Param("tenantId") Long tenantId,
            @Param("dictTypeId") Long dictTypeId,
            @Param("value") String value,
            @Param("label") String label,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /**
     * 查询当前租户可见的字典项列表（用于 overlay 合并后的内存分页）。
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE ((dt.tenantId IS NULL AND (di.tenantId IS NULL OR di.tenantId = :tenantId)) " +
           "   OR (dt.tenantId = :tenantId AND di.tenantId = :tenantId)) AND " +
           "(:dictTypeId IS NULL OR di.dictTypeId = :dictTypeId) AND " +
           "(:value IS NULL OR di.value LIKE %:value%) AND " +
           "(:label IS NULL OR di.label LIKE %:label%) AND " +
           "(:enabled IS NULL OR di.enabled = :enabled)")
    List<DictItem> findVisibleByConditions(
            @Param("tenantId") Long tenantId,
            @Param("dictTypeId") Long dictTypeId,
            @Param("value") String value,
            @Param("label") String label,
            @Param("enabled") Boolean enabled,
            Sort sort
    );

    boolean existsByDictTypeIdAndValueAndTenantIdAndIdNot(Long dictTypeId, String value, Long tenantId, Long id);

    boolean existsByDictTypeIdAndValueAndTenantId(Long dictTypeId, String value, Long tenantId);

    /**
     * 查询平台字典类型下的平台字典项（dt.tenantId IS NULL AND di.tenantId IS NULL）。
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE di.dictTypeId = :dictTypeId " +
           "AND dt.tenantId IS NULL AND di.tenantId IS NULL " +
           "ORDER BY di.sortOrder ASC, di.id ASC")
    List<DictItem> findPlatformByDictTypeId(@Param("dictTypeId") Long dictTypeId);

    /**
     * 分页查询平台字典项（dt.tenantId IS NULL AND di.tenantId IS NULL）。
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE dt.tenantId IS NULL AND di.tenantId IS NULL AND " +
           "(:dictTypeId IS NULL OR di.dictTypeId = :dictTypeId) AND " +
           "(:value IS NULL OR di.value LIKE %:value%) AND " +
           "(:label IS NULL OR di.label LIKE %:label%) AND " +
           "(:enabled IS NULL OR di.enabled = :enabled)")
    Page<DictItem> findPlatformByConditions(
            @Param("dictTypeId") Long dictTypeId,
            @Param("value") String value,
            @Param("label") String label,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /** 平台字典项唯一性检查：tenant_id IS NULL */
    boolean existsByDictTypeIdAndValueAndTenantIdIsNull(Long dictTypeId, String value);

    /** 平台字典项唯一性检查（排除指定 ID）：tenant_id IS NULL */
    boolean existsByDictTypeIdAndValueAndTenantIdIsNullAndIdNot(Long dictTypeId, String value, Long id);

    void deleteByDictTypeId(Long dictTypeId);
}
