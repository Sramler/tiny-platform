package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceProjection;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资源数据访问层
 * 提供资源的CRUD操作和自定义查询方法
 */
@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {
    
    /**
     * 根据资源类型查找资源
     * @param type 资源类型
     * @return 资源列表
     */
    List<Resource> findByTypeOrderBySortAsc(ResourceType type);
    
    /**
     * 根据多个资源类型查找资源
     * @param types 资源类型列表
     * @return 资源列表
     */
    List<Resource> findByTypeInOrderBySortAsc(List<ResourceType> types);
    
    /**
     * 根据父级ID查找子资源
     * @param parentId 父级资源ID
     * @return 子资源列表
     */
    List<Resource> findByParentIdOrderBySortAsc(Long parentId);
    
    /**
     * 查找顶级资源（parentId为null）
     * @return 顶级资源列表
     */
    List<Resource> findByParentIdIsNullOrderBySortAsc();
    
    /**
     * 根据名称查找资源
     * @param name 资源名称
     * @return 资源对象
     */
    Optional<Resource> findByName(String name);
    
    /**
     * 根据URL查找资源
     * @param url 前端路径
     * @return 资源对象
     */
    Optional<Resource> findByUrl(String url);
    
    /**
     * 根据URI查找资源
     * @param uri 后端API路径
     * @return 资源对象
     */
    Optional<Resource> findByUri(String uri);
    
    /**
     * 根据权限标识查找资源
     * @param permission 权限标识
     * @return 资源列表
     */
    List<Resource> findByPermission(String permission);
    
    /**
     * 根据HTTP方法查找资源
     * @param method HTTP方法
     * @return 资源列表
     */
    List<Resource> findByMethod(String method);
    
    /**
     * 根据是否隐藏查找资源
     * @param hidden 是否隐藏
     * @return 资源列表
     */
    List<Resource> findByHiddenOrderBySortAsc(boolean hidden);
    
    /**
     * 根据名称模糊查询资源
     * @param name 资源名称（模糊匹配）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByNameContainingIgnoreCaseOrderBySortAsc(String name, Pageable pageable);
    
    /**
     * 根据标题模糊查询资源
     * @param title 资源标题（模糊匹配）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByTitleContainingIgnoreCaseOrderBySortAsc(String title, Pageable pageable);
    
    /**
     * 根据URL模糊查询资源
     * @param url 前端路径（模糊匹配）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByUrlContainingIgnoreCaseOrderBySortAsc(String url, Pageable pageable);
    
    /**
     * 根据URI模糊查询资源
     * @param uri 后端API路径（模糊匹配）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByUriContainingIgnoreCaseOrderBySortAsc(String uri, Pageable pageable);
    
    /**
     * 根据权限标识模糊查询资源
     * @param permission 权限标识（模糊匹配）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByPermissionContainingIgnoreCaseOrderBySortAsc(String permission, Pageable pageable);
    
    /**
     * 根据资源类型分页查询
     * @param type 资源类型
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByTypeOrderBySortAsc(ResourceType type, Pageable pageable);
    
    /**
     * 根据父级ID分页查询
     * @param parentId 父级资源ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByParentIdOrderBySortAsc(Long parentId, Pageable pageable);
    
    /**
     * 检查是否存在指定名称的资源（排除指定ID）
     * @param name 资源名称
     * @param id 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByNameAndIdNot(String name, Long id);
    
    /**
     * 检查是否存在指定URL的资源（排除指定ID）
     * @param url 前端路径
     * @param id 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByUrlAndIdNot(String url, Long id);
    
    /**
     * 检查是否存在指定URI的资源（排除指定ID）
     * @param uri 后端API路径
     * @param id 要排除的资源ID
     * @return 是否存在
     */
    boolean existsByUriAndIdNot(String uri, Long id);
    
    /**
     * 获取最大排序值
     * @return 最大排序值
     */
    @Query("SELECT COALESCE(MAX(r.sort), 0) FROM Resource r")
    Integer findMaxSort();
    
    /**
     * 根据父级ID获取最大排序值
     * @param parentId 父级资源ID
     * @return 最大排序值
     */
    @Query("SELECT COALESCE(MAX(r.sort), 0) FROM Resource r WHERE r.parentId = :parentId")
    Integer findMaxSortByParentId(@Param("parentId") Long parentId);
    
    /**
     * 根据资源类型获取最大排序值
     * @param type 资源类型
     * @return 最大排序值
     */
    @Query("SELECT COALESCE(MAX(r.sort), 0) FROM Resource r WHERE r.type = :type")
    Integer findMaxSortByType(@Param("type") ResourceType type);
    
    /**
     * 复合查询：根据多个条件查询资源
     * @param name 资源名称（可选）
     * @param url 前端路径（可选）
     * @param uri 后端API路径（可选）
     * @param permission 权限标识（可选）
     * @param title 显示标题（可选）
     * @param type 资源类型（可选）
     * @param parentId 父级资源ID（可选）
     * @param hidden 是否隐藏（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT r FROM Resource r WHERE " +
           //"(:name IS NULL OR r.name LIKE %:name%) AND " +
           //"(:url IS NULL OR r.url LIKE %:url%) AND " +
           //"(:uri IS NULL OR r.uri LIKE %:uri%) AND " +
           //"(:permission IS NULL OR r.permission LIKE %:permission%) AND " +
           //"(:title IS NULL OR r.title LIKE %:title%) AND " +
           //"(:hidden IS NULL OR r.hidden = :hidden) AND " +
            "(:type IS NULL OR r.type = :type) AND " +
           "r.parentId = :parentId " +
           "ORDER BY r.sort ASC")
    Page<Resource> findByConditions(
            @Param("name") String name,
            @Param("url") String url,
            @Param("uri") String uri,
            @Param("permission") String permission,
            @Param("title") String title,
            @Param("type") ResourceType type,
            @Param("parentId") Long parentId,
            @Param("hidden") Boolean hidden,
            Pageable pageable
    );
    
    /**
     * 根据资源类型列表删除资源
     * @param types 资源类型列表
     */
    void deleteByTypeIn(List<ResourceType> types);

    /**
     * 根据类型列表和父级ID分页查询资源（用于菜单分页，type=0/1）
     * @param types 资源类型列表
     * @param parentId 父级ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Resource> findByTypeInAndParentId(List<ResourceType> types, Long parentId, Pageable pageable);

    /**
     * 多条件分页查询菜单（type IN、parentId、title、name、permission、enabled）
     * @param types 菜单类型列表（0/1）
     * @param parentId 父级ID
     * @param title 菜单标题（模糊）
     * @param name 菜单名称（模糊）
     * @param permission 权限标识（模糊）
     * @param enabled 是否启用
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("""
    SELECT r FROM Resource r
    WHERE (:parentId IS NULL OR r.parentId = :parentId)
      AND (COALESCE(:types, NULL) IS NULL OR r.type IN :types)
      AND (:title IS NULL OR r.title LIKE %:title%)
      AND (:name IS NULL OR r.name LIKE %:name%)
      AND (:permission IS NULL OR r.permission LIKE %:permission%)
      AND (:enabled IS NULL OR r.enabled = :enabled)
    ORDER BY r.sort ASC
    """)
    Page<Resource> findMenusByConditions(
        @Param("types") List<ResourceType> types,
        @Param("parentId") Long parentId,
        @Param("title") String title,
        @Param("name") String name,
        @Param("permission") String permission,
        @Param("enabled") Boolean enabled,
        Pageable pageable
    );

    /**
     * 判断是否存在指定父ID的资源（用于判断是否叶子节点）
     * @param parentId 父级资源ID
     * @return 是否存在
     */
    boolean existsByParentId(Long parentId);

    /**
     * 原生SQL方式分页查询菜单，直接返回leaf字段
     */
    @Query(value = """
    SELECT r.id,
           r.name,
           r.title,
           r.url,
           r.icon,
           r.show_icon AS showIcon,
           r.sort,
           r.component,
           r.redirect,
           r.hidden,
           r.keep_alive AS keepAlive,
           r.permission,
           r.type,
           r.parent_id AS parentId,
           NOT EXISTS (
               SELECT 1 FROM resource c WHERE c.parent_id = r.id
           ) AS leaf
    FROM resource r
    WHERE (:parentId IS NULL OR r.parent_id = :parentId)
      AND (:title IS NULL OR r.title LIKE CONCAT('%', :title, '%'))
      AND (:name IS NULL OR r.name LIKE CONCAT('%', :name, '%'))
      AND (:permission IS NULL OR r.permission LIKE CONCAT('%', :permission, '%'))
      AND (:enabled IS NULL OR r.enabled = :enabled)
      AND (:typesSize = 0 OR r.type IN (:types))
    ORDER BY r.sort ASC
    """,
            countQuery = """
    SELECT COUNT(*) FROM resource r
    WHERE (:parentId IS NULL OR r.parent_id = :parentId)
      AND (:title IS NULL OR r.title LIKE CONCAT('%', :title, '%'))
      AND (:name IS NULL OR r.name LIKE CONCAT('%', :name, '%'))
      AND (:permission IS NULL OR r.permission LIKE CONCAT('%', :permission, '%'))
      AND (:enabled IS NULL OR r.enabled = :enabled)
      AND (:typesSize = 0 OR r.type IN (:types))
    """,
            nativeQuery = true
    )
    Page<ResourceProjection> findMenusByNativeSql(
            @Param("types") List<Integer> types,
            @Param("typesSize") int typesSize,
            @Param("parentId") Long parentId,
            @Param("title") String title,
            @Param("name") String name,
            @Param("permission") String permission,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /**
     * JPQL DTO投影方式分页查询菜单，leaf字段用子查询
     */
    @Query("""
    SELECT new com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto(
        r.id, r.name, r.title, r.url, r.icon, r.showIcon, r.sort,
        r.component, r.redirect, r.hidden, r.keepAlive, r.permission,
        r.type, r.parentId,
        (SELECT COUNT(c) FROM Resource c WHERE c.parentId = r.id) = 0
    )
    FROM Resource r
    WHERE (:parentId IS NULL OR r.parentId = :parentId)
      AND (:title IS NULL OR r.title LIKE %:title%)
      AND (:name IS NULL OR r.name LIKE %:name%)
      AND (:permission IS NULL OR r.permission LIKE %:permission%)
      AND (:enabled IS NULL OR r.enabled = :enabled)
      AND (COALESCE(:types, NULL) IS NULL OR r.type IN :types)
    ORDER BY r.sort ASC
""")
    Page<ResourceResponseDto> findMenusByJpqlDto(
            @Param("types") List<Integer> types,
            @Param("parentId") Long parentId,
            @Param("title") String title,
            @Param("name") String name,
            @Param("permission") String permission,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );

    /**
     * 根据类型列表和父级ID查询菜单（用于按层级加载）
     * @param types 资源类型列表
     * @param parentId 父级ID
     * @return 菜单列表
     */
    List<Resource> findByTypeInAndParentIdOrderBySortAsc(List<ResourceType> types, Long parentId);
    
    /**
     * 根据类型列表查询顶级菜单（parentId为null）
     * @param types 资源类型列表
     * @return 顶级菜单列表
     */
    List<Resource> findByTypeInAndParentIdIsNullOrderBySortAsc(List<ResourceType> types);
    
    /**
     * 根据父级ID列表查询资源（用于批量判断叶子节点）
     * @param parentIds 父级ID列表
     * @return 资源列表
     */
    List<Resource> findByParentIdIn(List<Long> parentIds);

}