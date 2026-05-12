package com.tiny.platform.application.oauth.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessDisabledFallbackControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new ProcessDisabledFallbackController())
        .build();

    @Test
    void processDefinitionsShouldReturnServiceUnavailableWhenEngineDisabled() throws Exception {
        mockMvc.perform(get("/process/definitions"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("workflow_engine_disabled"))
            .andExpect(jsonPath("$.message").value("当前运行环境未启用流程引擎，流程管理接口不可用"))
            .andExpect(jsonPath("$.path").value("/process/definitions"));
    }

    @Test
    void processMutatingRequestsShouldReturnSameExplicitDisabledResponse() throws Exception {
        mockMvc.perform(post("/process/deploy")
                .contentType(MediaType.APPLICATION_XML)
                .content("<bpmn/>"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("workflow_engine_disabled"))
            .andExpect(jsonPath("$.path").value("/process/deploy"));
    }
}
