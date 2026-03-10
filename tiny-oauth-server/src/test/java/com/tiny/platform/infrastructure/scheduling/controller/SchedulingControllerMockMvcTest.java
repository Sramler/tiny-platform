package com.tiny.platform.infrastructure.scheduling.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingException;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调度控制器 MockMvc 测试（参照 ExportControllerMockMvcTest）。
 * 验证 HTTP 序列化、参数校验、SchedulingException 转 ProblemDetail。
 */
class SchedulingControllerMockMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SchedulingService schedulingService;
    private QuartzSchedulerService quartzSchedulerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        schedulingService = mock(SchedulingService.class);
        quartzSchedulerService = mock(QuartzSchedulerService.class);
        SchedulingController controller = new SchedulingController(schedulingService, quartzSchedulerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new OAuthServerExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate() {
        TenantContext.setTenantId(10L);
        SecurityUser user = new SecurityUser(1L, 10L, "alice", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "N/A", List.of()));
    }

    @Test
    void getTaskTypeShouldReturn200WithBodyWhenExists() throws Exception {
        authenticate();
        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(1L);
        taskType.setTenantId(10L);
        taskType.setCode("demo");
        taskType.setName("Demo");
        when(schedulingService.getTaskType(1L)).thenReturn(Optional.of(taskType));

        mockMvc.perform(get("/scheduling/task-type/1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("demo"));
    }

    @Test
    void getTaskTypeShouldReturn404WhenNotExists() throws Exception {
        authenticate();
        when(schedulingService.getTaskType(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/scheduling/task-type/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getTaskShouldReturn404WhenNotExists() throws Exception {
        authenticate();
        when(schedulingService.getTask(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/scheduling/task/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void triggerDagShouldReturn200WhenServiceSucceeds() throws Exception {
        authenticate();
        com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun run =
            new com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun();
        run.setId(1L);
        when(schedulingService.triggerDag(10L)).thenReturn(run);

        mockMvc.perform(post("/scheduling/dag/10/trigger").param("triggeredBy", "alice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void triggerDagShouldReturnProblemDetailWhenServiceThrowsSchedulingException() throws Exception {
        authenticate();
        when(schedulingService.triggerDag(10L))
            .thenThrow(new SchedulingException(ErrorCode.FORBIDDEN, "当前请求未解析到有效租户上下文"));

        mockMvc.perform(post("/scheduling/dag/10/trigger"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("租户")));
    }

    @Test
    void listTaskTypesShouldReturnPageWhenAuthenticated() throws Exception {
        authenticate();
        SchedulingTaskType tt = new SchedulingTaskType();
        tt.setId(1L);
        tt.setCode("billing");
        when(schedulingService.listTaskTypes(eq(null), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(tt)));

        mockMvc.perform(get("/scheduling/task-type/list")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].code").value("billing"));
    }

    @Test
    void getTaskInstanceLogShouldReturn404WhenServiceReturnsEmpty() throws Exception {
        authenticate();
        when(schedulingService.getTaskInstanceLog(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/scheduling/task-instance/999/log"))
            .andExpect(status().isNotFound());
    }
}
