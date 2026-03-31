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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 证明 {@link ExportTaskService#findReadableTasks()} 在受限数据范围下走库侧 {@code Specification}，
 * 而非租户内全量加载后再内存过滤。
 */
@SpringBootTest(
    classes = ExportTaskServiceReadableQueryIntegrationTest.TestApplication.class,
    properties = {
        "spring.datasource.type=org.springframework.jdbc.datasource.DriverManagerDataSource",
        "spring.datasource.url=jdbc:h2:mem:exportread;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.liquibase.enabled=false"
    }
)
@ActiveProfiles("integration")
class ExportTaskServiceReadableQueryIntegrationTest {

    @Autowired
    private ExportTaskRepository exportTaskRepository;

    @Autowired
    private ExportTaskService exportTaskService;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUnitRepository userUnitRepository;

    @BeforeEach
    void setUp() {
        exportTaskRepository.deleteAll();
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
        Mockito.reset(tenantUserRepository, userRepository, userUnitRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void findReadableTasksShouldApplyOwnerPredicateInDatabaseNotInMemory() {
        persistTask("t-a", 1L, "5", "alice");
        persistTask("t-b", 1L, "9", "other");
        persistTask("t-c", 2L, "5", "alice");

        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.selfOnly());
        authenticate(5L, 1L, "alice");
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE"))
            .thenReturn(List.of(5L, 9L));
        when(userRepository.findUsernamesByIdIn(Set.of(5L))).thenReturn(List.of("alice"));

        List<ExportTaskEntity> result = exportTaskService.findReadableTasks();

        assertThat(result).extracting(ExportTaskEntity::getTaskId).containsExactly("t-a");
    }

    @Test
    void findReadableTasksShouldApplyVisibleUnitMembersInDatabaseQuery() {
        persistTask("t-a", 1L, "2", "alice");
        persistTask("t-b", 1L, "9", "other");

        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(Set.of(30L), Set.of(), false));
        authenticate(5L, 1L, "viewer");
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(2L, 9L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(1L, Set.of(30L), "ACTIVE"))
            .thenReturn(List.of(2L));
        when(userRepository.findUsernamesByIdIn(Set.of(2L))).thenReturn(List.of("alice"));

        List<ExportTaskEntity> result = exportTaskService.findReadableTasks();

        assertThat(result).extracting(ExportTaskEntity::getTaskId).containsExactly("t-a");
    }

    @Test
    void findReadableTasksUnrestrictedShouldStillScopeToActiveTenant() {
        persistTask("t1", 1L, "1", "u1");
        persistTask("t2", 2L, "1", "u1");

        TenantContext.setActiveTenantId(1L);
        DataScopeContext.set(ResolvedDataScope.all());

        List<ExportTaskEntity> result = exportTaskService.findReadableTasks();

        assertThat(result).extracting(ExportTaskEntity::getTaskId).containsExactly("t1");
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
            "pv-it"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities())
        );
    }

    private void persistTask(String taskId, Long tenantId, String userId, String username) {
        ExportTaskEntity e = new ExportTaskEntity();
        e.setTaskId(taskId);
        e.setTenantId(tenantId);
        e.setUserId(userId);
        e.setUsername(username);
        e.setStatus(ExportTaskStatus.PENDING);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        exportTaskRepository.save(e);
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = ExportTaskEntity.class)
    @EnableJpaRepositories(basePackageClasses = ExportTaskRepository.class)
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JacksonAutoConfiguration.class
    })
    @Import({ ExportTaskService.class, IntegrationMocks.class })
    static class TestApplication {
    }

    @Configuration
    static class IntegrationMocks {

        @Bean
        @Primary
        TenantUserRepository tenantUserRepository() {
            return Mockito.mock(TenantUserRepository.class);
        }

        @Bean
        @Primary
        UserUnitRepository userUnitRepository() {
            return Mockito.mock(UserUnitRepository.class);
        }

        @Bean
        @Primary
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        @Primary
        TenantQuotaService tenantQuotaService() {
            return Mockito.mock(TenantQuotaService.class);
        }
    }
}
