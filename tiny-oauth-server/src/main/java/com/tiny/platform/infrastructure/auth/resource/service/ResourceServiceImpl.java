package com.tiny.platform.infrastructure.auth.resource.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 资源服务实现类。
 * <p>租户范围以 {@link TenantContext#getActiveTenantId()} 为准，不以 user.tenant_id 为依据。
 */
@Service
public class ResourceServiceImpl implements ResourceService {

    private static final String ACTIVE = "ACTIVE";
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);
    private static final String RESOURCE_LEVEL_PLATFORM = "PLATFORM";
    private static final String RESOURCE_LEVEL_TENANT = "TENANT";
    private static final String CARRIER_MENU = "MENU";
    private static final String CARRIER_UI_ACTION = "UI_ACTION";
    private static final String CARRIER_API_ENDPOINT = "API_ENDPOINT";

    private final RoleRepository roleRepository;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final MenuEntryRepository menuEntryRepository;
    private final UiActionEntryRepository uiActionEntryRepository;
    private final ApiEndpointEntryRepository apiEndpointEntryRepository;
    private final ResourcePermissionBindingService resourcePermissionBindingService;
    private final CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService;
    private final CarrierPermissionRequirementEvaluator carrierPermissionRequirementEvaluator;
    private final AuthorizationAuditService authorizationAuditService;

    @Autowired
    public ResourceServiceImpl(RoleRepository roleRepository,
                               EffectiveRoleResolutionService effectiveRoleResolutionService,
                               TenantUserRepository tenantUserRepository,
                               UserUnitRepository userUnitRepository,
                               MenuEntryRepository menuEntryRepository,
                               UiActionEntryRepository uiActionEntryRepository,
                               ApiEndpointEntryRepository apiEndpointEntryRepository,
                               ResourcePermissionBindingService resourcePermissionBindingService,
                               CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService,
                               CarrierPermissionRequirementEvaluator carrierPermissionRequirementEvaluator,
                               AuthorizationAuditService authorizationAuditService) {
        this.roleRepository = roleRepository;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.uiActionEntryRepository = uiActionEntryRepository;
        this.apiEndpointEntryRepository = apiEndpointEntryRepository;
        this.resourcePermissionBindingService = resourcePermissionBindingService;
        this.carrierPermissionReferenceSafetyService = carrierPermissionReferenceSafetyService;
        this.carrierPermissionRequirementEvaluator = carrierPermissionRequirementEvaluator;
        this.authorizationAuditService = authorizationAuditService;
    }

    @Override
    @DataScope(module = "resource")
    public Page<ResourceResponseDto> resources(ResourceRequestDto query, Pageable pageable) {
        Long tenantId = currentManagedTenantId();
        LinkedHashSet<Long> visibleCreatorIds = tenantId == null
            ? new LinkedHashSet<>()
            : resolveVisibleCreatorIdsForRead(tenantId);
        boolean applyDataScopeFilter = tenantId != null && requiresDataScopeFilter();

        if (query.getType() != null) {
            ResourceType requestedType = ResourceType.fromCode(query.getType());
            if (requestedType == ResourceType.BUTTON) {
                if (applyDataScopeFilter && visibleCreatorIds.isEmpty()) {
                    return Page.empty(pageable);
                }
                return uiActionEntryRepository.findAll(
                    buildUiActionSpecification(query, tenantId, visibleCreatorIds, applyDataScopeFilter),
                    normalizeUiActionPageable(pageable)
                ).map(this::toDto);
            }
            if (requestedType == ResourceType.API) {
                if (applyDataScopeFilter && visibleCreatorIds.isEmpty()) {
                    return Page.empty(pageable);
                }
                return apiEndpointEntryRepository.findAll(
                    buildApiEndpointSpecification(query, tenantId, visibleCreatorIds, applyDataScopeFilter),
                    normalizeApiEndpointPageable(pageable)
                ).map(this::toDto);
            }
        }

        List<Resource> resources = loadManagedControlPlaneResources(
            query,
            tenantId,
            visibleCreatorIds,
            applyDataScopeFilter,
            pageable == null ? Sort.unsorted() : pageable.getSort()
        );
        return toResourceDtoPage(resources, pageable);
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> buildMenuSpecification(ResourceRequestDto query,
                                                                                                          Long tenantId,
                                                                                                          LinkedHashSet<Long> visibleCreatorIds,
                                                                                                          boolean applyDataScopeFilter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, currentResourceLevel());
            if (applyDataScopeFilter && !visibleCreatorIds.isEmpty()) {
                predicates.add(root.get("createdBy").in(visibleCreatorIds));
            } else if (applyDataScopeFilter) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getType() != null) {
                ResourceType requestedType = ResourceType.fromCode(query.getType());
                if (requestedType == ResourceType.DIRECTORY || requestedType == ResourceType.MENU) {
                    predicates.add(criteriaBuilder.equal(root.get("type"), requestedType.getCode()));
                }
            } else {
                predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            }

            if (StringUtils.hasText(query.getName())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    containsIgnoreCase(query.getName())
                ));
            }
            if (StringUtils.hasText(query.getTitle())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("title")),
                    containsIgnoreCase(query.getTitle())
                ));
            }
            if (StringUtils.hasText(query.getUrl())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("path")),
                    containsIgnoreCase(query.getUrl())
                ));
            }
            if (StringUtils.hasText(query.getUri())) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getParentId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("parentId"), query.getParentId()));
            }
            if (query.getHidden() != null) {
                predicates.add(criteriaBuilder.equal(root.get("hidden"), query.getHidden()));
            }
            if (query.getEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), query.getEnabled()));
            }

            criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get("sort")),
                criteriaBuilder.asc(root.get("id"))
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> buildUiActionSpecification(ResourceRequestDto query,
                                                                    Long tenantId,
                                                                    LinkedHashSet<Long> visibleCreatorIds,
                                                                    boolean applyDataScopeFilter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId == null) {
                predicates.add(criteriaBuilder.isNull(root.get("tenantId")));
            } else {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
            }
            predicates.add(criteriaBuilder.equal(root.get("resourceLevel"), currentResourceLevel()));
            if (applyDataScopeFilter && !visibleCreatorIds.isEmpty()) {
                predicates.add(root.get("createdBy").in(visibleCreatorIds));
            } else if (applyDataScopeFilter) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (StringUtils.hasText(query.getName())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), containsIgnoreCase(query.getName())));
            }
            if (StringUtils.hasText(query.getTitle())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), containsIgnoreCase(query.getTitle())));
            }
            if (StringUtils.hasText(query.getUrl())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("pagePath")), containsIgnoreCase(query.getUrl())));
            }
            if (StringUtils.hasText(query.getUri())) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getParentId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("parentMenuId"), query.getParentId()));
            }
            if (query.getHidden() != null && Boolean.TRUE.equals(query.getHidden())) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), query.getEnabled()));
            }
            criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get("sort")),
                criteriaBuilder.asc(root.get("id"))
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ApiEndpointEntry> buildApiEndpointSpecification(ResourceRequestDto query,
                                                                          Long tenantId,
                                                                          LinkedHashSet<Long> visibleCreatorIds,
                                                                          boolean applyDataScopeFilter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId == null) {
                predicates.add(criteriaBuilder.isNull(root.get("tenantId")));
            } else {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
            }
            predicates.add(criteriaBuilder.equal(root.get("resourceLevel"), currentResourceLevel()));
            if (applyDataScopeFilter && !visibleCreatorIds.isEmpty()) {
                predicates.add(root.get("createdBy").in(visibleCreatorIds));
            } else if (applyDataScopeFilter) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (StringUtils.hasText(query.getName())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), containsIgnoreCase(query.getName())));
            }
            if (StringUtils.hasText(query.getTitle())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), containsIgnoreCase(query.getTitle())));
            }
            if (StringUtils.hasText(query.getUrl())) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (StringUtils.hasText(query.getUri())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("uri")), containsIgnoreCase(query.getUri())));
            }
            if (query.getParentId() != null) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getHidden() != null && Boolean.TRUE.equals(query.getHidden())) {
                predicates.add(criteriaBuilder.disjunction());
            }
            if (query.getEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), query.getEnabled()));
            }
            criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get("name")),
                criteriaBuilder.asc(root.get("id"))
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Pageable normalizeUiActionPageable(Pageable pageable) {
        return normalizeCarrierPageable(
            pageable,
            Set.of("id", "name", "title", "permission", "sort", "enabled", "createdAt", "updatedAt"),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        );
    }

    private Pageable normalizeApiEndpointPageable(Pageable pageable) {
        return normalizeCarrierPageable(
            pageable,
            Set.of("id", "name", "title", "uri", "method", "permission", "enabled", "createdAt", "updatedAt"),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        );
    }

    private Pageable normalizeCarrierPageable(Pageable pageable, Set<String> allowedProperties, Sort fallbackSort) {
        if (pageable == null || pageable.isUnpaged()) {
            return pageable;
        }
        List<Sort.Order> allowedOrders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            if (allowedProperties.contains(order.getProperty())) {
                allowedOrders.add(order);
            }
        });
        Sort normalizedSort = allowedOrders.isEmpty() ? fallbackSort : Sort.by(allowedOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), normalizedSort);
    }

    private String containsIgnoreCase(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private List<Resource> loadManagedControlPlaneResources(ResourceRequestDto query,
                                                            Long tenantId,
                                                            LinkedHashSet<Long> visibleCreatorIds,
                                                            boolean applyDataScopeFilter,
                                                            Sort requestedSort) {
        List<Resource> resources = new ArrayList<>();
        ResourceType requestedType = query.getType() == null ? null : ResourceType.fromCode(query.getType());

        if (requestedType == null || requestedType == ResourceType.DIRECTORY || requestedType == ResourceType.MENU) {
            resources.addAll(menuEntryRepository.findAll(
                buildMenuSpecification(query, tenantId, visibleCreatorIds, applyDataScopeFilter),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList());
        }
        if (requestedType == null || requestedType == ResourceType.BUTTON) {
            if (!(applyDataScopeFilter && visibleCreatorIds.isEmpty())) {
                resources.addAll(uiActionEntryRepository.findAll(
                    buildUiActionSpecification(query, tenantId, visibleCreatorIds, applyDataScopeFilter),
                    Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
                ).stream().map(this::toResource).toList());
            }
        }
        if (requestedType == null || requestedType == ResourceType.API) {
            if (!(applyDataScopeFilter && visibleCreatorIds.isEmpty())) {
                resources.addAll(apiEndpointEntryRepository.findAll(
                    buildApiEndpointSpecification(query, tenantId, visibleCreatorIds, applyDataScopeFilter),
                    Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
                ).stream().map(this::toResource).toList());
            }
        }

        resources.sort(resourceSortComparator(requestedSort));
        return resources;
    }

    private Page<ResourceResponseDto> toResourceDtoPage(List<Resource> resources, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            List<ResourceResponseDto> content = resources.stream().map(this::toDto).toList();
            return new org.springframework.data.domain.PageImpl<>(content);
        }
        int start = Math.min((int) pageable.getOffset(), resources.size());
        int end = Math.min(start + pageable.getPageSize(), resources.size());
        List<ResourceResponseDto> content = resources.subList(start, end).stream()
            .map(this::toDto)
            .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, resources.size());
    }

    private Comparator<Resource> resourceSortComparator(Sort requestedSort) {
        List<Sort.Order> orders = new ArrayList<>();
        if (requestedSort != null) {
            requestedSort.forEach(orders::add);
        }
        if (orders.isEmpty()) {
            orders = List.of(Sort.Order.asc("sort"), Sort.Order.asc("id"));
        }

        Comparator<Resource> comparator = null;
        for (Sort.Order order : orders) {
            Comparator<Resource> propertyComparator = Comparator.comparing(
                resource -> comparableResourceProperty(resource, order.getProperty()),
                Comparator.nullsLast(this::compareComparableValues)
            );
            if (order.isDescending()) {
                propertyComparator = propertyComparator.reversed();
            }
            comparator = comparator == null ? propertyComparator : comparator.thenComparing(propertyComparator);
        }

        Comparator<Resource> fallback = Comparator
            .comparing(Resource::getSort, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(Resource::getId, Comparator.nullsLast(Long::compareTo));
        return comparator == null ? fallback : comparator.thenComparing(fallback);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareComparableValues(Comparable left, Comparable right) {
        return left.compareTo(right);
    }

    private Comparable<?> comparableResourceProperty(Resource resource, String property) {
        return switch (property) {
            case "id" -> resource.getId();
            case "name" -> normalizeComparableString(resource.getName());
            case "title" -> normalizeComparableString(resource.getTitle());
            case "url" -> normalizeComparableString(resource.getUrl());
            case "uri" -> normalizeComparableString(resource.getUri());
            case "method" -> normalizeComparableString(resource.getMethod());
            case "permission" -> normalizeComparableString(resource.getPermission());
            case "sort" -> resource.getSort();
            case "enabled" -> resource.getEnabled();
            case "createdAt" -> resource.getCreatedAt();
            case "updatedAt" -> resource.getUpdatedAt();
            default -> resource.getSort();
        };
    }

    private String normalizeComparableString(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<Resource> findById(Long id) {
        return findManagedControlPlaneResource(id);
    }

    @Override
    public Optional<ResourceResponseDto> findDetailById(Long id) {
        return findById(id).map(this::toDto);
    }

    @Override
    public Resource create(Resource resource) {
        resource.setTenantId(currentManagedTenantId());
        resource.setResourceLevel(currentResourceLevel());
        Long actorUserId = extractCurrentUserId();
        resource.setCreatedBy(actorUserId);
        resourcePermissionBindingService.bindResource(resource, actorUserId);
        // fail-closed: permission provided but required_permission_id could not be resolved
        if (StringUtils.hasText(resource.getPermission()) && resource.getRequiredPermissionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required_permission_id for provided permission");
        }
        return saveCarrierEntry(resource);
    }

    @Override
    public Resource update(Long id, Resource resource) {
        Resource existingResource = findManagedControlPlaneResource(id)
                .orElseThrow(() -> new RuntimeException("资源不存在"));
        
        // 更新字段
        existingResource.setName(resource.getName());
        existingResource.setTitle(resource.getTitle());
        existingResource.setUrl(resource.getUrl());
        existingResource.setUri(resource.getUri());
        existingResource.setMethod(resource.getMethod());
        existingResource.setIcon(resource.getIcon());
        existingResource.setShowIcon(resource.getShowIcon());
        existingResource.setSort(resource.getSort());
        existingResource.setComponent(resource.getComponent());
        existingResource.setRedirect(resource.getRedirect());
        existingResource.setHidden(resource.getHidden());
        existingResource.setKeepAlive(resource.getKeepAlive());
        existingResource.setPermission(resource.getPermission());
        existingResource.setType(resource.getType());
        existingResource.setParentId(resource.getParentId());
        resourcePermissionBindingService.bindResource(existingResource, extractCurrentUserId());
        // fail-closed: permission provided but required_permission_id could not be resolved
        if (StringUtils.hasText(existingResource.getPermission()) && existingResource.getRequiredPermissionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required_permission_id for provided permission");
        }

        return saveCarrierEntry(existingResource);
    }

    @Override
    public Resource createFromDto(ResourceCreateUpdateDto resourceDto) {
        Long tenantId = currentManagedTenantId();
        validateResourceByType(resourceDto);

        // 检查名称是否已存在
        if (findByName(resourceDto.getName()).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源名称已存在: " + resourceDto.getName()
            );
        }
        
        // 检查URL是否已存在（如果提供了URL）
        if (StringUtils.hasText(resourceDto.getUrl()) &&
            findByUrl(resourceDto.getUrl()).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源URL已存在: " + resourceDto.getUrl()
            );
        }
        
        // 检查URI是否已存在（如果提供了URI）
        if (StringUtils.hasText(resourceDto.getUri()) &&
            findByUri(resourceDto.getUri()).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源URI已存在: " + resourceDto.getUri()
            );
        }
        
        // 创建资源对象
        Resource resource = new Resource();
        resource.setTenantId(tenantId);
        resource.setResourceLevel(currentResourceLevel());
        resource.setCreatedBy(extractCurrentUserId());
        resource.setName(resourceDto.getName());
        resource.setTitle(resourceDto.getTitle());
        resource.setUrl(resourceDto.getUrl());
        
        // 设置 uri 和 method 的默认值
        String uri = resourceDto.getUri();
        if (uri == null || uri.trim().isEmpty()) {
            uri = ""; // 设置默认值为空字符串
        }
        resource.setUri(uri);
        
        String method = resourceDto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            method = ""; // 设置默认值为空字符串
        }
        resource.setMethod(method);
        
        resource.setIcon(resourceDto.getIcon());
        resource.setShowIcon(resourceDto.isShowIcon());
        resource.setSort(resourceDto.getSort());
        resource.setComponent(resourceDto.getComponent());
        resource.setRedirect(resourceDto.getRedirect());
        resource.setHidden(resourceDto.isHidden());
        resource.setKeepAlive(resourceDto.isKeepAlive());
        resource.setPermission(resourceDto.getPermission());
        resource.setRequiredPermissionId(resourceDto.getRequiredPermissionId());
        resource.setType(ResourceType.fromCode(resourceDto.getType()));
        resource.setParentId(resourceDto.getParentId());
        resourcePermissionBindingService.bindResource(resource, resource.getCreatedBy());
        // fail-closed: permission provided but required_permission_id could not be resolved
        if (StringUtils.hasText(resource.getPermission()) && resource.getRequiredPermissionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required_permission_id for provided permission");
        }

        return saveCarrierEntry(resource);
    }

    @Override
    public Resource updateFromDto(ResourceCreateUpdateDto resourceDto) {
        validateResourceByType(resourceDto);

        Resource existingResource = findManagedControlPlaneResource(resourceDto.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "资源不存在: " + resourceDto.getId()
                ));
        
        // 检查名称是否已被其他资源使用
        Optional<Resource> resourceWithSameName = findByName(resourceDto.getName());
        if (resourceWithSameName.isPresent() && !resourceWithSameName.get().getId().equals(resourceDto.getId())) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "资源名称已被其他资源使用: " + resourceDto.getName()
            );
        }
        
        // 检查URL是否已被其他资源使用（如果提供了URL）
        if (StringUtils.hasText(resourceDto.getUrl())) {
            Optional<Resource> resourceWithSameUrl = findByUrl(resourceDto.getUrl());
            if (resourceWithSameUrl.isPresent() && !resourceWithSameUrl.get().getId().equals(resourceDto.getId())) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "资源URL已被其他资源使用: " + resourceDto.getUrl()
                );
            }
        }
        
        // 检查URI是否已被其他资源使用（如果提供了URI）
        if (StringUtils.hasText(resourceDto.getUri())) {
            Optional<Resource> resourceWithSameUri = findByUri(resourceDto.getUri());
            if (resourceWithSameUri.isPresent() && !resourceWithSameUri.get().getId().equals(resourceDto.getId())) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "资源URI已被其他资源使用: " + resourceDto.getUri()
                );
            }
        }
        
        // 更新字段
        existingResource.setName(resourceDto.getName());
        existingResource.setTitle(resourceDto.getTitle());
        existingResource.setUrl(resourceDto.getUrl());
        
        // 设置 uri 和 method 的默认值
        String uri = resourceDto.getUri();
        if (uri == null || uri.trim().isEmpty()) {
            uri = ""; // 设置默认值为空字符串
        }
        existingResource.setUri(uri);
        
        String method = resourceDto.getMethod();
        if (method == null || method.trim().isEmpty()) {
            method = ""; // 设置默认值为空字符串
        }
        existingResource.setMethod(method);
        
        existingResource.setIcon(resourceDto.getIcon());
        existingResource.setShowIcon(resourceDto.isShowIcon());
        existingResource.setSort(resourceDto.getSort());
        existingResource.setComponent(resourceDto.getComponent());
        existingResource.setRedirect(resourceDto.getRedirect());
        existingResource.setHidden(resourceDto.isHidden());
        existingResource.setKeepAlive(resourceDto.isKeepAlive());
        existingResource.setPermission(resourceDto.getPermission());
        existingResource.setRequiredPermissionId(resourceDto.getRequiredPermissionId());
        existingResource.setType(ResourceType.fromCode(resourceDto.getType()));
        existingResource.setParentId(resourceDto.getParentId());
        existingResource.setTenantId(currentManagedTenantId());
        existingResource.setResourceLevel(currentResourceLevel());
        resourcePermissionBindingService.bindResource(existingResource, extractCurrentUserId());
        // fail-closed: permission provided but required_permission_id could not be resolved
        if (StringUtils.hasText(existingResource.getPermission()) && existingResource.getRequiredPermissionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required_permission_id for provided permission");
        }

        return saveCarrierEntry(existingResource);
    }

    /**
     * 按资源类型校验必填字段：
     * <ul>
     *   <li>接口(API)：必须填写 uri、method</li>
     *   <li>菜单/目录(MENU/DIRECTORY)：必须填写 url、component</li>
     *   <li>按钮(BUTTON)：无额外必填</li>
     * </ul>
     * 与 DB 层 CHECK 约束一致，避免绕过应用写入导致数据无效。
     */
    private void validateResourceByType(ResourceCreateUpdateDto dto) {
        ResourceType type = ResourceType.fromCode(dto.getType());
        switch (type) {
            case API -> {
                if (!StringUtils.hasText(dto.getUri())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "接口类型资源必须填写 API 路径(uri)");
                }
                if (!StringUtils.hasText(dto.getMethod())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "接口类型资源必须填写 HTTP 方法(method)");
                }
            }
            case MENU, DIRECTORY -> {
                if (!StringUtils.hasText(dto.getUrl())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "菜单/目录类型资源必须填写前端路由路径(url)");
                }
                if (!StringUtils.hasText(dto.getComponent())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "菜单/目录类型资源必须填写组件路径(component)");
                }
            }
            default -> { /* BUTTON 等无额外必填 */ }
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = currentManagedTenantId();

        Optional<Resource> managed = findManagedControlPlaneResource(id);

        if (managed.isEmpty()) {
            return;
        }

        Resource resource = managed.get();
        ResourceType type = resource.getType();

        // Menu/DIRECTORY: must cascade in the resource tree (including nested menus and attached buttons).
        if (type == ResourceType.DIRECTORY || type == ResourceType.MENU) {
            if (tenantId == null) {
                // Platform template cascade is not supported here; fail-safe: treat as single delete.
                deleteSingleCarrier(resource, null);
                return;
            }
            deleteMenuTreeRecursive(id, tenantId);
            deleteSingleCarrier(resource, tenantId);
            return;
        }

        // Non-menu: forbid deleting when there are child resources.
        List<Resource> children = findByParentId(id);
        if (!children.isEmpty()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                String.format("无法删除有子资源的资源，请先删除子资源。资源ID: %d，子资源数量: %d", id, children.size())
            );
        }

        deleteSingleCarrier(resource, tenantId);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    private void deleteMenuTreeRecursive(Long parentId, Long tenantId) {
        List<Resource> children = findByParentId(parentId);

        for (Resource child : children) {
            deleteMenuTreeRecursive(child.getId(), tenantId);
            deleteSingleCarrier(child, tenantId);
        }
    }

    /**
     * Delete a single carrier projection and (only if it becomes the last carrier) revoke permission bindings
     * from {@code role_permission}.
     */
    private void deleteSingleCarrier(Resource resource, Long tenantId) {
        if (resource == null || resource.getId() == null) {
            return;
        }

        Long requiredPermissionId = resource.getRequiredPermissionId();

        deleteCarrierEntriesById(resource.getId());

        if (requiredPermissionId == null) {
            return;
        }

        boolean permissionStillReferenced = carrierPermissionReferenceSafetyService.existsPermissionReference(requiredPermissionId, tenantId);
        if (permissionStillReferenced) {
            return;
        }

        roleRepository.deleteRolePermissionRelationsByPermissionIdAndTenantId(requiredPermissionId, tenantId);
    }

    @Override
    public List<Resource> findByType(ResourceType type) {
        if (type == ResourceType.BUTTON) {
            return uiActionEntryRepository.findAll(
                uiActionCarrierScopeSpec(currentManagedTenantId(), currentResourceLevel()),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList();
        }
        if (type == ResourceType.API) {
            return apiEndpointEntryRepository.findAll(
                apiCarrierScopeSpec(currentManagedTenantId(), currentResourceLevel()),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList();
        }
        return menuEntryRepository.findAll(
            menuCarrierTypeSpec(type, currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList();
    }

    @Override
    public List<ResourceResponseDto> findDtosByType(ResourceType type) {
        if (type == ResourceType.BUTTON) {
            return uiActionEntryRepository.findAll(
                uiActionCarrierScopeSpec(currentManagedTenantId(), currentResourceLevel()),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream().map(this::toDto).toList();
        }
        if (type == ResourceType.API) {
            return apiEndpointEntryRepository.findAll(
                apiCarrierScopeSpec(currentManagedTenantId(), currentResourceLevel()),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
            ).stream().map(this::toDto).toList();
        }
        return findByType(type).stream().map(this::toDto).toList();
    }

    @Override
    public List<ResourceResponseDto> findAllowedUiActionDtos(String pagePath) {
        String normalizedPagePath = normalizeCarrierPath(pagePath);
        if (!StringUtils.hasText(normalizedPagePath)) {
            return List.of();
        }
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();
        List<Long> parentMenuIds = menuEntryRepository.findAll(
                menuPathCarrierSpec(normalizedPagePath, tenantId, resourceLevel),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream()
            .map(com.tiny.platform.infrastructure.menu.domain.MenuEntry::getId)
            .filter(Objects::nonNull)
            .toList();
        if (parentMenuIds.isEmpty()) {
            return List.of();
        }
        List<UiActionEntry> actions = uiActionEntryRepository.findAll(
            uiActionParentMenuIdsCarrierSpec(parentMenuIds, tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        );
        Set<String> authorityCodes = resolveCurrentAuthorityCodes();
        Map<Long, RequirementAwareAuditDetail> details =
            carrierPermissionRequirementEvaluator.resolveUiActionRequirementDetails(actions, authorityCodes);
        Set<Long> allowedActionIds = details.entrySet().stream()
            .filter(e -> "ALLOW".equalsIgnoreCase(e.getValue().decision()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());

        details.values().forEach(detail -> {
            try {
                authorizationAuditService.logRequirementAware(
                    AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS,
                    tenantId,
                    "ui-action-runtime-gate",
                    detail
                );
            } catch (Exception ex) {
                logger.warn(
                    "Requirement-aware ui_action audit failed (tenantId={}, carrierId={}, carrierType={})",
                    tenantId,
                    detail == null ? null : detail.carrierId(),
                    detail == null ? null : detail.carrierType(),
                    ex
                );
            }
        });

        return actions.stream()
            .filter(action -> action.getId() != null && allowedActionIds.contains(action.getId()))
            .map(this::toDto)
            .toList();
    }

    @Override
    public boolean canAccessApiEndpoint(String method, String uri) {
        String normalizedUri = normalizeCarrierPath(uri);
        if (!StringUtils.hasText(normalizedUri)) {
            return false;
        }
        List<ApiEndpointEntry> endpoints = apiEndpointEntryRepository.findAll(
            apiEndpointAccessSpec(method, normalizedUri, currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("id"))
        );
        if (endpoints.isEmpty()) {
            return false;
        }
        // Deterministic strict matching:
        // - endpoint.uri supports templates like "/sys/resources/{id}"
        // - request URI must match the template segment-by-segment (no prefix/contains fallback)
        endpoints = endpoints.stream()
            .filter(e -> apiEndpointUriTemplateMatches(e.getUri(), normalizedUri))
            .toList();
        if (endpoints.isEmpty()) {
            return false;
        }
        Set<Long> allowedEndpointIds = carrierPermissionRequirementEvaluator.resolveAllowedApiEndpointIds(endpoints, resolveCurrentAuthorityCodes());
        return endpoints.stream().anyMatch(endpoint -> endpoint.getId() != null && allowedEndpointIds.contains(endpoint.getId()));
    }

    @Override
    public ApiEndpointRequirementDecision evaluateApiEndpointRequirement(String method, String uri) {
        String normalizedUri = normalizeCarrierPath(uri);
        if (!StringUtils.hasText(normalizedUri)) {
            return ApiEndpointRequirementDecision.DENIED;
        }

        List<ApiEndpointEntry> endpoints = apiEndpointEntryRepository.findAll(
            apiEndpointAccessSpec(method, normalizedUri, currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("id"))
        );
        if (endpoints.isEmpty()) {
            return ApiEndpointRequirementDecision.DENIED;
        }
        // Template match must be strict and deterministic.
        endpoints = endpoints.stream()
            .filter(e -> apiEndpointUriTemplateMatches(e.getUri(), normalizedUri))
            .toList();
        if (endpoints.isEmpty()) {
            return ApiEndpointRequirementDecision.DENIED;
        }
        if (endpoints.size() != 1) {
            return ApiEndpointRequirementDecision.DENIED;
        }
        ApiEndpointEntry endpoint = endpoints.getFirst();
        Set<String> authorityCodes = resolveCurrentAuthorityCodes();
        RequirementAwareAuditDetail detail =
            carrierPermissionRequirementEvaluator.evaluateApiEndpointRequirementDetail(endpoint, authorityCodes);

        try {
            authorizationAuditService.logRequirementAware(
                AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS,
                currentManagedTenantId(),
                "api-endpoint-unified-guard",
                detail
            );
        } catch (Exception ex) {
            logger.warn(
                "Requirement-aware api_endpoint audit failed (tenantId={}, carrierId={}, carrierType={})",
                currentManagedTenantId(),
                detail == null ? null : detail.carrierId(),
                detail == null ? null : detail.carrierType(),
                ex
            );
        }

        return detail != null && "ALLOW".equalsIgnoreCase(detail.decision())
            ? ApiEndpointRequirementDecision.ALLOWED
            : ApiEndpointRequirementDecision.DENIED;
    }

    @Override
    public List<Resource> findByTypeIn(List<ResourceType> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        List<Resource> result = new ArrayList<>();
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();
        if (types.contains(ResourceType.DIRECTORY) || types.contains(ResourceType.MENU)) {
            result.addAll(menuEntryRepository.findAll(
                menuCarrierTypesSpec(types, tenantId, resourceLevel),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList());
        }
        if (types.contains(ResourceType.BUTTON)) {
            result.addAll(uiActionEntryRepository.findAll(
                uiActionCarrierScopeSpec(tenantId, resourceLevel),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList());
        }
        if (types.contains(ResourceType.API)) {
            result.addAll(apiEndpointEntryRepository.findAll(
                apiCarrierScopeSpec(tenantId, resourceLevel),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
            ).stream().map(this::toResource).toList());
        }
        return result;
    }

    @Override
    public List<Resource> findByParentId(Long parentId) {
        List<Resource> result = new ArrayList<>();
        result.addAll(menuEntryRepository.findAll(
            childMenuCarrierSpec(parentId, currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList());
        result.addAll(uiActionEntryRepository.findAll(
            childUiActionCarrierSpec(parentId, currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList());
        return result;
    }

    @Override
    @DataScope(module = "resource")
    public List<ResourceResponseDto> findChildDtos(Long parentId) {
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();
        List<ResourceResponseDto> result = new ArrayList<>();
        result.addAll(menuEntryRepository.findAll(
            childMenuControlPlaneReadSpec(parentId, tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toDto).toList());
        result.addAll(uiActionEntryRepository.findAll(
            childUiActionControlPlaneReadSpec(parentId, tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toDto).toList());
        return result;
    }

    @Override
    public List<Resource> findTopLevel() {
        List<Resource> result = new ArrayList<>();
        result.addAll(menuEntryRepository.findAll(
            topLevelMenuCarrierSpec(currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList());
        result.addAll(uiActionEntryRepository.findAll(
            topLevelUiActionCarrierSpec(currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList());
        result.addAll(apiEndpointEntryRepository.findAll(
            apiCarrierScopeSpec(currentManagedTenantId(), currentResourceLevel()),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        ).stream().map(this::toResource).toList());
        return result;
    }

    @Override
    @DataScope(module = "resource")
    public List<ResourceResponseDto> findTopLevelDtos() {
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();
        List<ResourceResponseDto> result = new ArrayList<>();
        result.addAll(menuEntryRepository.findAll(
            topLevelMenuControlPlaneReadSpec(tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toDto).toList());
        result.addAll(uiActionEntryRepository.findAll(
            topLevelUiActionControlPlaneReadSpec(tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        ).stream().map(this::toDto).toList());
        result.addAll(apiEndpointEntryRepository.findAll(
            apiControlPlaneReadSpec(tenantId, resourceLevel),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
        ).stream().map(this::toDto).toList());
        return result;
    }

    /**
     * 资源管理树：与 {@link #resources} 分页读一致，在 {@code @DataScope} 注入的 {@link DataScopeContext} 下按
     * {@code created_by ∈ visibleCreatorIds} 做 query-time 收缩；几何与角色集合仍由
     * {@link com.tiny.platform.infrastructure.auth.datascope.service.DataScopeResolverService}
     * 根据 {@link TenantContext#getActiveScopeType()}/{@link TenantContext#getActiveScopeId()} 解析（Contract B）。
     */
    @Override
    @DataScope(module = "resource")
    public List<ResourceResponseDto> findResourceTreeDtos() {
        List<ResourceResponseDto> roots = new ArrayList<>(findTopLevelDtos());
        roots.sort(resourceDtoOrder());
        roots.forEach(this::populateChildrenRecursively);
        return roots;
    }

    @Override
    public List<ResourceResponseDto> buildResourceTree(List<Resource> resources) {
        // 构建ID到资源DTO的映射
        Map<Long, ResourceResponseDto> resourceMap = new HashMap<>();
        List<ResourceResponseDto> rootResources = new ArrayList<>();
        // 1. 先将所有资源转为DTO并建立映射
        for (Resource resource : resources) {
            ResourceResponseDto dto = toDto(resource);
            // 自动补全 enabled 字段，null 时默认 true
            if (dto.getEnabled() == null) {
                dto.setEnabled(Boolean.TRUE);
            }
            // 初始化 children，避免为 null
            dto.setChildren(new ArrayList<>());
            resourceMap.put(resource.getId(), dto);
        }
        // 2. 构建树形结构
        for (Resource resource : resources) {
            ResourceResponseDto dto = resourceMap.get(resource.getId());
            if (resource.getParentId() == null) {
                // 顶级资源，加入根节点列表
                rootResources.add(dto);
            } else {
                // 子资源，加入父节点的 children
                ResourceResponseDto parent = resourceMap.get(resource.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                }
            }
        }
        // 3. 递归补全 leaf 字段
        for (ResourceResponseDto root : rootResources) {
            fillLeafField(root);
        }
        return rootResources;
    }

    /**
     * 递归补全 leaf 字段，children 为空则 leaf=true，否则 leaf=false
     */
    private void fillLeafField(ResourceResponseDto node) {
        if (node.getChildren() == null) {
            node.setChildren(new ArrayList<>());
        }
        if (node.getChildren().isEmpty()) {
            node.setLeaf(Boolean.TRUE);
        } else {
            node.setLeaf(Boolean.FALSE);
            for (ResourceResponseDto child : node.getChildren()) {
                fillLeafField(child);
            }
        }
    }

    private void populateChildrenRecursively(ResourceResponseDto node) {
        Long parentId = node.getId();
        List<ResourceResponseDto> children = new ArrayList<>(findChildDtos(parentId));
        children.sort(resourceDtoOrder());
        node.setChildren(children);
        if (children.isEmpty()) {
            node.setLeaf(Boolean.TRUE);
            return;
        }
        node.setLeaf(Boolean.FALSE);
        children.forEach(this::populateChildrenRecursively);
    }

    private Comparator<ResourceResponseDto> resourceDtoOrder() {
        return Comparator
            .comparing(ResourceResponseDto::getSort, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ResourceResponseDto::getId, Comparator.nullsLast(Long::compareTo));
    }

    @Override
    public Optional<Resource> findByName(String name) {
        return findManagedControlPlaneResourcesByField("name", name).stream().findFirst();
    }

    @Override
    public Optional<Resource> findByUrl(String url) {
        return findManagedControlPlaneResourcesByField("url", url).stream().findFirst();
    }

    @Override
    public Optional<Resource> findByUri(String uri) {
        return findManagedControlPlaneResourcesByField("uri", uri).stream().findFirst();
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return findManagedControlPlaneResourcesByField("name", name).stream()
            .anyMatch(r -> excludeId == null || !r.getId().equals(excludeId));
    }

    @Override
    public boolean existsByUrl(String url, Long excludeId) {
        return findManagedControlPlaneResourcesByField("url", url).stream()
            .anyMatch(r -> excludeId == null || !r.getId().equals(excludeId));
    }

    @Override
    public boolean existsByUri(String uri, Long excludeId) {
        return findManagedControlPlaneResourcesByField("uri", uri).stream()
            .anyMatch(r -> excludeId == null || !r.getId().equals(excludeId));
    }

    @Override
    public Resource updateSort(Long id, Integer sort) {
        Resource resource = findManagedControlPlaneResource(id)
                .orElseThrow(() -> new RuntimeException("资源不存在"));
        resource.setSort(sort);
        Resource carrierResource = saveCarrierEntry(resource);
        return carrierResource;
    }

    @Override
    public List<ResourceType> getResourceTypes() {
        return Arrays.asList(ResourceType.values());
    }

    @Override
    public List<Resource> findByRoleId(Long roleId) {
        Long tenantId = currentManagedTenantId();
        if (roleId == null) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = roleRepository.findPermissionIdsByRoleIdAndTenantId(roleId, tenantId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return loadCarrierResourcesByPermissionIds(permissionIds, tenantId);
    }

    @Override
    public List<Resource> findByUserId(Long userId) {
        Long tenantId = requireTenantId();
        Set<Long> roleIds = new LinkedHashSet<>(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(userId, tenantId));
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = roleRepository.findPermissionIdsByRoleIdsAndTenantId(new ArrayList<>(roleIds), tenantId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return loadCarrierResourcesByPermissionIds(permissionIds, tenantId);
    }

    private List<Resource> loadCarrierResourcesByPermissionIds(Collection<Long> permissionIds, Long tenantId) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        String resourceLevel = currentResourceLevel();
        LinkedHashSet<Long> normalizedPermissionIds = permissionIds.stream()
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalizedPermissionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Resource> resources = new ArrayList<>();
        resources.addAll(menuEntryRepository.findByRequiredPermissionIdInAndScope(normalizedPermissionIds, tenantId, resourceLevel)
            .stream()
            .map(this::toResource)
            .toList());
        resources.addAll(uiActionEntryRepository.findByRequiredPermissionIdInAndScope(normalizedPermissionIds, tenantId, resourceLevel)
            .stream()
            .map(this::toResource)
            .toList());
        resources.addAll(apiEndpointEntryRepository.findByRequiredPermissionIdInAndScope(normalizedPermissionIds, tenantId, resourceLevel)
            .stream()
            .map(this::toResource)
            .toList());

        resources.sort(
            Comparator.comparing(Resource::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Resource::getId, Comparator.nullsLast(Long::compareTo))
        );
        return resources;
    }

    /**
     * 将Resource实体转换为ResourceResponseDto
     */
    private ResourceResponseDto toDto(Resource resource) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setRecordTenantId(resource.getTenantId());
        dto.setId(resource.getId());
        dto.setName(resource.getName());
        dto.setTitle(resource.getTitle());
        dto.setUrl(resource.getUrl());
        dto.setUri(resource.getUri());
        dto.setMethod(resource.getMethod());
        dto.setIcon(resource.getIcon());
        dto.setShowIcon(resource.getShowIcon());
        dto.setSort(resource.getSort());
        dto.setComponent(resource.getComponent());
        dto.setRedirect(resource.getRedirect());
        dto.setHidden(resource.getHidden());
        dto.setKeepAlive(resource.getKeepAlive());
        dto.setPermission(resource.getPermission());
        dto.setRequiredPermissionId(resource.getRequiredPermissionId());
        dto.setType(resource.getType().getCode());
        dto.setTypeName(getTypeName(resource.getType()));
        dto.setCarrierKind(getCarrierKind(resource.getType()));
        dto.setParentId(resource.getParentId());
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    private Resource saveCarrierEntry(Resource resource) {
        if (resource == null || resource.getType() == null) {
            return resource;
        }
        return switch (resource.getType()) {
            case DIRECTORY, MENU -> toResource(menuEntryRepository.save(toMenuEntry(resource)));
            case BUTTON -> toResource(uiActionEntryRepository.save(toUiActionEntry(resource)));
            case API -> toResource(apiEndpointEntryRepository.save(toApiEndpointEntry(resource)));
        };
    }

    private void deleteCarrierEntriesById(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Long> ids = List.of(resourceId);
        menuEntryRepository.deleteAllByIdInBatch(ids);
        uiActionEntryRepository.deleteAllByIdInBatch(ids);
        apiEndpointEntryRepository.deleteAllByIdInBatch(ids);
    }

    private com.tiny.platform.infrastructure.menu.domain.MenuEntry toMenuEntry(Resource resource) {
        com.tiny.platform.infrastructure.menu.domain.MenuEntry menu = new com.tiny.platform.infrastructure.menu.domain.MenuEntry();
        menu.setId(resource.getId());
        menu.setTenantId(resource.getTenantId());
        menu.setResourceLevel(resource.getResourceLevel());
        menu.setName(resource.getName());
        menu.setTitle(resource.getTitle());
        menu.setPath(resource.getUrl());
        menu.setIcon(resource.getIcon());
        menu.setShowIcon(Boolean.TRUE.equals(resource.getShowIcon()));
        menu.setSort(resource.getSort());
        menu.setComponent(resource.getComponent());
        menu.setRedirect(resource.getRedirect());
        menu.setHidden(Boolean.TRUE.equals(resource.getHidden()));
        menu.setKeepAlive(Boolean.TRUE.equals(resource.getKeepAlive()));
        menu.setPermission(resource.getPermission());
        menu.setRequiredPermissionId(resource.getRequiredPermissionId());
        menu.setType(resource.getType().getCode());
        menu.setParentId(resource.getParentId());
        menu.setEnabled(resource.getEnabled());
        menu.setCreatedAt(resource.getCreatedAt());
        menu.setCreatedBy(resource.getCreatedBy());
        menu.setUpdatedAt(resource.getUpdatedAt());
        return menu;
    }

    private UiActionEntry toUiActionEntry(Resource resource) {
        UiActionEntry action = new UiActionEntry();
        action.setId(resource.getId());
        action.setTenantId(resource.getTenantId());
        action.setResourceLevel(resource.getResourceLevel());
        action.setName(resource.getName());
        action.setTitle(resource.getTitle());
        action.setActionKey(resource.getName());
        action.setPagePath(resource.getUrl());
        action.setPermission(resource.getPermission());
        action.setRequiredPermissionId(resource.getRequiredPermissionId());
        action.setParentMenuId(resource.getParentId());
        action.setSort(resource.getSort());
        action.setEnabled(resource.getEnabled());
        action.setCreatedAt(resource.getCreatedAt());
        action.setCreatedBy(resource.getCreatedBy());
        action.setUpdatedAt(resource.getUpdatedAt());
        return action;
    }

    private ApiEndpointEntry toApiEndpointEntry(Resource resource) {
        ApiEndpointEntry endpoint = new ApiEndpointEntry();
        endpoint.setId(resource.getId());
        endpoint.setTenantId(resource.getTenantId());
        endpoint.setResourceLevel(resource.getResourceLevel());
        endpoint.setName(resource.getName());
        endpoint.setTitle(resource.getTitle());
        endpoint.setUri(resource.getUri());
        endpoint.setMethod(resource.getMethod());
        endpoint.setPermission(resource.getPermission());
        endpoint.setRequiredPermissionId(resource.getRequiredPermissionId());
        endpoint.setEnabled(resource.getEnabled());
        endpoint.setCreatedAt(resource.getCreatedAt());
        endpoint.setCreatedBy(resource.getCreatedBy());
        endpoint.setUpdatedAt(resource.getUpdatedAt());
        return endpoint;
    }

    private Resource toResource(com.tiny.platform.infrastructure.menu.domain.MenuEntry menu) {
        Resource resource = new Resource();
        resource.setId(menu.getId());
        resource.setTenantId(menu.getTenantId());
        resource.setResourceLevel(menu.getResourceLevel());
        resource.setName(menu.getName());
        resource.setTitle(menu.getTitle());
        resource.setUrl(menu.getPath());
        resource.setUri("");
        resource.setMethod("");
        resource.setIcon(menu.getIcon());
        resource.setShowIcon(menu.getShowIcon());
        resource.setSort(menu.getSort());
        resource.setComponent(menu.getComponent());
        resource.setRedirect(menu.getRedirect());
        resource.setHidden(menu.getHidden());
        resource.setKeepAlive(menu.getKeepAlive());
        resource.setPermission(menu.getPermission());
        resource.setRequiredPermissionId(menu.getRequiredPermissionId());
        resource.setType(ResourceType.fromCode(menu.getType()));
        resource.setParentId(menu.getParentId());
        resource.setCreatedAt(menu.getCreatedAt());
        resource.setCreatedBy(menu.getCreatedBy());
        resource.setUpdatedAt(menu.getUpdatedAt());
        resource.setEnabled(menu.getEnabled());
        resource.setCarrierType(CARRIER_MENU);
        resource.setCarrierSourceId(menu.getId());
        resource.setChildren(new HashSet<>());
        return resource;
    }

    private ResourceResponseDto toDto(com.tiny.platform.infrastructure.menu.domain.MenuEntry menu) {
        return toDto(toResource(menu));
    }

    private Resource toResource(UiActionEntry action) {
        Resource resource = new Resource();
        resource.setId(action.getId());
        resource.setTenantId(action.getTenantId());
        resource.setResourceLevel(action.getResourceLevel());
        resource.setName(action.getName());
        resource.setTitle(action.getTitle());
        resource.setUrl(action.getPagePath());
        resource.setUri("");
        resource.setMethod("");
        resource.setIcon("");
        resource.setShowIcon(false);
        resource.setSort(action.getSort());
        resource.setComponent("");
        resource.setRedirect("");
        resource.setHidden(false);
        resource.setKeepAlive(false);
        resource.setPermission(action.getPermission());
        resource.setRequiredPermissionId(action.getRequiredPermissionId());
        resource.setType(ResourceType.BUTTON);
        resource.setParentId(action.getParentMenuId());
        resource.setCreatedAt(action.getCreatedAt());
        resource.setCreatedBy(action.getCreatedBy());
        resource.setUpdatedAt(action.getUpdatedAt());
        resource.setEnabled(action.getEnabled());
        resource.setCarrierType(CARRIER_UI_ACTION);
        resource.setCarrierSourceId(action.getId());
        resource.setChildren(new HashSet<>());
        return resource;
    }

    private Resource toResource(ApiEndpointEntry endpoint) {
        Resource resource = new Resource();
        resource.setId(endpoint.getId());
        resource.setTenantId(endpoint.getTenantId());
        resource.setResourceLevel(endpoint.getResourceLevel());
        resource.setName(endpoint.getName());
        resource.setTitle(endpoint.getTitle());
        resource.setUrl("");
        resource.setUri(endpoint.getUri());
        resource.setMethod(endpoint.getMethod());
        resource.setIcon("");
        resource.setShowIcon(false);
        resource.setSort(0);
        resource.setComponent("");
        resource.setRedirect("");
        resource.setHidden(false);
        resource.setKeepAlive(false);
        resource.setPermission(endpoint.getPermission());
        resource.setRequiredPermissionId(endpoint.getRequiredPermissionId());
        resource.setType(ResourceType.API);
        resource.setParentId(null);
        resource.setCreatedAt(endpoint.getCreatedAt());
        resource.setCreatedBy(endpoint.getCreatedBy());
        resource.setUpdatedAt(endpoint.getUpdatedAt());
        resource.setEnabled(endpoint.getEnabled());
        resource.setCarrierType(CARRIER_API_ENDPOINT);
        resource.setCarrierSourceId(endpoint.getId());
        resource.setChildren(new HashSet<>());
        return resource;
    }

    private ResourceResponseDto toDto(UiActionEntry action) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setRecordTenantId(action.getTenantId());
        dto.setId(action.getId());
        dto.setName(action.getName());
        dto.setTitle(action.getTitle());
        dto.setUrl(action.getPagePath());
        dto.setUri("");
        dto.setMethod("");
        dto.setIcon("");
        dto.setShowIcon(Boolean.FALSE);
        dto.setSort(action.getSort());
        dto.setComponent("");
        dto.setRedirect("");
        dto.setHidden(Boolean.FALSE);
        dto.setKeepAlive(Boolean.FALSE);
        dto.setPermission(action.getPermission());
        dto.setRequiredPermissionId(action.getRequiredPermissionId());
        dto.setType(ResourceType.BUTTON.getCode());
        dto.setTypeName(getTypeName(ResourceType.BUTTON));
        dto.setCarrierKind(getCarrierKind(ResourceType.BUTTON));
        dto.setParentId(action.getParentMenuId());
        dto.setEnabled(action.getEnabled());
        dto.setLeaf(Boolean.TRUE);
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    private ResourceResponseDto toDto(ApiEndpointEntry endpoint) {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setRecordTenantId(endpoint.getTenantId());
        dto.setId(endpoint.getId());
        dto.setName(endpoint.getName());
        dto.setTitle(endpoint.getTitle());
        dto.setUrl("");
        dto.setUri(endpoint.getUri());
        dto.setMethod(endpoint.getMethod());
        dto.setIcon("");
        dto.setShowIcon(Boolean.FALSE);
        dto.setSort(0);
        dto.setComponent("");
        dto.setRedirect("");
        dto.setHidden(Boolean.FALSE);
        dto.setKeepAlive(Boolean.FALSE);
        dto.setPermission(endpoint.getPermission());
        dto.setRequiredPermissionId(endpoint.getRequiredPermissionId());
        dto.setType(ResourceType.API.getCode());
        dto.setTypeName(getTypeName(ResourceType.API));
        dto.setCarrierKind(getCarrierKind(ResourceType.API));
        dto.setParentId(null);
        dto.setEnabled(endpoint.getEnabled());
        dto.setLeaf(Boolean.TRUE);
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    /**
     * 获取资源类型名称
     */
    private String getTypeName(ResourceType type) {
        switch (type) {
            case DIRECTORY:
                return "目录";
            case MENU:
                return "菜单";
            case BUTTON:
                return "按钮";
            case API:
                return "API";
            default:
                return "未知";
        }
    }

    private String getCarrierKind(ResourceType type) {
        return switch (type) {
            case DIRECTORY, MENU -> "menu";
            case BUTTON -> "ui_action";
            case API -> "api_endpoint";
        };
    }


    private String normalizeCarrierPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Strict template URI matching for {@code api_endpoint.uri}.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Templates are only supported when the placeholder occupies the whole path segment, e.g. "{id}".</li>
     *   <li>Match is segment-by-segment with identical segment count.</li>
     *   <li>No prefix/contains fallback is allowed.</li>
     *   <li>Non-placeholder segments must match exactly.</li>
     * </ul>
     */
    private boolean apiEndpointUriTemplateMatches(String templateUri, String requestUri) {
        String t = normalizeCarrierPath(templateUri);
        String r = normalizeCarrierPath(requestUri);
        if (!StringUtils.hasText(t) || !StringUtils.hasText(r)) {
            return false;
        }

        List<String> tSegs = splitUriPathSegments(t);
        List<String> rSegs = splitUriPathSegments(r);
        if (tSegs.size() != rSegs.size()) {
            return false;
        }

        for (int i = 0; i < tSegs.size(); i++) {
            String tSeg = tSegs.get(i);
            String rSeg = rSegs.get(i);
            if (isTemplatePlaceholderSegment(tSeg)) {
                // placeholder matches exactly one path segment (no slashes due to segment splitting)
                if (!StringUtils.hasText(rSeg)) {
                    return false;
                }
                continue;
            }
            if (!tSeg.equals(rSeg)) {
                return false;
            }
        }
        return true;
    }

    private List<String> splitUriPathSegments(String uri) {
        String normalized = uri.startsWith("/") ? uri.substring(1) : uri;
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        // Keep segment count deterministic; path segments are separated by "/"
        return Arrays.asList(normalized.split("/", -1));
    }

    private boolean isTemplatePlaceholderSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return false;
        }
        // Placeholder must occupy the whole segment, e.g. "{id}".
        // Disallow nested braces and any slash.
        if (segment.length() < 3 || !segment.startsWith("{") || !segment.endsWith("}")) {
            return false;
        }
        String inner = segment.substring(1, segment.length() - 1);
        if (!StringUtils.hasText(inner)) {
            return false;
        }
        return inner.chars().allMatch(ch -> ch != '/' && ch != '{' && ch != '}');
    }

    private Set<String> resolveCurrentAuthorityCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantId;
    }

    private Long currentManagedTenantId() {
        return TenantContext.isPlatformScope() ? null : requireTenantId();
    }

    private String currentResourceLevel() {
        return TenantContext.isPlatformScope() ? RESOURCE_LEVEL_PLATFORM : RESOURCE_LEVEL_TENANT;
    }

    private Optional<Resource> findManagedControlPlaneResource(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();

        Optional<Resource> menu = safeOptional(menuEntryRepository.findById(id))
            .filter(entry -> matchesCarrierScope(entry.getTenantId(), entry.getResourceLevel(), tenantId, resourceLevel))
            .map(this::toResource);
        if (menu.isPresent()) {
            return menu;
        }

        Optional<Resource> action = safeOptional(uiActionEntryRepository.findById(id))
            .filter(entry -> matchesCarrierScope(entry.getTenantId(), entry.getResourceLevel(), tenantId, resourceLevel))
            .map(this::toResource);
        if (action.isPresent()) {
            return action;
        }

        return safeOptional(apiEndpointEntryRepository.findById(id))
            .filter(entry -> matchesCarrierScope(entry.getTenantId(), entry.getResourceLevel(), tenantId, resourceLevel))
            .map(this::toResource);
    }

    private List<Resource> findManagedControlPlaneResourcesByField(String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        Long tenantId = currentManagedTenantId();
        String resourceLevel = currentResourceLevel();
        List<Resource> resources = new ArrayList<>();
        switch (fieldName) {
            case "name" -> {
                resources.addAll(safeList(menuEntryRepository.findAll(menuExactFieldSpec("name", value, tenantId, resourceLevel)))
                    .stream().map(this::toResource).toList());
                resources.addAll(safeList(uiActionEntryRepository.findAll(uiActionExactFieldSpec("name", value, tenantId, resourceLevel)))
                    .stream().map(this::toResource).toList());
                resources.addAll(safeList(apiEndpointEntryRepository.findAll(apiExactFieldSpec("name", value, tenantId, resourceLevel)))
                    .stream().map(this::toResource).toList());
            }
            case "url" -> {
                resources.addAll(safeList(menuEntryRepository.findAll(menuExactFieldSpec("path", value, tenantId, resourceLevel)))
                    .stream().map(this::toResource).toList());
                resources.addAll(safeList(uiActionEntryRepository.findAll(uiActionExactFieldSpec("pagePath", value, tenantId, resourceLevel)))
                    .stream().map(this::toResource).toList());
            }
            case "uri" -> resources.addAll(safeList(apiEndpointEntryRepository.findAll(apiExactFieldSpec("uri", value, tenantId, resourceLevel)))
                .stream().map(this::toResource).toList());
            default -> {
                return List.of();
            }
        }
        resources.sort(resourceSortComparator(Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))));
        return resources;
    }

    private boolean matchesCarrierScope(Long entryTenantId, String entryResourceLevel, Long tenantId, String resourceLevel) {
        return Objects.equals(entryTenantId, tenantId)
            && resourceLevel.equalsIgnoreCase(entryResourceLevel);
    }

    private <T> Optional<T> safeOptional(Optional<T> optional) {
        return optional == null ? Optional.empty() : optional;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> menuPathCarrierSpec(String path,
                                                                                                      Long tenantId,
                                                                                                      String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("path"), path));
            predicates.add(criteriaBuilder.equal(root.get("enabled"), Boolean.TRUE));
            predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> topLevelMenuCarrierSpec(Long tenantId,
                                                                                                           String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.isNull(root.get("parentId")));
            predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> childMenuCarrierSpec(Long parentId,
                                                                                                        Long tenantId,
                                                                                                        String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("parentId"), parentId));
            predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> menuCarrierTypesSpec(List<ResourceType> types,
                                                                                                        Long tenantId,
                                                                                                        String resourceLevel) {
        List<Integer> typeCodes = types.stream()
            .filter(type -> type == ResourceType.DIRECTORY || type == ResourceType.MENU)
            .map(ResourceType::getCode)
            .toList();
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(root.get("type").in(typeCodes));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> menuCarrierTypeSpec(ResourceType type,
                                                                                                       Long tenantId,
                                                                                                       String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("type"), type.getCode()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> menuExactFieldSpec(String fieldName,
                                                                                                      String value,
                                                                                                      Long tenantId,
                                                                                                      String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get(fieldName), value));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> topLevelUiActionCarrierSpec(Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.isNull(root.get("parentMenuId")));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }


    private Specification<UiActionEntry> uiActionParentMenuIdsCarrierSpec(Collection<Long> parentMenuIds,
                                                                          Long tenantId,
                                                                          String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(root.get("parentMenuId").in(parentMenuIds));
            predicates.add(criteriaBuilder.equal(root.get("enabled"), Boolean.TRUE));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    private Specification<UiActionEntry> childUiActionCarrierSpec(Long parentId, Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("parentMenuId"), parentId));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> uiActionCarrierScopeSpec(Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> uiActionExactFieldSpec(String fieldName,
                                                                String value,
                                                                Long tenantId,
                                                                String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get(fieldName), value));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }


    private Specification<ApiEndpointEntry> apiEndpointAccessSpec(String method,
                                                                  String uri,
                                                                  Long tenantId,
                                                                  String resourceLevel) {
        String normalizedMethod = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : null;
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("enabled"), Boolean.TRUE));
            if (normalizedMethod != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("method")), normalizedMethod));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    private Specification<ApiEndpointEntry> apiCarrierScopeSpec(Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 控制面树/分层读：在 {@link DataScopeContext} 受限时于 SQL 层追加 {@code created_by} 谓词（与 {@link #resources} 一致）。
     */
    private void appendControlPlaneReadDataScopePredicates(List<Predicate> predicates,
                                                          jakarta.persistence.criteria.Path<Long> createdByPath,
                                                          jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                                          Long tenantId) {
        if (tenantId == null) {
            return;
        }
        if (!requiresDataScopeFilter()) {
            return;
        }
        LinkedHashSet<Long> visible = resolveVisibleCreatorIdsForRead(tenantId);
        if (visible.isEmpty()) {
            predicates.add(criteriaBuilder.disjunction());
        } else {
            predicates.add(createdByPath.in(visible));
        }
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> topLevelMenuControlPlaneReadSpec(
        Long tenantId,
        String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.isNull(root.get("parentId")));
            predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            appendControlPlaneReadDataScopePredicates(predicates, root.get("createdBy"), criteriaBuilder, tenantId);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<com.tiny.platform.infrastructure.menu.domain.MenuEntry> childMenuControlPlaneReadSpec(
        Long parentId,
        Long tenantId,
        String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("parentId"), parentId));
            predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            appendControlPlaneReadDataScopePredicates(predicates, root.get("createdBy"), criteriaBuilder, tenantId);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> topLevelUiActionControlPlaneReadSpec(Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.isNull(root.get("parentMenuId")));
            appendControlPlaneReadDataScopePredicates(predicates, root.get("createdBy"), criteriaBuilder, tenantId);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<UiActionEntry> childUiActionControlPlaneReadSpec(Long parentId, Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get("parentMenuId"), parentId));
            appendControlPlaneReadDataScopePredicates(predicates, root.get("createdBy"), criteriaBuilder, tenantId);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ApiEndpointEntry> apiControlPlaneReadSpec(Long tenantId, String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            appendControlPlaneReadDataScopePredicates(predicates, root.get("createdBy"), criteriaBuilder, tenantId);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ApiEndpointEntry> apiExactFieldSpec(String fieldName,
                                                              String value,
                                                              Long tenantId,
                                                              String resourceLevel) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendCarrierScopePredicates(predicates, root, criteriaBuilder, tenantId, resourceLevel);
            predicates.add(criteriaBuilder.equal(root.get(fieldName), value));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void appendCarrierScopePredicates(List<Predicate> predicates,
                                              jakarta.persistence.criteria.Root<?> root,
                                              jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                              Long tenantId,
                                              String resourceLevel) {
        if (tenantId == null) {
            predicates.add(criteriaBuilder.isNull(root.get("tenantId")));
        } else {
            predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
        }
        predicates.add(criteriaBuilder.equal(root.get("resourceLevel"), resourceLevel));
    }

    private LinkedHashSet<Long> resolveVisibleCreatorIdsForRead(Long tenantId) {
        LinkedHashSet<Long> tenantVisibleUserIds = new LinkedHashSet<>(
            tenantUserRepository.findUserIdsByTenantIdAndStatus(tenantId, ACTIVE)
        );
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null || scope.isUnrestricted()) {
            return tenantVisibleUserIds;
        }

        LinkedHashSet<Long> scopedUserIds = new LinkedHashSet<>();
        Long currentUserId = extractCurrentUserId();
        if (scope.isSelfOnly() && currentUserId != null) {
            scopedUserIds.add(currentUserId);
        }
        scopedUserIds.addAll(scope.getVisibleUserIds());

        if (!scope.getVisibleUnitIds().isEmpty()) {
            scopedUserIds.addAll(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(
                tenantId,
                scope.getVisibleUnitIds(),
                ACTIVE
            ));
        }

        if (scopedUserIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        scopedUserIds.retainAll(tenantVisibleUserIds);
        return scopedUserIds;
    }

    private boolean requiresDataScopeFilter() {
        ResolvedDataScope scope = DataScopeContext.get();
        return scope != null && !scope.isUnrestricted();
    }

    private Long extractCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }
} 
