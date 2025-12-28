package com.tiny.platform.infrastructure.menu.service;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceProjection;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;


/**
 * 菜单业务实现类，专注于 type=0/1 的菜单和目录
 */
@Service
public class MenuServiceImpl implements MenuService {
    private final ResourceRepository resourceRepository;
    
    @PersistenceContext
    private EntityManager entityManager; // 注入 EntityManager 用于原生 Hibernate 查询

    public MenuServiceImpl(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId == 0) {
            return null;
        }
        return parentId;
    }

    /**
     * 分页查询菜单（支持type、parentId、title、enabled多条件）
     */
    @Override
    public Page<ResourceResponseDto> menus(ResourceRequestDto query, Pageable pageable) {
        // 查询 type=0/1（目录和菜单）
        List<ResourceType> types = List.of(ResourceType.DIRECTORY, ResourceType.MENU); // 目录和菜单
        List<Integer> typeCodes = types.stream().map(ResourceType::getCode).toList();
        
        // 原生 Hibernate 方式（推荐使用）
        return findMenusByNativeHibernate(query, typeCodes, pageable);
        
        // 原生SQL方式，直接返回leaf字段（如需切换，取消注释即可）
        // return findMenusByNativeSql(query, typeCodes, pageable);
        
        // JPQL DTO投影方式（如需切换，取消注释即可）
        // return findMenusByJpqlDto(query, typeCodes, pageable);
    }
    
    /**
     * 原生 Hibernate 方式分页查询菜单
     * 使用 EntityManager 和原生 SQL，性能最优
     */
    private Page<ResourceResponseDto> findMenusByNativeHibernate(ResourceRequestDto query, List<Integer> typeCodes, Pageable pageable) {
        // 构建动态 SQL 查询条件
        StringBuilder sqlBuilder = new StringBuilder();
        StringBuilder countSqlBuilder = new StringBuilder();
        
        // 主查询 SQL
        sqlBuilder.append("""
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
                   r.enabled,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM resource c WHERE c.parent_id = r.id
                   ) THEN 0 ELSE 1 END AS leaf
            FROM resource r
            WHERE 1=1
            """);
        
        // 计数查询 SQL
        countSqlBuilder.append("""
            SELECT COUNT(*) FROM resource r WHERE 1=1
            """);
        
        // 动态添加查询条件
        if (query.getParentId() != null) {
            sqlBuilder.append(" AND r.parent_id = :parentId");
            countSqlBuilder.append(" AND r.parent_id = :parentId");
        }
        
        if (StringUtils.hasText(query.getTitle())) {
            sqlBuilder.append(" AND r.title LIKE :title");
            countSqlBuilder.append(" AND r.title LIKE :title");
        }
        
        if (StringUtils.hasText(query.getName())) {
            sqlBuilder.append(" AND r.name LIKE :name");
            countSqlBuilder.append(" AND r.name LIKE :name");
        }
        
        if (StringUtils.hasText(query.getPermission())) {
            sqlBuilder.append(" AND r.permission LIKE :permission");
            countSqlBuilder.append(" AND r.permission LIKE :permission");
        }
        
        if (query.getEnabled() != null) {
            sqlBuilder.append(" AND r.enabled = :enabled");
            countSqlBuilder.append(" AND r.enabled = :enabled");
        }
        
        // 添加类型过滤条件
        sqlBuilder.append(" AND r.type IN (:types)");
        countSqlBuilder.append(" AND r.type IN (:types)");
        
        // 添加排序
        sqlBuilder.append(" ORDER BY r.sort ASC");
        
        // 创建查询对象
        Query sqlQuery = entityManager.createNativeQuery(sqlBuilder.toString());
        Query countQuery = entityManager.createNativeQuery(countSqlBuilder.toString());
        
        // 设置查询参数
        if (query.getParentId() != null) {
            sqlQuery.setParameter("parentId", query.getParentId());
            countQuery.setParameter("parentId", query.getParentId());
        }
        
        if (StringUtils.hasText(query.getTitle())) {
            sqlQuery.setParameter("title", "%" + query.getTitle() + "%");
            countQuery.setParameter("title", "%" + query.getTitle() + "%");
        }
        
        if (StringUtils.hasText(query.getName())) {
            sqlQuery.setParameter("name", "%" + query.getName() + "%");
            countQuery.setParameter("name", "%" + query.getName() + "%");
        }
        
        if (StringUtils.hasText(query.getPermission())) {
            sqlQuery.setParameter("permission", "%" + query.getPermission() + "%");
            countQuery.setParameter("permission", "%" + query.getPermission() + "%");
        }
        
        if (query.getEnabled() != null) {
            sqlQuery.setParameter("enabled", query.getEnabled());
            countQuery.setParameter("enabled", query.getEnabled());
        }
        
        sqlQuery.setParameter("types", typeCodes);
        countQuery.setParameter("types", typeCodes);
        
        // 设置分页参数
        sqlQuery.setFirstResult((int) pageable.getOffset());
        sqlQuery.setMaxResults(pageable.getPageSize());
        
        // 执行查询
        List<Object[]> results = sqlQuery.getResultList();
        Long total = ((Number) countQuery.getSingleResult()).longValue();
        
        // 转换结果
        List<ResourceResponseDto> dtos = results.stream()
            .map(this::mapToResourceResponseDto)
            .collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, total);
    }
    
    /**
     * 将查询结果映射为 ResourceResponseDto
     */
    private ResourceResponseDto mapToResourceResponseDto(Object[] row) {
        ResourceResponseDto dto = new ResourceResponseDto();
        
        // 按查询字段顺序映射（与 SQL SELECT 字段顺序一致）
        dto.setId(((Number) row[0]).longValue()); // id
        dto.setName((String) row[1]); // name
        dto.setTitle((String) row[2]); // title
        dto.setUrl((String) row[3]); // url
        dto.setIcon((String) row[4]); // icon
        dto.setShowIcon(Boolean.TRUE.equals(row[5])); // showIcon
        dto.setSort(safeToInteger(row[6])); // sort - 安全转换为 Integer
        dto.setComponent((String) row[7]); // component
        dto.setRedirect((String) row[8]); // redirect
        dto.setHidden(Boolean.TRUE.equals(row[9])); // hidden
        dto.setKeepAlive(Boolean.TRUE.equals(row[10])); // keepAlive
        dto.setPermission((String) row[11]); // permission
        dto.setType(safeToInteger(row[12])); // type - 安全转换为 Integer
        dto.setParentId(row[13] != null ? ((Number) row[13]).longValue() : null); // parentId
        dto.setEnabled(safeToBoolean(row[14])); // enabled
        dto.setLeaf(safeToBoolean(row[15])); // leaf
        
        return dto;
    }
    
    /**
     * 安全地将对象转换为 Integer
     * 处理 Byte、Short、Integer 等数字类型
     */
    private Integer safeToInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Byte) {
            return ((Byte) value).intValue();
        }
        if (value instanceof Short) {
            return ((Short) value).intValue();
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        // 如果是字符串，尝试解析
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 安全地将对象转换为 Boolean
     * 处理 Long、Integer、Boolean 等类型
     */
    private Boolean safeToBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            // 数字类型：1 表示 true，0 表示 false
            return ((Number) value).longValue() == 1L;
        }
        if (value instanceof String) {
            // 字符串类型：尝试解析为布尔值
            String str = ((String) value).trim().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }
        return false;
    }

    /**
     * 获取菜单树结构（只查 type=0/1）
     */
    @Override
    public List<ResourceResponseDto> menuTree() {
        List<Resource> menus = resourceRepository.findByTypeInOrderBySortAsc(List.of(ResourceType.DIRECTORY, ResourceType.MENU));
        List<ResourceResponseDto> fullTree = buildResourceTree(menus);

        // 1. 获取所有节点的扁平化列表，用于后续查找 redirect 目标
        List<ResourceResponseDto> flatList = new ArrayList<>();
        flattenTree(fullTree, flatList);

        // 2. 从扁平化列表中，筛选出所有可见菜单的 URL，作为有效 redirect 目标集合
        Set<String> visibleUrls = flatList.stream()
                .filter(this::isNodeVisible)
                .map(ResourceResponseDto::getUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 3. 返回前进行过滤，并传入可见 URL 集合
        return filterVisibleMenus(fullTree, visibleUrls);
    }

    /**
     * 返回不带任何过滤的完整菜单树
     * 用于角色授权、菜单管理等后台场景
     */
    @Override
    public List<ResourceResponseDto> menuTreeAll() {
        List<Resource> menus = resourceRepository.findByTypeInOrderBySortAsc(List.of(ResourceType.DIRECTORY, ResourceType.MENU));
        return buildResourceTree(menus);
    }

    /**
     * 将树形结构扁平化为列表
     * @param nodes 节点列表
     * @param flatList 存储扁平化结果的列表
     */
    private void flattenTree(List<ResourceResponseDto> nodes, List<ResourceResponseDto> flatList) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (ResourceResponseDto node : nodes) {
            flatList.add(node);
            if (node.getChildren() != null) {
                flattenTree(node.getChildren(), flatList);
            }
        }
    }

    /**
     * 递归过滤出启用的、未隐藏的菜单项
     * @param nodes 菜单节点列表
     * @param visibleUrls 所有可见菜单的 URL 集合，用于校验 redirect
     * @return 过滤后的菜单节点列表
     */
    private List<ResourceResponseDto> filterVisibleMenus(List<ResourceResponseDto> nodes, Set<String> visibleUrls) {
        if (nodes == null) {
            return new ArrayList<>();
        }

        return nodes.stream()
            .filter(this::isNodeVisible)
            .map(node -> {
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    node.setChildren(filterVisibleMenus(node.getChildren(), visibleUrls));
                }
                return node;
            })
            // 过滤掉没有可访问子菜单的目录，除非它有有效的重定向
            .filter(node -> {
                // 非目录节点直接保留
                if (node.getType() != 0) {
                    return true;
                }

                // 是目录节点，满足以下任一条件则保留：
                // 1. 有可见的子菜单
                boolean hasVisibleChildren = node.getChildren() != null && !node.getChildren().isEmpty();
                // 2. 有一个有效的重定向地址（该地址必须是一个可见菜单的URL）
                boolean hasValidRedirect = StringUtils.hasText(node.getRedirect()) && visibleUrls.contains(node.getRedirect());

                return hasVisibleChildren || hasValidRedirect;
            })
            .collect(Collectors.toList());
    }

    private boolean isNodeVisible(ResourceResponseDto node) {
        boolean enabled = !Boolean.FALSE.equals(node.getEnabled());
        boolean hidden = Boolean.TRUE.equals(node.getHidden());
        return enabled && !hidden;
    }

    /**
     * 根据父级ID查询子菜单（只查 type=0/1）
     */
    @Override
    public List<ResourceResponseDto> getMenusByParentId(Long parentId) {
        List<Resource> menus;
        if (parentId == null || parentId == 0) {
            // 查询顶级菜单（parentId为null或0）
            menus = resourceRepository.findByTypeInAndParentIdIsNullOrderBySortAsc(List.of(ResourceType.DIRECTORY, ResourceType.MENU));
        } else {
            // 查询指定父级ID的子菜单
            menus = resourceRepository.findByTypeInAndParentIdOrderBySortAsc(List.of(ResourceType.DIRECTORY, ResourceType.MENU), parentId);
        }
        return menus.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 创建菜单
     */
    @Override
    public Resource createMenu(ResourceCreateUpdateDto resourceDto) {
        // 只允许 type=0/1
        if (resourceDto.getType() == null || (resourceDto.getType() != ResourceType.DIRECTORY.getCode() && resourceDto.getType() != ResourceType.MENU.getCode())) {
            resourceDto.setType(ResourceType.MENU.getCode());
        }
        
        // 验证父ID设置（创建时ID为null，所以传入0作为占位符）
        resourceDto.setParentId(normalizeParentId(resourceDto.getParentId()));
        validateParentId(0L, resourceDto.getParentId());
        
        Resource resource = new Resource();
        resource.setName(resourceDto.getName());
        resource.setTitle(resourceDto.getTitle());
        resource.setUrl(resourceDto.getUrl());
        resource.setUri(StringUtils.hasText(resourceDto.getUri()) ? resourceDto.getUri() : "");
        resource.setMethod(StringUtils.hasText(resourceDto.getMethod()) ? resourceDto.getMethod() : "");
        resource.setIcon(resourceDto.getIcon());
        resource.setShowIcon(resourceDto.isShowIcon());
        resource.setSort(resourceDto.getSort());
        resource.setComponent(resourceDto.getComponent());
        resource.setRedirect(resourceDto.getRedirect());
        resource.setHidden(resourceDto.isHidden());
        resource.setKeepAlive(resourceDto.isKeepAlive());
        resource.setPermission(resourceDto.getPermission());
        resource.setType(ResourceType.fromCode(resourceDto.getType()));
        resource.setParentId(resourceDto.getParentId());
        return resourceRepository.save(resource);
    }

    /**
     * 更新菜单
     */
    @Override
    public Resource updateMenu(ResourceCreateUpdateDto resourceDto) {
        Resource resource = resourceRepository.findById(resourceDto.getId())
                .orElseThrow(() -> new RuntimeException("菜单不存在"));
        
        // 验证父ID设置
        resourceDto.setParentId(normalizeParentId(resourceDto.getParentId()));
        validateParentId(resourceDto.getId(), resourceDto.getParentId());
        
        resource.setName(resourceDto.getName());
        resource.setTitle(resourceDto.getTitle());
        resource.setUrl(resourceDto.getUrl());
        resource.setUri(StringUtils.hasText(resourceDto.getUri()) ? resourceDto.getUri() : "");
        resource.setMethod(StringUtils.hasText(resourceDto.getMethod()) ? resourceDto.getMethod() : "");
        resource.setIcon(resourceDto.getIcon());
        resource.setShowIcon(resourceDto.isShowIcon());
        resource.setSort(resourceDto.getSort());
        resource.setComponent(resourceDto.getComponent());
        resource.setRedirect(resourceDto.getRedirect());
        resource.setHidden(resourceDto.isHidden());
        resource.setKeepAlive(resourceDto.isKeepAlive());
        resource.setPermission(resourceDto.getPermission());
        resource.setType(ResourceType.fromCode(resourceDto.getType()));
        resource.setParentId(resourceDto.getParentId());
        return resourceRepository.save(resource);
    }
    
    /**
     * 验证父ID设置是否有效
     */
    private void validateParentId(Long menuId, Long parentId) {
        Long normalizedParentId = normalizeParentId(parentId);
        // 如果父ID为空，表示设置为顶级菜单，这是允许的
        if (normalizedParentId == null) {
            return;
        }
        
        // 不能将自己设置为自己的父菜单
        if (menuId != null && menuId.equals(normalizedParentId)) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                "不能将自己设置为父菜单"
            );
        }
        
        // 检查父菜单是否存在
        Resource parentResource = resourceRepository.findById(normalizedParentId)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "父菜单不存在，ID: " + normalizedParentId
                ));
        
        // 父菜单必须是目录类型
        if (parentResource.getType() != ResourceType.DIRECTORY) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "父菜单必须是目录类型，当前类型: " + parentResource.getType()
            );
        }
        
        // 检查是否会造成循环引用
        if (wouldCreateCircularReference(menuId, normalizedParentId)) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                "设置此父菜单会造成循环引用"
            );
        }
    }
    
    /**
     * 检查是否会造成循环引用
     */
    private boolean wouldCreateCircularReference(Long menuId, Long parentId) {
        Long currentParentId = parentId;
        Set<Long> visitedIds = new HashSet<>();
        
        while (currentParentId != null && currentParentId != 0) {
            // 如果已经访问过这个ID，说明存在循环
            if (visitedIds.contains(currentParentId)) {
                return true;
            }
            
            // 如果找到了要修改的菜单ID，说明会造成循环
            if (currentParentId.equals(menuId)) {
                return true;
            }
            
            visitedIds.add(currentParentId);
            
            // 获取当前父菜单的父ID
            Resource currentParent = resourceRepository.findById(currentParentId).orElse(null);
            if (currentParent == null) {
                break;
            }
            currentParentId = currentParent.getParentId();
        }
        
        return false;
    }

    /**
     * 删除菜单
     * 如果菜单有子菜单，会先删除所有子菜单（级联删除）
     * 同时会删除角色与资源的关联关系
     */
    @Override
    @Transactional
    public void deleteMenu(Long id) {
        // 检查菜单是否存在
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "菜单不存在: " + id));
        
        // 递归删除所有子菜单
        deleteMenuRecursive(id);
        
        // 删除当前菜单（会先删除角色关联）
        deleteResourceWithRoleAssociations(id);
    }
    
    /**
     * 递归删除菜单及其所有子菜单
     */
    private void deleteMenuRecursive(Long parentId) {
        // 查找所有子菜单（包括目录和菜单类型）
        List<Resource> children = resourceRepository.findByParentIdOrderBySortAsc(parentId);
        
        // 递归删除每个子菜单
        for (Resource child : children) {
            deleteMenuRecursive(child.getId());
            deleteResourceWithRoleAssociations(child.getId());
        }
    }
    
    /**
     * 删除资源及其与角色的关联关系
     * 先删除 role_resource 表中的关联记录，再删除资源本身
     */
    private void deleteResourceWithRoleAssociations(Long resourceId) {
        // 先删除 role_resource 表中的关联记录
        Query deleteRoleResourceQuery = entityManager.createNativeQuery(
                "DELETE FROM role_resource WHERE resource_id = :resourceId");
        deleteRoleResourceQuery.setParameter("resourceId", resourceId);
        deleteRoleResourceQuery.executeUpdate();
        
        // 然后删除资源本身
        resourceRepository.deleteById(resourceId);
    }

    /**
     * 批量删除菜单
     * 如果菜单有子菜单，会先删除所有子菜单（级联删除）
     */
    @Override
    @Transactional
    public void batchDeleteMenus(List<Long> ids) {
        // 对每个菜单执行级联删除
        for (Long id : ids) {
            deleteMenu(id);
        }
    }

    /**
     * 构建菜单树结构
     */
    private List<ResourceResponseDto> buildResourceTree(List<Resource> resources) {
        try {
            // 构建ID到资源的映射
            Map<Long, ResourceResponseDto> resourceMap = new HashMap<>();
            List<ResourceResponseDto> rootResources = new ArrayList<>();
            
            // 转换为DTO并建立映射
            for (Resource resource : resources) {
                ResourceResponseDto dto = toDto(resource);
                resourceMap.put(resource.getId(), dto);
            }
            
            // 构建树形结构
            for (Resource resource : resources) {
                ResourceResponseDto dto = resourceMap.get(resource.getId());
                Long parentId = normalizeParentId(resource.getParentId());
                if (parentId == null) {
                    // 顶级资源
                    rootResources.add(dto);
                } else {
                    // 子资源
                    ResourceResponseDto parent = resourceMap.get(parentId);
                    if (parent != null) {
                        if (parent.getChildren() == null) {
                            parent.setChildren(new ArrayList<>());
                        }
                        parent.getChildren().add(dto);
                    }
                }
            }
            
            return rootResources;
        } catch (Exception e) {
            // 如果构建树形结构失败，返回平铺列表
            return resources.stream().map(this::toDto).collect(Collectors.toList());
        }
    }

    /**
     * 实体转DTO
     */
    private ResourceResponseDto toDto(Resource resource) {
        try {
            if (resource == null) {
                return null;
            }
            
            ResourceResponseDto dto = new ResourceResponseDto();
            dto.setId(resource.getId());
            dto.setName(resource.getName());
            dto.setTitle(resource.getTitle());
            dto.setUrl(resource.getUrl());
            dto.setUri(resource.getUri());
            dto.setMethod(resource.getMethod());
            dto.setIcon(resource.getIcon());
            dto.setShowIcon(Boolean.TRUE.equals(resource.getShowIcon()));
            dto.setSort(resource.getSort());
            dto.setComponent(resource.getComponent());
            dto.setRedirect(resource.getRedirect());
            dto.setHidden(Boolean.TRUE.equals(resource.getHidden()));
            dto.setKeepAlive(Boolean.TRUE.equals(resource.getKeepAlive()));
            dto.setPermission(resource.getPermission());
            dto.setType(resource.getType() != null ? resource.getType().getCode() : null);
            dto.setTypeName(resource.getType() != null ? resource.getType().getDescription() : null);
            dto.setParentId(normalizeParentId(resource.getParentId()));
            dto.setEnabled(Boolean.TRUE.equals(resource.getEnabled()));
            
            // 判断是否为叶子节点（没有子资源）
            Boolean isLeaf = !resourceRepository.existsByParentId(resource.getId());
            dto.setLeaf(Boolean.TRUE.equals(isLeaf));
            
            // 初始化children为空集合，避免null指针异常
            dto.setChildren(new ArrayList<>());
            
            return dto;
        } catch (Exception e) {
            // 如果转换失败，返回一个基本的DTO，并确保空指针安全
            ResourceResponseDto dto = new ResourceResponseDto();
            if (resource != null) {
                dto.setId(resource.getId());
                dto.setName(resource.getName());
                dto.setTitle(resource.getTitle());
                dto.setType(resource.getType() != null ? resource.getType().getCode() : null);
                dto.setParentId(resource.getParentId());
            }
            dto.setEnabled(false);
            dto.setLeaf(true);
            dto.setChildren(new ArrayList<>());
            return dto;
        }
    }

    /**
     * 原生 SQL 方式分页查询菜单
     * 使用 Repository 的 @Query 注解，直接返回 leaf 字段
     */
    @SuppressWarnings("unused") // retained for potential native SQL path
    private Page<ResourceResponseDto> findMenusByNativeSql(ResourceRequestDto query, List<Integer> typeCodes, Pageable pageable) {
        Page<ResourceProjection> page = resourceRepository.findMenusByNativeSql(
            typeCodes,
            typeCodes.size(),
            query.getParentId(),
            StringUtils.hasText(query.getTitle()) ? query.getTitle() : null,
            StringUtils.hasText(query.getName()) ? query.getName() : null,
            StringUtils.hasText(query.getPermission()) ? query.getPermission() : null,
            query.getEnabled(),
            pageable
        );
        
        return page.map(proj -> {
            ResourceResponseDto dto = new ResourceResponseDto();
            dto.setId(proj.getId());
            dto.setName(proj.getName());
            dto.setTitle(proj.getTitle());
            dto.setUrl(proj.getUrl());
            dto.setIcon(proj.getIcon());
            dto.setShowIcon(Boolean.TRUE.equals(proj.getShowIcon()));
            dto.setSort(safeToInteger(proj.getSort()));
            dto.setComponent(proj.getComponent());
            dto.setRedirect(proj.getRedirect());
            dto.setHidden(Boolean.TRUE.equals(proj.getHidden()));
            dto.setKeepAlive(Boolean.TRUE.equals(proj.getKeepAlive()));
            dto.setPermission(proj.getPermission());
            dto.setType(safeToInteger(proj.getType()));
            dto.setParentId(proj.getParentId());
            dto.setLeaf(proj.getLeaf() != null && proj.getLeaf() == 1);
            return dto;
        });
    }
    
    /**
     * JPQL DTO 投影方式分页查询菜单
     * 使用 Repository 的 JPQL 查询，直接返回 DTO 对象
     */
    @SuppressWarnings("unused") // kept for future switch to JPQL projection query
    private Page<ResourceResponseDto> findMenusByJpqlDto(ResourceRequestDto query, List<Integer> typeCodes, Pageable pageable) {
        return resourceRepository.findMenusByJpqlDto(
            typeCodes,
            query.getParentId(),
            StringUtils.hasText(query.getTitle()) ? query.getTitle() : null,
            StringUtils.hasText(query.getName()) ? query.getName() : null,
            StringUtils.hasText(query.getPermission()) ? query.getPermission() : null,
            query.getEnabled(),
            pageable
        );
    }

    /**
     * 按条件查询菜单（type=0/1），返回list结构
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ResourceResponseDto> list(ResourceRequestDto query) {
        // 查询 type=0/1（目录和菜单）
        List<ResourceType> types = List.of(ResourceType.DIRECTORY, ResourceType.MENU); // 目录和菜单
        List<Integer> typeCodes = types.stream().map(ResourceType::getCode).toList();
        StringBuilder sqlBuilder = new StringBuilder();

        sqlBuilder.append("""
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
               r.enabled,
               CASE WHEN EXISTS (
                   SELECT 1 FROM resource c WHERE c.parent_id = r.id
               ) THEN 0 ELSE 1 END AS leaf
        FROM resource r
        WHERE 1=1
        """);

        // 动态条件拼接
        if (query.getParentId() != null) {
            sqlBuilder.append(" AND r.parent_id = :parentId");
        } else {
            sqlBuilder.append(" AND r.parent_id IS NULL");
        }
        if (StringUtils.hasText(query.getTitle())) {
            sqlBuilder.append(" AND r.title LIKE :title");
        }
        if (StringUtils.hasText(query.getName())) {
            sqlBuilder.append(" AND r.name LIKE :name");
        }
        if (StringUtils.hasText(query.getPermission())) {
            sqlBuilder.append(" AND r.permission LIKE :permission");
        }
        if (query.getEnabled() != null) {
            sqlBuilder.append(" AND r.enabled = :enabled");
        }

        sqlBuilder.append(" AND r.type IN (:types)");
        sqlBuilder.append(" ORDER BY r.sort ASC");

        Query sqlQuery = entityManager.createNativeQuery(sqlBuilder.toString());

        // 设置参数
        if (query.getParentId() != null) {
            sqlQuery.setParameter("parentId", query.getParentId());
        }
        if (StringUtils.hasText(query.getTitle())) {
            sqlQuery.setParameter("title", "%" + query.getTitle() + "%");
        }
        if (StringUtils.hasText(query.getName())) {
            sqlQuery.setParameter("name", "%" + query.getName() + "%");
        }
        if (StringUtils.hasText(query.getPermission())) {
            sqlQuery.setParameter("permission", "%" + query.getPermission() + "%");
        }
        if (query.getEnabled() != null) {
            sqlQuery.setParameter("enabled", query.getEnabled());
        }
        sqlQuery.setParameter("types", typeCodes);

        List<Object[]> results = sqlQuery.getResultList();
        return results.stream()
                .map(this::mapToResourceResponseDto)
                .collect(Collectors.toList());
    }
} 