package com.tiny.platform.core.dict.repository;

import com.tiny.platform.core.dict.model.DictItem;
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
     * 根据字典类型ID查找所有字典项
     */
    List<DictItem> findByDictTypeIdOrderBySortOrderAsc(Long dictTypeId);

    /**
     * 根据字典类型ID和启用状态查找
     */
    List<DictItem> findByDictTypeIdAndEnabledOrderBySortOrderAsc(Long dictTypeId, Boolean enabled);

    /**
     * 根据字典类型ID和租户ID查找
     */
    List<DictItem> findByDictTypeIdAndTenantIdOrderBySortOrderAsc(Long dictTypeId, Long tenantId);

    /**
     * 根据字典类型ID、值和租户ID查找
     */
    Optional<DictItem> findByDictTypeIdAndValueAndTenantId(Long dictTypeId, String value, Long tenantId);

    /**
     * 根据字典编码查找字典项（支持多租户：先查租户，再查平台）
     */
    @Query("SELECT di FROM DictItem di " +
           "JOIN di.dictType dt " +
           "WHERE dt.dictCode = :dictCode " +
           "AND (di.tenantId = :tenantId OR di.tenantId = 0) " +
           "AND di.enabled = true " +
           "ORDER BY di.tenantId DESC, di.sortOrder ASC")
    List<DictItem> findByDictCodeAndTenantId(@Param("dictCode") String dictCode, @Param("tenantId") Long tenantId);

    /**
     * 分页查询字典项
     */
    @Query("SELECT di FROM DictItem di WHERE " +
           "(:dictTypeId IS NULL OR di.dictTypeId = :dictTypeId) AND " +
           "(:value IS NULL OR di.value LIKE %:value%) AND " +
           "(:label IS NULL OR di.label LIKE %:label%) AND " +
           "(:tenantId IS NULL OR di.tenantId = :tenantId) AND " +
           "(:enabled IS NULL OR di.enabled = :enabled)")
    Page<DictItem> findByConditions(
            @Param("dictTypeId") Long dictTypeId,
            @Param("value") String value,
            @Param("label") String label,
            @Param("tenantId") Long tenantId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
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

