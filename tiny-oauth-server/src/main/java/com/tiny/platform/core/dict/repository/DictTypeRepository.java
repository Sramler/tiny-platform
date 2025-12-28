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
     * 根据字典编码查找
     */
    Optional<DictType> findByDictCode(String dictCode);

    /**
     * 根据字典编码和租户ID查找
     */
    Optional<DictType> findByDictCodeAndTenantId(String dictCode, Long tenantId);

    /**
     * 根据租户ID查找所有字典类型
     */
    List<DictType> findByTenantIdOrderBySortOrderAsc(Long tenantId);

    /**
     * 根据租户ID和启用状态查找
     */
    List<DictType> findByTenantIdAndEnabledOrderBySortOrderAsc(Long tenantId, Boolean enabled);

    /**
     * 分页查询字典类型
     */
    @Query("SELECT d FROM DictType d WHERE " +
           "(:dictCode IS NULL OR d.dictCode LIKE %:dictCode%) AND " +
           "(:dictName IS NULL OR d.dictName LIKE %:dictName%) AND " +
           "(:tenantId IS NULL OR d.tenantId = :tenantId) AND " +
           "(:enabled IS NULL OR d.enabled = :enabled)")
    Page<DictType> findByConditions(
            @Param("dictCode") String dictCode,
            @Param("dictName") String dictName,
            @Param("tenantId") Long tenantId,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /**
     * 检查字典编码是否存在（排除指定ID）
     */
    boolean existsByDictCodeAndIdNot(String dictCode, Long id);

    /**
     * 检查字典编码是否存在
     */
    boolean existsByDictCode(String dictCode);
}

