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

    /**
     * 根据字典类型ID、值和租户ID查找
     */
    Optional<DictItem> findByDictTypeIdAndValueAndTenantId(Long dictTypeId, String value, Long tenantId);

    /**
     * 查询当前租户在指定字典类型下可见的字典项。
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE di.dictTypeId = :dictTypeId " +
           "AND ((dt.tenantId = 0 AND (di.tenantId = 0 OR di.tenantId = :tenantId)) " +
           "  OR (dt.tenantId = :tenantId AND di.tenantId = :tenantId)) " +
           "ORDER BY di.tenantId ASC, di.sortOrder ASC, di.id ASC")
    List<DictItem> findVisibleByDictTypeId(@Param("dictTypeId") Long dictTypeId, @Param("tenantId") Long tenantId);

    /**
     * 分页查询当前租户可见的字典项（平台 + 当前租户）
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE ((dt.tenantId = 0 AND (di.tenantId = 0 OR di.tenantId = :tenantId)) " +
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
           "WHERE ((dt.tenantId = 0 AND (di.tenantId = 0 OR di.tenantId = :tenantId)) " +
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

    /**
     * 检查字典项是否存在（同一字典类型下，同一租户内value唯一）
     */
    boolean existsByDictTypeIdAndValueAndTenantIdAndIdNot(Long dictTypeId, String value, Long tenantId, Long id);

    /**
     * 检查字典项是否存在
     */
    boolean existsByDictTypeIdAndValueAndTenantId(Long dictTypeId, String value, Long tenantId);

    /**
     * 根据字典类型ID删除所有字典项
     */
    void deleteByDictTypeId(Long dictTypeId);
}
