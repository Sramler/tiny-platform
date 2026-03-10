package com.tiny.platform.core.dict.repository;

import com.tiny.platform.core.dict.model.DictType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 根据字典编码和租户ID查找
     */
    Optional<DictType> findByDictCodeAndTenantId(String dictCode, Long tenantId);

    /**
     * 查询当前租户可见的字典类型（平台 + 当前租户）
     */
    @Query("SELECT d FROM DictType d " +
           "WHERE d.tenantId = 0 OR d.tenantId = :tenantId " +
           "ORDER BY d.tenantId ASC, d.sortOrder ASC, d.id ASC")
    List<DictType> findVisibleByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 分页查询当前租户可见的字典类型（平台 + 当前租户）
     */
    @Query("SELECT d FROM DictType d WHERE " +
           "(d.tenantId = 0 OR d.tenantId = :tenantId) AND " +
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

    /**
     * 检查当前租户内的字典编码是否存在（排除指定ID）
     */
    boolean existsByDictCodeAndTenantIdAndIdNot(String dictCode, Long tenantId, Long id);

    /**
     * 检查当前租户内的字典编码是否存在
     */
    boolean existsByDictCodeAndTenantId(String dictCode, Long tenantId);
}
