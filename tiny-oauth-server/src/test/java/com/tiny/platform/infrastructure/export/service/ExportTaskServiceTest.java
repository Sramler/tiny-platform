package com.tiny.platform.infrastructure.export.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportTaskServiceTest {

    private ExportTaskRepository repository;
    private TenantUserRepository tenantUserRepository;
    private UserUnitRepository userUnitRepository;
    private UserRepository userRepository;
    private TenantQuotaService tenantQuotaService;
    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ExportTaskRepository.class);
        tenantUserRepository = Mockito.mock(TenantUserRepository.class);
        userUnitRepository = Mockito.mock(UserUnitRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        tenantQuotaService = Mockito.mock(TenantQuotaService.class);
        service = new ExportTaskService(
            repository,
            tenantUserRepository,
            userUnitRepository,
            userRepository,
            tenantQuotaService
        );
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void findReadableTasksShouldReturnAllWhenScopeAll() {
        List<ExportTaskEntity> tasks = List.of(
            task("task-1", "1", "admin"),
            task("task-2", "2", "alice")
        );
        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.all());
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(tasks);

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(tasks, result);
        verifyNoInteractions(tenantUserRepository, userUnitRepository, userRepository, tenantQuotaService);
        verify(repository, never()).findAll(any(Sort.class));
    }

    @Test
    void findReadableTasksShouldReturnAllWhenDataScopeUnrestricted() {
        List<ExportTaskEntity> tasks = List.of(
            task("task-1", "1", "admin"),
            task("task-2", "2", "alice")
        );
        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.all());
        authenticate(5L, 1L, "current");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(tasks);

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(tasks, result);
        verifyNoInteractions(tenantUserRepository, userUnitRepository, userRepository);
        verify(repository, never()).findAll(any(Sort.class));
    }

    @Test
    void findReadableTasksShouldRestrictToSelfWhenSelfOnlyScope() {
        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.selfOnly());
        authenticate(5L, 1L, "current");
        List<ExportTaskEntity> scopedRows = List.of(task("task-self", "5", "current"));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(scopedRows);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(5L, 9L));
        when(userRepository.findUsernamesByIdIn(Set.of(5L))).thenReturn(List.of("current"));

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(List.of("task-self"), result.stream().map(ExportTaskEntity::getTaskId).toList());
        verify(repository, never()).findAll(any(Sort.class));
        verify(repository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void findReadableTasksShouldFilterByVisibleUsersUnitsAndSelf() {
        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(30L), Set.of(2L), true));
        authenticate(5L, 1L, "current");

        List<ExportTaskEntity> scopedRows = List.of(
            task("task-self", "5", "current"),
            task("task-user", "2", "alice"),
            task("task-unit", "3", "bob")
        );
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(scopedRows);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE"))
            .thenReturn(List.of(2L, 3L, 5L));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(1L, Set.of(2L), "ACTIVE"))
            .thenReturn(List.of(2L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(1L, Set.of(30L), "ACTIVE"))
            .thenReturn(List.of(3L));
        when(userRepository.findUsernamesByIdIn(Set.of(2L, 3L, 5L)))
            .thenReturn(List.of("alice", "bob", "current"));

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(List.of("task-self", "task-user", "task-unit"),
            result.stream().map(ExportTaskEntity::getTaskId).toList());
        verify(repository, never()).findAll(any(Sort.class));
    }

    @Test
    void findReadableTasksShouldReturnEmptyWhenVisibleOwnerKeysEmpty() {
        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(99L), Set.of(), false));
        authenticate(5L, 1L, "current");
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(5L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(1L, Set.of(99L), "ACTIVE"))
            .thenReturn(List.of());

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(List.of(), result);
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void findReadableTasksShouldReturnEmptyWhenNoActiveTenant() {
        DataScopeContext.set(ResolvedDataScope.all());

        List<ExportTaskEntity> result = service.findReadableTasks();

        assertEquals(List.of(), result);
        verify(repository, never()).findAll(any(Specification.class), any(Sort.class));
        verify(repository, never()).findAll(any(Sort.class));
    }

    private void authenticate(Long userId, Long tenantId, String username) {
        SecurityUser principal = new SecurityUser(
            userId,
            tenantId,
            username,
            "",
            List.of(),
            true,
            true,
            true,
            true,
            "pv-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities())
        );
    }

    private ExportTaskEntity task(String taskId, String userId, String username) {
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
