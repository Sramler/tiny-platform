package com.tiny.platform.application.controller.menu;

import com.tiny.platform.core.oauth.config.jackson.JacksonConfig;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = MenuTreeSerializationIntegrationTest.TestApp.class
)
@AutoConfigureMockMvc(addFilters = false)
class MenuTreeSerializationIntegrationTest {

    @SpringBootConfiguration
    @Import({
            JacksonConfig.class,
            MenuController.class,
            HttpMessageConvertersAutoConfiguration.class,
            WebMvcAutoConfiguration.class
    })
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @MockitoBean
    private MenuService menuService;

    @Test
    void sysMenusTreeShouldSerializeAsPlainJsonArrayWithoutJackson3TypeWrappers() throws Exception {
        when(menuService.menuTree()).thenReturn(List.of(directoryWithChildMenu()));

        String body = mockMvc.perform(get("/sys/menus/tree"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("\"name\":\"system\"")
                .contains("\"children\":[")
                .contains("\"name\":\"tenant\"")
                .doesNotContain("java.util.ArrayList")
                .doesNotContain("\"@class\"");
    }

    @Test
    void webMvcShouldNotRegisterJackson3HttpMessageConverterForRegularControllers() {
        List<String> converterClassNames = requestMappingHandlerAdapter.getMessageConverters().stream()
                .map(HttpMessageConverter::getClass)
                .map(Class::getName)
                .toList();

        assertThat(converterClassNames)
                .contains("org.springframework.http.converter.json.MappingJackson2HttpMessageConverter")
                .doesNotContain("org.springframework.http.converter.json.JacksonJsonHttpMessageConverter");
    }

    private ResourceResponseDto directoryWithChildMenu() {
        ResourceResponseDto child = new ResourceResponseDto();
        child.setId(646L);
        child.setName("tenant");
        child.setTitle("租户管理");
        child.setUrl("/system/tenant");
        child.setPermission("system:tenant:list");
        child.setType(1);
        child.setLeaf(true);
        child.setEnabled(true);
        child.setChildren(List.of());

        ResourceResponseDto root = new ResourceResponseDto();
        root.setId(312L);
        root.setName("system");
        root.setTitle("系统管理");
        root.setUrl("/system");
        root.setPermission("system:tenant:list");
        root.setType(0);
        root.setLeaf(false);
        root.setEnabled(true);
        root.setChildren(List.of(child));
        return root;
    }
}
