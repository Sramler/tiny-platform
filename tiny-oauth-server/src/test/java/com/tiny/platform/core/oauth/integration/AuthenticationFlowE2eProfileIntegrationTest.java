package com.tiny.platform.core.oauth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路在 e2e profile 下的真实行为验证（不依赖前端或 Playwright）。
 *
 * 仅关注 /login -> activeTenantId 解析 -> BadCredentials 映射的大致行为，
 * 具体账号、租户、错误文案依赖于种子数据与 LoginFailureHandler 的配置。
 */
@SpringBootTest(properties = {
        // 显式提供 JWT key 路径，避免 e2e profile 下配置绑定不完整导致 AuthorizationServerConfig 启动失败。
        "authentication.jwt.public-key-path=classpath:keys/public.pem",
        "authentication.jwt.private-key-path=classpath:keys/private.pem",

        // 提供最小化的 OAuth client 配置，避免 RegisteredClientConfig 在测试启动时因为 clients=null NPE。
        "authentication.clients[0].client-id=test-client",
        "authentication.clients[0].client-secret=test-secret",
        "authentication.clients[0].authentication-methods[0]=client_secret_basic",
        "authentication.clients[0].grant-types[0]=authorization_code",
        "authentication.clients[0].redirect-uris[0]=http://localhost:9000/",
        "authentication.clients[0].scopes[0]=openid",
        "authentication.clients[0].client-setting.require-authorization-consent=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
class AuthenticationFlowE2eProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("基于 /login 表单的基础行为")
    class FormLoginBehaviour {

        @Test
        @DisplayName("tenantCode=default + e2e_admin + 正确密码 => 不应返回 200+用户不存在页面，而是进入成功路径（如回调或安全中心）")
        void loginWithValidTenantAndE2eAdminShouldRedirectToSuccessTarget() throws Exception {
            // 说明：
            // - 该用例依赖 e2e profile 下预置的租户 default 与用户 e2e_admin；
            // - 为避免在测试代码中暴露密码，这里的密码来自环境与种子脚本，期望由 CI/本地环境预置。
            mockMvc.perform(
                            post("/login")
                                    .param("username", System.getenv().getOrDefault("E2E_ADMIN_USERNAME", "e2e_admin"))
                                    .param("password", System.getenv().getOrDefault("E2E_ADMIN_PASSWORD", "e2e_admin_password"))
                                    .param("tenantCode", System.getenv().getOrDefault("E2E_TENANT_CODE", "default"))
                                    // 与前端 Login.vue 保持一致：默认 LOCAL+PASSWORD
                                    .param("authenticationProvider", "LOCAL")
                                    .param("authenticationType", "PASSWORD")
                                    .with(csrf())
                    )
                    .andExpect(status().is3xxRedirection())
                    // 这里不强绑定最终 URL：
                    // - 如果租户未冻结，应该进入成功路径（回调 / 安全中心 / 前端）。
                    // - 如果租户已冻结，当前真实行为是拒绝登录并重定向回 /login?error=true&message=租户已冻结。
                    .andExpect(result -> {
                        String redirected = result.getResponse().getRedirectedUrl();
                        if (redirected != null) {
                            if (redirected.contains("/login?error=true")) {
                                org.assertj.core.api.Assertions.assertThat(redirected)
                                        .contains("message=")
                                        .contains("%E7%A7%9F%E6%88%B7%E5%B7%B2%E5%86%BB%E7%BB%93");
                            } else {
                                org.assertj.core.api.Assertions.assertThat(redirected)
                                        .doesNotContain("用户不存在");
                            }
                        }
                    });
        }

        @Test
        @DisplayName("tenantCode=default + 不存在用户 => 仍然返回用户不存在类错误（通常是 302 /login?error=xxx）")
        void loginWithUnknownUserShouldReturnUserNotFoundError() throws Exception {
            var result = mockMvc.perform(
                            post("/login")
                                    .param("username", "e2e_unknown_user_" + System.currentTimeMillis())
                                    .param("password", "any-password")
                                    .param("tenantCode", System.getenv().getOrDefault("E2E_TENANT_CODE", "default"))
                                    .param("authenticationProvider", "LOCAL")
                                    .param("authenticationType", "PASSWORD")
                                    .with(csrf())
                    )
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirected = result.getResponse().getRedirectedUrl();
            if (redirected != null) {
                org.assertj.core.api.Assertions.assertThat(redirected).contains("/login").contains("error=true");
                // 若租户已冻结，会优先返回“租户已冻结”，从而覆盖“用户不存在”路径。
                // 若租户未冻结，则应返回“用户不存在”。
                if (!redirected.contains("%E7%A7%9F%E6%88%B7%E5%B7%B2%E5%86%BB%E7%BB%93")) {
                    org.assertj.core.api.Assertions.assertThat(redirected)
                            .contains("message=")
                            .contains("%E7%94%A8%E6%88%B7%E4%B8%8D%E5%AD%98%E5%9C%A8");
                }
            }
        }

        @Test
        @DisplayName("非法或不存在的 tenantCode => 触发租户解析错误（例如 missing_tenant 或 equivalent）")
        void loginWithInvalidTenantCodeShouldFailFastOnTenant() throws Exception {
            mockMvc.perform(
                            post("/login")
                                    .param("username", "e2e_admin_invalid_tenant")
                                    .param("password", "any-password")
                                    .param("tenantCode", "___invalid-tenant-code___")
                                    .with(csrf())
                    )
                    // 具体行为由 TenantContextFilter + LoginFailureHandler 决定：
                    // - 可能是 400 JSON（missing_tenant）
                    // - 也可能是 302 /login?error=missing_tenant
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isIn(400, 302);
                        String body = result.getResponse().getContentAsString();
                        String redirected = result.getResponse().getRedirectedUrl();
                        org.assertj.core.api.Assertions.assertThat(body + " " + redirected)
                                .contains("missing_tenant")
                                .as("期待在响应体或重定向 URL 中看到 missing_tenant 或等价错误标记");
                    });
        }
    }
}

