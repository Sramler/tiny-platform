package com.tiny.platform.infrastructure.scheduling.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.security.SchedulingAccessGuard;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证调度控制器在启用 Spring Method Security 时的 RBAC 拒绝路径。
 * 使用最小上下文（仅 controller + Method Security + 守卫），确保 @PreAuthorize 真实生效，
 * 而非 standaloneSetup 下的“未执行权限检查”。不加载 OauthServerApplication 以免拉取整应用依赖。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = SchedulingControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class SchedulingControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        SchedulingControllerRbacTestConfig.class,
        SchedulingController.class,
        org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchedulingService schedulingService;

    @MockBean
    private QuartzSchedulerService quartzSchedulerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setTenantContext() {
        TenantContext.setActiveTenantId(10L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("READ (getTaskType)")
    class ReadEndpoint {

        @Test
        @DisplayName("scheduling:console:view -> 200")
        void allowsWhenHasReadAuthority() throws Exception {
            SchedulingTaskType taskType = new SchedulingTaskType();
            taskType.setId(1L);
            taskType.setTenantId(10L);
            taskType.setCode("demo");
            when(schedulingService.getTaskType(1L)).thenReturn(Optional.of(taskType));

            mockMvc.perform(get("/scheduling/task-type/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(user("reader").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("scheduling:* -> 200")
        void allowsWhenHasWildcardAuthority() throws Exception {
            when(schedulingService.getTaskType(1L)).thenReturn(Optional.of(new SchedulingTaskType()));

            mockMvc.perform(get("/scheduling/task-type/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(user("admin").authorities(new SimpleGrantedAuthority("scheduling:*"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("only scheduling:console:config -> 403")
        void deniesWhenOnlyManageConfig() throws Exception {
            mockMvc.perform(get("/scheduling/task-type/1")
                    .with(user("configOnly").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.MANAGE_CONFIG_AUTHORITY))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("anonymous -> 401 or 403")
        @WithAnonymousUser
        void deniesWhenAnonymous() throws Exception {
            mockMvc.perform(get("/scheduling/task-type/1"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("MANAGE_CONFIG (createTaskType)")
    class ManageConfigEndpoint {

        @Test
        @DisplayName("scheduling:console:config -> 200")
        void allowsWhenHasManageConfigAuthority() throws Exception {
            SchedulingTaskType created = new SchedulingTaskType();
            created.setId(1L);
            created.setCode("demo");
            when(schedulingService.createTaskType(any(SchedulingTaskTypeCreateUpdateDto.class))).thenReturn(created);

            SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
            dto.setCode("demo");
            dto.setName("Demo");

            mockMvc.perform(post("/scheduling/task-type")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("config").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.MANAGE_CONFIG_AUTHORITY))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("only scheduling:console:view -> 403")
        void deniesWhenOnlyRead() throws Exception {
            SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
            dto.setCode("demo");
            dto.setName("Demo");

            mockMvc.perform(post("/scheduling/task-type")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("reader").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("OPERATE_RUN (triggerDag)")
    class OperateRunEndpoint {

        @Test
        @DisplayName("scheduling:run:control -> 200")
        void allowsWhenHasOperateRunAuthority() throws Exception {
            SchedulingDagRun run = new SchedulingDagRun();
            run.setId(1L);
            when(schedulingService.triggerDag(10L)).thenReturn(run);

            mockMvc.perform(post("/scheduling/dag/10/trigger")
                    .with(user("operator").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.OPERATE_RUN_AUTHORITY))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("scheduling:* -> 200")
        void allowsWhenHasWildcardAuthority() throws Exception {
            when(schedulingService.triggerDag(10L)).thenReturn(new SchedulingDagRun());

            mockMvc.perform(post("/scheduling/dag/10/trigger")
                    .with(user("admin").authorities(new SimpleGrantedAuthority("scheduling:*"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("only scheduling:console:view -> 403")
        void deniesWhenOnlyRead() throws Exception {
            mockMvc.perform(post("/scheduling/dag/10/trigger")
                    .with(user("reader").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("VIEW_AUDIT (listAudits)")
    class ViewAuditEndpoint {

        @Test
        @DisplayName("scheduling:audit:view -> 200")
        void allowsWhenHasViewAuditAuthority() throws Exception {
            when(schedulingService.listAudits(eq(null), eq(null), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/scheduling/audit/list")
                    .accept(MediaType.APPLICATION_JSON)
                    .param("page", "0").param("size", "10")
                    .with(user("auditor").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.VIEW_AUDIT_AUTHORITY))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("only scheduling:console:view -> 403")
        void deniesWhenOnlyRead() throws Exception {
            mockMvc.perform(get("/scheduling/audit/list")
                    .param("page", "0").param("size", "10")
                    .with(user("reader").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("VIEW_CLUSTER_STATUS (getQuartzClusterStatus)")
    class ViewClusterStatusEndpoint {

        @Test
        @DisplayName("scheduling:cluster:view -> 200")
        void allowsWhenHasViewClusterStatusAuthority() throws Exception {
            QuartzSchedulerService.ClusterStatusInfo info = new QuartzSchedulerService.ClusterStatusInfo(
                    "test", "instance-1", false, 0, System.currentTimeMillis(), true, false);
            when(quartzSchedulerService.getClusterStatus()).thenReturn(info);

            mockMvc.perform(get("/scheduling/quartz/cluster-status")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(user("ops").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.VIEW_CLUSTER_STATUS_AUTHORITY))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("only scheduling:console:view -> 403")
        void deniesWhenOnlyRead() throws Exception {
            mockMvc.perform(get("/scheduling/quartz/cluster-status")
                    .with(user("reader").authorities(new SimpleGrantedAuthority(SchedulingAccessGuard.READ_AUTHORITY))))
                    .andExpect(status().isForbidden());
        }
    }
}
