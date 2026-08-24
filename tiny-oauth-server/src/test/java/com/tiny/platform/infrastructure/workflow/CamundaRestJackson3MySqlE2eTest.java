package com.tiny.platform.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import com.tiny.platform.core.oauth.security.ApiEndpointRequirementFilter;
import com.tiny.platform.core.oauth.session.UserSessionActivityFilter;
import com.tiny.platform.core.oauth.tenant.TenantContextFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"e2e", "camunda-rest-e2e"})
@EnabledIfEnvironmentVariable(named = "E2E_DB_PASSWORD", matches = ".+")
@EnabledIfSystemProperty(named = "tiny.camunda.rest.tests.enabled", matches = "true")
@Import({
        CamundaBpmRestJerseyAutoConfiguration.class,
        CamundaRestJackson3MySqlE2eTest.RestSecurityConfiguration.class
})
class CamundaRestJackson3MySqlE2eTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private CamundaBpmProperties camundaProperties;

    @Test
    void shouldRoundTripVariablesThroughNativeRestAndRealMySql() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processKey = "tinyJackson3E2e" + suffix;
        String deploymentId = null;

        HttpResponse<String> engines = get("/engine-rest/engine");
        assertThat(engines.statusCode()).isBetween(200, 299);
        assertThat(objectMapper.readTree(engines.body()).isArray()).isTrue();

        try {
            HttpResponse<String> deployment = deploy(processKey);
            assertThat(deployment.statusCode()).isBetween(200, 299);
            JsonNode deploymentBody = objectMapper.readTree(deployment.body());
            deploymentId = deploymentBody.path("id").asText();
            assertThat(deploymentId).isNotBlank();

            String startBody = """
                    {
                      "variables": {
                        "textValue": {"value": "hello", "type": "String"},
                        "countValue": {"value": 42, "type": "Integer"},
                        "flagValue": {"value": true, "type": "Boolean"},
                        "dateValue": {"value": "2026-08-24T00:00:00.000+0800", "type": "Date"}
                      }
                    }
                    """;
            HttpResponse<String> started = postJson(
                    "/engine-rest/engine/{engineName}/process-definition/key/{processKey}/start",
                    startBody,
                    camundaProperties.getProcessEngineName(), processKey);
            assertThat(started.statusCode()).isBetween(200, 299);
            String processInstanceId = objectMapper.readTree(started.body()).path("id").asText();
            assertThat(processInstanceId).isNotBlank();

            HttpResponse<String> variables = get(
                    "/engine-rest/engine/{engineName}/process-instance/{id}/variables",
                    camundaProperties.getProcessEngineName(), processInstanceId);
            assertThat(variables.statusCode()).isBetween(200, 299);
            JsonNode variableBody = objectMapper.readTree(variables.body());
            assertThat(variableBody.path("textValue").path("value").asText()).isEqualTo("hello");
            assertThat(variableBody.path("countValue").path("value").asInt()).isEqualTo(42);
            assertThat(variableBody.path("flagValue").path("value").asBoolean()).isTrue();
            assertThat(variableBody.path("dateValue").path("value").asText())
                    .isEqualTo("2026-08-23T16:00:00.000Z");

            HttpResponse<String> malformed = postJson(
                    "/engine-rest/engine/{engineName}/process-definition/key/{processKey}/start",
                    "{not-valid-json",
                    camundaProperties.getProcessEngineName(), processKey);
            assertThat(malformed.statusCode()).isBetween(400, 499);
        } finally {
            if (deploymentId != null && repositoryService.createDeploymentQuery().deploymentId(deploymentId).count() > 0) {
                repositoryService.deleteDeployment(deploymentId, true);
            }
        }
    }

    private HttpResponse<String> deploy(String processKey) throws Exception {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                             targetNamespace="http://tiny-platform.test/camunda-rest-e2e">
                  <process id="%s" isExecutable="true" camunda:historyTimeToLive="30">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <userTask id="wait" name="E2E wait state"/>
                  </process>
                </definitions>
                """.formatted(processKey);
        String boundary = "TinyPlatformBoundary" + UUID.randomUUID().toString().replace("-", "");
        String multipart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"deployment-name\"\r\n\r\n"
                + processKey + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"deployment-source\"\r\n\r\n"
                + "tiny-platform-real-mysql-e2e\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"data\"; filename=\"" + processKey + ".bpmn\"\r\n"
                + "Content-Type: application/xml\r\n\r\n"
                + bpmn + "\r\n"
                + "--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(uri(
                        "/engine-rest/engine/{engineName}/deployment/create",
                        camundaProperties.getProcessEngineName()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.getBytes(StandardCharsets.UTF_8)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, Object... values) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path, values)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postJson(String path, String body, Object... values) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path, values))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path, Object... values) {
        String resolved = path;
        for (Object value : values) {
            resolved = resolved.replaceFirst("\\{[^}]+}", value.toString());
        }
        return URI.create("http://127.0.0.1:" + port + resolved);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestSecurityConfiguration {

        @Bean
        @Order(0)
        SecurityFilterChain camundaRestE2eSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/engine-rest/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }

        @Bean
        FilterRegistrationBean<TenantContextFilter> disableTenantContextServletRegistration(
                TenantContextFilter filter) {
            return disabledRegistration(filter);
        }

        @Bean
        FilterRegistrationBean<ApiEndpointRequirementFilter> disableApiRequirementServletRegistration(
                ApiEndpointRequirementFilter filter) {
            return disabledRegistration(filter);
        }

        @Bean
        FilterRegistrationBean<UserSessionActivityFilter> disableSessionActivityServletRegistration(
                UserSessionActivityFilter filter) {
            return disabledRegistration(filter);
        }

        private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
            FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }
}
