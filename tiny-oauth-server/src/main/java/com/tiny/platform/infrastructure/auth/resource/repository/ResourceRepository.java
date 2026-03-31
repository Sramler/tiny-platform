package com.tiny.platform.infrastructure.auth.resource.repository;

import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceProjection;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 兼容总表 resource 的数据访问层。
 *
 * <p><b>注意</b>：运行时主线的业务读写已退出对 {@code resource} 表的依赖。
 * 本仓储保留给历史资产（可观测/迁移输入/运营可读字段）与少量兼容查询；
 * 新代码应优先使用 {@code menu/ui_action/api_endpoint} 及其 repository。</p>
 */
@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

    List<Resource> findByTenantIdOrderBySortAscIdAsc(Long tenantId);

    boolean existsByTenantId(Long tenantId);

    /** 平台模板：tenant_id IS NULL，见 §4 平台模板与 default 解耦 */
    List<Resource> findByTenantIdIsNullOrderBySortAscIdAsc();

    @Query(value = """
        SELECT c.id AS id,
               c.tenant_id AS tenantId,
               c.resource_level AS resourceLevel,
               c.name AS name,
               c.url AS url,
               c.uri AS uri,
               c.method AS method,
               c.icon AS icon,
               c.show_icon AS showIcon,
               c.sort AS sort,
               c.component AS component,
               c.redirect AS redirect,
               c.hidden AS hidden,
               c.keep_alive AS keepAlive,
               c.title AS title,
               c.permission AS permission,
               c.required_permission_id AS requiredPermissionId,
               c.type_code AS typeCode,
               c.parent_id AS parentId,
               c.enabled AS enabled
        FROM (
            SELECT m.id,
                   m.tenant_id,
                   CONVERT(m.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(m.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT(m.path USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT(m.icon USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   m.show_icon,
                   m.sort,
                   CONVERT(m.component USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT(m.redirect USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   m.hidden,
                   m.keep_alive,
                   CONVERT(m.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(m.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   m.required_permission_id,
                   m.type AS type_code,
                   m.parent_id,
                   m.enabled
            FROM menu m
            WHERE ((:tenantId IS NULL AND m.tenant_id IS NULL) OR m.tenant_id = :tenantId)
              AND LOWER(m.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT a.id,
                   a.tenant_id,
                   CONVERT(a.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(a.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT(a.page_path USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   FALSE AS show_icon,
                   a.sort,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   FALSE AS hidden,
                   FALSE AS keep_alive,
                   CONVERT(a.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(a.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   a.required_permission_id,
                   2 AS type_code,
                   a.parent_menu_id AS parent_id,
                   a.enabled
            FROM ui_action a
            WHERE ((:tenantId IS NULL AND a.tenant_id IS NULL) OR a.tenant_id = :tenantId)
              AND LOWER(a.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT e.id,
                   e.tenant_id,
                   CONVERT(e.resource_level USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS resource_level,
                   CONVERT(e.name USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS name,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS url,
                   CONVERT(e.uri USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS uri,
                   CONVERT(e.method USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS method,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS icon,
                   FALSE AS show_icon,
                   0 AS sort,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS component,
                   CONVERT('' USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS redirect,
                   FALSE AS hidden,
                   FALSE AS keep_alive,
                   CONVERT(e.title USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS title,
                   CONVERT(e.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   e.required_permission_id,
                   3 AS type_code,
                   NULL AS parent_id,
                   e.enabled
            FROM api_endpoint e
            WHERE ((:tenantId IS NULL AND e.tenant_id IS NULL) OR e.tenant_id = :tenantId)
              AND LOWER(e.resource_level) = LOWER(:resourceLevel)
        ) c
        ORDER BY c.sort ASC, c.id ASC
        """, nativeQuery = true)
    List<CarrierTemplateResourceSnapshotView> findCarrierTemplateSnapshotViewsByScope(
        @Param("tenantId") Long tenantId,
        @Param("resourceLevel") String resourceLevel
    );

    /**
     * 根据资源类型查找资源
     * @param type 资源类型
     * @return 资源列表
     */
    List<Resource> findByTypeOrderBySortAsc(ResourceType type);

    List<Resource> findByTypeAndTenantIdOrderBySortAsc(ResourceType type, Long tenantId);
    
    /**
     * 根据多个资源类型查找资源
     * @param types 资源类型列表
     * @return 资源列表
     */
    List<Resource> findByTypeInOrderBySortAsc(List<ResourceType> types);

    List<Resource> findByTypeInAndTenantIdOrderBySortAsc(List<ResourceType> types, Long tenantId);
    
    /**
     * 根据父级ID查找子资源
     * @param parentId 父级资源ID
     * @return 子资源列表
     */
    List<Resource> findByParentIdOrderBySortAsc(Long parentId);

    List<Resource> findByParentIdAndTenantIdOrderBySortAsc(Long parentId, Long tenantId);
    
    /**
     * 查找顶级资源（parentId为null）
     * @return 顶级资源列表
     */
    List<Resource> findByParentIdIsNullOrderBySortAsc();

    List<Resource> findByParentIdIsNullAndTenantIdOrderBySortAsc(Long tenantId);
    
    /**
     * 根据名称查找资源
     * @param name 资源名称
     * @return 资源对象
     */
    Optional<Resource> findByName(String name);

    Optional<Resource> findByNameAndTenantId(String name, Long tenantId);
    
    /**
     * 根据URL查找资源
     * @param url 前端路径
     * @return 资源对象
     */
    Optional<Resource> findByUrl(String url);

    Optional<Resource> findByUrlAndTenantId(String url, Long tenantId);
    
    /**
     * 根据URI查找资源
     * @param uri 后端API路径
     * @return 资源对象
     */
    Optional<Resource> findByUri(String uri);

    Optional<Resource> findByUriAndTenantId(String uri, Long tenantId);

    Optional<Resource> findByTenantIdAndResourceLevelAndCarrierTypeAndCarrierSourceId(
        Long tenantId,
        String resourceLevel,
        String carrierType,
        Long carrierSourceId
    );

    List<Resource> findByIdInAndTenantId(List<Long> ids, Long tenantId);

    @Query("""
        SELECT
          r.id AS id,
          r.permission AS permission,
          r.requiredPermissionId AS requiredPermissionId
        FROM Resource r
        WHERE r.id IN :ids
          AND ((:tenantId IS NULL AND r.tenantId IS NULL) OR r.tenantId = :tenantId)
          AND LOWER(r.resourceLevel) = LOWER(:resourceLevel)
        """)
    List<RoleResourcePermissionBindingView> findRolePermissionBindingViewsByIdsAndScope(
        @Param("ids") List<Long> ids,
        @Param("tenantId") Long tenantId,
        @Param("resourceLevel") String resourceLevel
    );

    @Query(value = """
        SELECT c.id AS id, c.permission AS permission, c.required_permission_id AS requiredPermissionId
        FROM (
            SELECT m.id,
                   CONVERT(m.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   m.required_permission_id
            FROM menu m
            WHERE m.id IN (:ids)
              AND ((:tenantId IS NULL AND m.tenant_id IS NULL) OR m.tenant_id = :tenantId)
              AND LOWER(m.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT a.id,
                   CONVERT(a.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   a.required_permission_id
            FROM ui_action a
            WHERE a.id IN (:ids)
              AND ((:tenantId IS NULL AND a.tenant_id IS NULL) OR a.tenant_id = :tenantId)
              AND LOWER(a.resource_level) = LOWER(:resourceLevel)
            UNION ALL
            SELECT e.id,
                   CONVERT(e.permission USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS permission,
                   e.required_permission_id
            FROM api_endpoint e
            WHERE e.id IN (:ids)
              AND ((:tenantId IS NULL AND e.tenant_id IS NULL) OR e.tenant_id = :tenantId)
              AND LOWER(e.resource_level) = LOWER(:resourceLevel)
        ) c
        """, nativeQuery = true)
    List<RoleResourcePermissionBindingView> findCarrierPermissionBindingViewsByIdsAndScope(
        @Param("ids") List<Long> ids,
        @Param("tenantId") Long tenantId,
        @Param("resourceLevel") String resourceLevel
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Resource r WHERE r.id = :id AND r.tenantId = :tenantId")
    int deleteByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
    
    /**
     * 根据权限标识查找资源
     * @param permission 权限标识
     * @return 资源列表
     */
    List<Resource> findByPermission(String permission);

    List<Resource> findByPermissionAndTenantId(String permission, Long tenantId);

    boolean existsByRequiredPermissionIdAndTenantId(Long requiredPermissionId, Long tenantId);
    
    /**
     * 根据HTTP方法查找资源
     * @param method HTTP方法
     * @return 资源列表
     */
    List<Resource> findByMethod(String method);

    List<Resource> findByMethodAndTenantId(String method, Long tenantId);
    
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
    @Query("SELECT COALESCE(MAX(r.sort), 0) FROM Resource r WHERE r.parentId = :parentId AND r.tenantId = :tenantId")
    Integer findMaxSortByParentId(@Param("parentId") Long parentId, @Param("tenantId") Long tenantId);
    
    /**
     * 根据资源类型获取最大排序值
     * @param type 资源类型
     * @return 最大排序值
     */
    @Query("SELECT COALESCE(MAX(r.sort), 0) FROM Resource r WHERE r.type = :type AND r.tenantId = :tenantId")
    Integer findMaxSortByType(@Param("type") ResourceType type, @Param("tenantId") Long tenantId);
    
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
    Page<Resource> findByTypeInAndParentIdAndTenantId(List<ResourceType> types, Long parentId, Long tenantId, Pageable pageable);

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
      AND r.tenantId = :tenantId
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
        @Param("tenantId") Long tenantId,
        Pageable pageable
    );

    /**
     * 判断是否存在指定父ID的资源（用于判断是否叶子节点）
     * @param parentId 父级资源ID
     * @return 是否存在
     */
    boolean existsByParentId(Long parentId);

    boolean existsByParentIdAndTenantId(Long parentId, Long tenantId);

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
      AND r.tenant_id = :tenantId
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
      AND r.tenant_id = :tenantId
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
            @Param("tenantId") Long tenantId,
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
      AND r.tenantId = :tenantId
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
            @Param("tenantId") Long tenantId,
            Pageable pageable
    );

    /**
     * 根据类型列表和父级ID查询菜单（用于按层级加载）
     * @param types 资源类型列表
     * @param parentId 父级ID
     * @return 菜单列表
     */
    List<Resource> findByTypeInAndParentIdOrderBySortAsc(List<ResourceType> types, Long parentId);

    List<Resource> findByTypeInAndParentIdAndTenantIdOrderBySortAsc(List<ResourceType> types, Long parentId, Long tenantId);
    
    /**
     * 根据类型列表查询顶级菜单（parentId为null）
     * @param types 资源类型列表
     * @return 顶级菜单列表
     */
    List<Resource> findByTypeInAndParentIdIsNullOrderBySortAsc(List<ResourceType> types);

    List<Resource> findByTypeInAndParentIdIsNullAndTenantIdOrderBySortAsc(List<ResourceType> types, Long tenantId);

    /**
     * 根据父级ID列表查询资源（用于批量判断叶子节点）
     * @param parentIds 父级ID列表
     * @return 资源列表
     */
    List<Resource> findByParentIdIn(List<Long> parentIds);

    List<Resource> findByParentIdInAndTenantId(List<Long> parentIds, Long tenantId);

}
