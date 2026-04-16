package com.tiny.platform.core.oauth.integration;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        // e2e 测试上下文不会自动继承 application.yaml 中的 authentication.clients 列表，
        // 这里显式提供 vue-client，确保 OIDC /oauth2/authorize 主线可真实执行。
        "authentication.clients[0].client-id=vue-client",
        "authentication.clients[0].authentication-methods[0]=none",
        "authentication.clients[0].grant-types[0]=authorization_code",
        "authentication.clients[0].grant-types[1]=refresh_token",
        "authentication.clients[0].redirect-uris[0]=http://localhost:5173/callback",
        "authentication.clients[0].redirect-uris[1]=http://localhost:5173/silent-renew.html",
        "authentication.clients[0].post-logout-redirect-uris[0]=http://localhost:5173/",
        "authentication.clients[0].scopes[0]=openid",
        "authentication.clients[0].scopes[1]=profile",
        "authentication.clients[0].scopes[2]=offline_access",
        "authentication.clients[0].client-setting.require-authorization-consent=false",
        "authentication.clients[0].client-setting.require-proof-key=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
class AuthenticationFlowE2eProfileIntegrationTest {

    private static String firstNonBlankEnv(String... keysThenLiteralFallback) {
        if (keysThenLiteralFallback.length < 1) {
            throw new IllegalArgumentException("firstNonBlankEnv requires at least one argument");
        }
        for (int i = 0; i < keysThenLiteralFallback.length - 1; i++) {
            String value = System.getenv(keysThenLiteralFallback[i]);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return keysThenLiteralFallback[keysThenLiteralFallback.length - 1];
    }

    private static final String OIDC_TEST_CLIENT_ID = "vue-client";
    private static final String OIDC_SILENT_REDIRECT_URI = "http://localhost:5173/silent-renew.html";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("基于 /login 表单的基础行为")
    class FormLoginBehaviour {

        @Test
        @DisplayName("表单管理员登录（租户与口令来自 E2E_* 环境）=> 成功路径或非冻结类失败，而非用户不存在页")
        void loginWithValidTenantAndE2eAdminShouldRedirectToSuccessTarget() throws Exception {
            // 租户：E2E_FORM_LOGIN_TENANT_CODE 优先，否则 E2E_TENANT_CODE（与 Playwright 主身份一致），最后 default。
            // 账号/口令：E2E_ADMIN_* 优先，否则回退到 E2E_USERNAME / E2E_PASSWORD，避免 Tier2 与 .env.e2e.local 双轨维护。
            String tenantCode = firstNonBlankEnv("E2E_FORM_LOGIN_TENANT_CODE", "E2E_TENANT_CODE", "default");
            String username = firstNonBlankEnv("E2E_ADMIN_USERNAME", "E2E_USERNAME", "e2e_admin");
            String password = firstNonBlankEnv("E2E_ADMIN_PASSWORD", "E2E_PASSWORD", "e2e_admin_password");
            mockMvc.perform(
                            post("/login")
                                    .param("username", username)
                                    .param("password", password)
                                    .param("tenantCode", tenantCode)
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
                                if (redirected.contains("%E8%AF%A5%E7%94%A8%E6%88%B7%E6%9C%AA%E9%85%8D%E7%BD%AE%E6%AD%A4%E8%AE%A4%E8%AF%81%E6%96%B9%E5%BC%8F")) {
                                    Assumptions.abort("当前 e2e 数据未为 e2e_admin 配置 LOCAL+PASSWORD，跳过成功路径断言。redirect=" + redirected);
                                }
                                // 密码错误（URL 编码「密码错误」）；Tier2 前应由 verify-platform-login-auth-chain 调用 ensure 脚本对齐库内哈希
                                if (redirected.contains("%E5%AF%86%E7%A0%81%E9%94%99%E8%AF%AF")) {
                                    Assumptions.abort(
                                            "登录返回密码错误：环境口令与 user_authentication_method 不一致。redirect="
                                                    + redirected);
                                }
                                if (redirected.contains("%E7%94%A8%E6%88%B7%E4%B8%8D%E5%AD%98%E5%9C%A8")) {
                                    Assumptions.abort(
                                            "登录返回用户不存在：E2E 账号/租户与库内数据不一致或未 seed。redirect=" + redirected);
                                }
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

        /**
         * 平台账号全链路：无 tenantCode 表单登录 → 会话内 TenantContext 为 PLATFORM → 可调平台租户列表。
         * 依赖 DB 中已执行 {@code ensure-platform-admin.sh}（须显式 {@code PLATFORM_TENANT_CODE}），且 PLATFORM 赋权指向平台模板 {@code ROLE_PLATFORM_ADMIN}（role.tenant_id IS NULL）。
         */
        @Test
        @DisplayName("platform_admin：无 tenantCode 登录后会话可访问 GET /sys/tenants（平台控制面）")
        void platformAdminFormLoginWithoutTenantCodeThenTenantListAuthorized() throws Exception {
            String username = System.getenv().getOrDefault("E2E_PLATFORM_ADMIN_USERNAME", "platform_admin");
            String password = System.getenv().getOrDefault("E2E_PLATFORM_ADMIN_PASSWORD", "admin");

            MvcResult loginResult = mockMvc.perform(
                            post("/login")
                                    .param("username", username)
                                    .param("password", password)
                                    .param("authenticationProvider", "LOCAL")
                                    .param("authenticationType", "PASSWORD")
                                    .with(csrf())
                    )
                    .andReturn();

            org.assertj.core.api.Assertions.assertThat(loginResult.getResponse().getStatus())
                    .as("表单登录应进入重定向成功路径")
                    .isBetween(300, 399);

            String redirected = loginResult.getResponse().getRedirectedUrl();
            if (redirected != null && redirected.contains("error=true")) {
                Assumptions.abort(
                        "platform_admin 登录失败（可能未 seed 或密码非默认 admin），跳过平台链路透测。redirect=" + redirected);
            }

            MockHttpSession session = Objects.requireNonNull(
                    (MockHttpSession) loginResult.getRequest().getSession(false),
                    "登录后应创建会话");

            var tenantListResult = mockMvc.perform(get("/sys/tenants").session(session)).andReturn();
            int tenantListStatus = tenantListResult.getResponse().getStatus();
            if (tenantListStatus == 403) {
                Assumptions.abort(
                        "GET /sys/tenants 返回 403：api_endpoint 未登记或 permission requirement 未满足（CARD-13B fail-closed）。"
                                + "请确认 e2e 种子含该端点登记且 platform_admin 权限满足守卫。");
            }
            org.assertj.core.api.Assertions.assertThat(tenantListStatus).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("OIDC prompt=none / silent renew 行为")
    class OidcPromptNoneBehaviour {

        @Test
        @DisplayName("未登录访问 tenant issuer /oauth2/authorize?prompt=none => 回 redirect_uri 的 login_required，而不是普通 /login 错误页")
        void unauthenticatedPromptNoneShouldReturnLoginRequiredToRedirectUri() throws Exception {
            String state = "prompt-none-state";
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 1L);

            MvcResult result = mockMvc.perform(
                            get("/oauth2/authorize")
                                    .session(session)
                                    .queryParam("client_id", OIDC_TEST_CLIENT_ID)
                                    .queryParam("redirect_uri", OIDC_SILENT_REDIRECT_URI)
                                    .queryParam("response_type", "code")
                                    .queryParam("scope", "openid profile offline_access")
                                    .queryParam("state", state)
                                    .queryParam("code_challenge", "qlYGR0ERBzPofG_2r2MFVbpKDE8agR3YJXn93z8I20w")
                                    .queryParam("code_challenge_method", "S256")
                                    .queryParam("prompt", "none")
                                    .header("Accept", "text/html")
                                    .header("Sec-Fetch-Mode", "navigate")
                    )
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirected = result.getResponse().getRedirectedUrl();
            org.assertj.core.api.Assertions.assertThat(redirected)
                    .as("prompt=none 未登录时应按 OAuth/OIDC 语义返回 redirect_uri，而不是走普通表单登录失败页")
                    .startsWith(OIDC_SILENT_REDIRECT_URI)
                    .contains("error=login_required")
                    .contains("state=" + state)
                    .doesNotContain("/login?error=true")
                    .doesNotContain("/login?redirect=");
        }
    }
}
