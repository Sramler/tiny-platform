package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeQueryDto;
import com.tiny.platform.core.dict.dto.DictTypeResponseDto;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.dict.service.DictTypeService;
import com.tiny.platform.core.dict.support.DictTenantScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 字典类型服务实现类
 */
@Service
public class DictTypeServiceImpl implements DictTypeService {
    private static final String ACTIVE = "ACTIVE";

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final UserRepository userRepository;

    public DictTypeServiceImpl(DictTypeRepository dictTypeRepository,
                               DictItemRepository dictItemRepository,
                               TenantUserRepository tenantUserRepository,
                               UserUnitRepository userUnitRepository,
                               UserRepository userRepository) {
        this.dictTypeRepository = dictTypeRepository;
        this.dictItemRepository = dictItemRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.userRepository = userRepository;
    }

    /**
     * 租户可见字典类型分页查询。
     * <p>数据范围由 {@code @DataScope(module="dict")} 注入 {@link DataScopeContext}；有效角色与 ORG/DEPT 几何随
     * {@link com.tiny.platform.core.oauth.tenant.TenantContext} 的 active scope 由
     * {@link com.tiny.platform.infrastructure.auth.datascope.service.DataScopeResolverService} 解析（Contract B）。
     * 租户行按 {@code created_by} 用户名与可见用户集交集过滤；平台字典行（{@code tenant_id IS NULL}）在受限 scope 下仍只读展示、不参与按创建者收缩。</p>
     */
    @Override
    @DataScope(module = "dict")
    @Transactional(readOnly = true)
    public Page<DictTypeResponseDto> query(DictTypeQueryDto query, Pageable pageable) {
        // 租户隔离仅以 TenantContext 为准，不使用 query 中的 tenantId
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        if (requiresDataScopeFilter()) {
            LinkedHashSet<String> visibleCreatorNames = resolveVisibleCreatorNamesForRead(currentTenantId);
            List<DictType> filtered = dictTypeRepository.findVisibleByConditions(
                    StringUtils.hasText(query.getDictCode()) ? query.getDictCode() : null,
                    StringUtils.hasText(query.getDictName()) ? query.getDictName() : null,
                    currentTenantId,
                    query.getEnabled(),
                    sortFor(pageable)
            ).stream().filter(dictType -> isReadableType(dictType, currentTenantId, visibleCreatorNames)).toList();
            return toPage(filtered, pageable).map(DictTypeResponseDto::new);
        }
        Page<DictType> page = dictTypeRepository.findVisibleByConditions(
                StringUtils.hasText(query.getDictCode()) ? query.getDictCode() : null,
                StringUtils.hasText(query.getDictName()) ? query.getDictName() : null,
                currentTenantId,
                query.getEnabled(),
                pageable
        );
        return page.map(DictTypeResponseDto::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findById(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        LinkedHashSet<String> visibleCreatorNames = resolveVisibleCreatorNamesForRead(currentTenantId);
        return dictTypeRepository.findById(id)
                .filter(dictType -> DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId))
                .filter(dictType -> isReadableType(dictType, currentTenantId, visibleCreatorNames));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DictType> findByDictCode(String dictCode) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        Optional<DictType> tenantType = dictTypeRepository.findByDictCodeAndTenantId(dictCode, currentTenantId);
        if (tenantType.isPresent()) {
            return tenantType;
        }
        return dictTypeRepository.findByDictCodeAndTenantIdIsNull(dictCode);
    }

    @Override
    @Transactional
    public DictType create(DictTypeCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        validateDictCodeAvailability(dto.getDictCode(), currentTenantId, null);

        DictType dictType = new DictType();
        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setTenantId(currentTenantId);
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictType.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictType.setCreatedAt(LocalDateTime.now());
        dictType.setUpdatedAt(LocalDateTime.now());
        dictType.setCreatedBy(DictTenantScope.currentUsername());
        dictType.setUpdatedBy(DictTenantScope.currentUsername());

        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public DictType update(Long id, DictTypeCreateUpdateDto dto) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findMutableType(id, currentTenantId);

        if (!dictType.getDictCode().equals(dto.getDictCode())) {
            validateDictCodeAvailability(dto.getDictCode(), currentTenantId, id);
        }

        dictType.setDictCode(dto.getDictCode());
        dictType.setDictName(dto.getDictName());
        dictType.setDescription(dto.getDescription());
        dictType.setCategoryId(dto.getCategoryId());
        dictType.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        dictType.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        dictType.setUpdatedAt(LocalDateTime.now());
        dictType.setUpdatedBy(DictTenantScope.currentUsername());

        return dictTypeRepository.save(dictType);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        DictType dictType = findMutableType(id, currentTenantId);

        dictItemRepository.deleteByDictTypeId(id);
        dictTypeRepository.delete(dictType);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictType> findVisibleTypes() {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        List<DictType> visibleTypes = dictTypeRepository.findVisibleByTenantId(currentTenantId);
        if (!requiresDataScopeFilter()) {
            return visibleTypes;
        }
        LinkedHashSet<String> visibleCreatorNames = resolveVisibleCreatorNamesForRead(currentTenantId);
        return visibleTypes.stream().filter(dictType -> isReadableType(dictType, currentTenantId, visibleCreatorNames)).toList();
    }

    @Override
    public boolean existsByDictCode(String dictCode, Long excludeId) {
        Long currentTenantId = DictTenantScope.requireCurrentTenantId();
        if (excludeId == null) {
            return dictTypeRepository.existsByDictCodeAndTenantId(dictCode, currentTenantId)
                    || dictTypeRepository.existsByDictCodeAndTenantIdIsNull(dictCode);
        }
        return dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(dictCode, currentTenantId, excludeId)
                || dictTypeRepository.existsByDictCodeAndTenantIdIsNullAndIdNot(dictCode, excludeId);
    }

    private DictType findMutableType(Long id, Long currentTenantId) {
        DictType dictType = findAccessibleType(id, currentTenantId);
        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "平台字典只读，不允许修改");
        }
        if (Boolean.TRUE.equals(dictType.getBuiltinLocked())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "内置字典已锁定，不允许修改");
        }
        return dictType;
    }

    private DictType findAccessibleType(Long id, Long currentTenantId) {
        DictType dictType = dictTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("字典类型不存在: " + id));
        if (!DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId)) {
            throw new NotFoundException("字典类型不存在: " + id);
        }
        return dictType;
    }

    private void validateDictCodeAvailability(String dictCode, Long currentTenantId, Long excludeId) {
        boolean currentTenantConflict = excludeId == null
                ? dictTypeRepository.existsByDictCodeAndTenantId(dictCode, currentTenantId)
                : dictTypeRepository.existsByDictCodeAndTenantIdAndIdNot(dictCode, currentTenantId, excludeId);
        if (currentTenantConflict) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已存在: " + dictCode);
        }

        boolean platformConflict = excludeId == null
                ? dictTypeRepository.existsByDictCodeAndTenantIdIsNull(dictCode)
                : dictTypeRepository.existsByDictCodeAndTenantIdIsNullAndIdNot(dictCode, excludeId);
        if (platformConflict) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已被平台字典保留: " + dictCode);
        }
    }

    private boolean isReadableType(DictType dictType, Long currentTenantId, LinkedHashSet<String> visibleCreatorNames) {
        if (dictType == null || !DictTenantScope.isVisibleTenant(dictType.getTenantId(), currentTenantId)) {
            return false;
        }
        if (!requiresDataScopeFilter()) {
            return true;
        }
        if (DictTenantScope.isPlatformTenant(dictType.getTenantId())) {
            return true;
        }
        return visibleCreatorNames.contains(dictType.getCreatedBy());
    }

    private LinkedHashSet<String> resolveVisibleCreatorNamesForRead(Long currentTenantId) {
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null) {
            return new LinkedHashSet<>();
        }

        LinkedHashSet<Long> visibleUserIds = new LinkedHashSet<>();
        if (!scope.getVisibleUserIds().isEmpty()) {
            visibleUserIds.addAll(
                    tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(
                            currentTenantId,
                            scope.getVisibleUserIds(),
                            ACTIVE
                    )
            );
        }
        if (!scope.getVisibleUnitIds().isEmpty()) {
            visibleUserIds.addAll(
                    userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(
                            currentTenantId,
                            scope.getVisibleUnitIds(),
                            ACTIVE
                    )
            );
        }
        Long currentUserId = DictTenantScope.currentUserId();
        if (scope.isSelfOnly() && currentUserId != null) {
            visibleUserIds.add(currentUserId);
        }
        return userRepository.findUsernamesByIdIn(visibleUserIds).stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean requiresDataScopeFilter() {
        ResolvedDataScope scope = DataScopeContext.get();
        return scope != null && !scope.isUnrestricted();
    }

    private Sort sortFor(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));
    }

    private <T> Page<T> toPage(List<T> items, Pageable pageable) {
        int start = Math.toIntExact(pageable.getOffset());
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}
