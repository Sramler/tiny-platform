package com.tiny.platform.core.oauth.security;

import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB drift gate between Spring MVC controller mappings and enabled api_endpoint carriers.
 *
 * <p>The class intentionally ends in {@code IT}; the ordinary Surefire unit suite does not pick it
 * up. {@code scripts/verify-api-endpoint-controller-drift.sh} runs it explicitly against a migrated
 * MySQL database so the gate validates the real Liquibase result rather than an H2 approximation.</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "authentication.jwt.public-key-path=classpath:keys/public.pem",
        "authentication.jwt.private-key-path=classpath:keys/private.pem",
        "authentication.clients[0].client-id=api-endpoint-drift-gate",
        "authentication.clients[0].client-secret=drift-gate-secret",
        "authentication.clients[0].authentication-methods[0]=client_secret_basic",
        "authentication.clients[0].grant-types[0]=authorization_code",
        "authentication.clients[0].redirect-uris[0]=http://localhost:9000/",
        "authentication.clients[0].scopes[0]=openid",
        "authentication.clients[0].client-setting.require-authorization-consent=false"
    }
)
@ActiveProfiles("e2e")
class ApiEndpointControllerMappingDriftIT {

    private static final Pattern PLACEHOLDER_SEGMENT = Pattern.compile("^\\{[^/{}]+}$");

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private List<SecurityFilterChain> securityFilterChains;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void guardedControllerMappingsMustHaveCompleteUnambiguousRuntimeCarriers() throws Exception {
        List<EndpointRow> endpoints = loadEnabledEndpoints();
        Set<MappingKey> guardedMappings = resolveGuardedControllerMappings();

        Map<RuntimeKey, List<EndpointRow>> endpointsByRuntimeKey = new LinkedHashMap<>();
        for (EndpointRow endpoint : endpoints) {
            RuntimeKey key = new RuntimeKey(
                endpoint.scopeKey(),
                normalizeMethod(endpoint.method()),
                normalizeTemplate(endpoint.uri())
            );
            endpointsByRuntimeKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(endpoint);
        }

        List<String> ambiguous = endpointsByRuntimeKey.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> entry.getKey() + " -> ids=" + entry.getValue().stream()
                .map(EndpointRow::id)
                .sorted()
                .toList())
            .sorted()
            .toList();

        Map<MappingKey, List<EndpointRow>> carriersByMapping = new LinkedHashMap<>();
        for (MappingKey mapping : guardedMappings) {
            List<EndpointRow> carriers = endpoints.stream()
                .filter(endpoint -> normalizeMethod(endpoint.method()).equals(mapping.method()))
                .filter(endpoint -> normalizeTemplate(endpoint.uri()).equals(mapping.template()))
                .sorted(Comparator.comparing(EndpointRow::id))
                .toList();
            carriersByMapping.put(mapping, carriers);
        }

        List<String> missing = carriersByMapping.entrySet().stream()
            .filter(entry -> entry.getValue().isEmpty())
            .map(entry -> entry.getKey().toString())
            .sorted()
            .toList();

        List<String> incomplete = carriersByMapping.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .filter(endpoint -> endpoint.requiredPermissionId() == null || endpoint.requirementCount() == 0)
                .map(endpoint -> entry.getKey() + " -> endpointId=" + endpoint.id()
                    + ", scope=" + endpoint.scopeKey()
                    + ", requiredPermissionId=" + endpoint.requiredPermissionId()
                    + ", requirementCount=" + endpoint.requirementCount()))
            .sorted()
            .toList();

        assertThat(ambiguous)
            .as("同一 scope + method + 运行时等价模板只能存在一个已启用 api_endpoint")
            .isEmpty();
        assertThat(missing)
            .as("经过 ApiEndpointRequirementFilter 且未精确豁免的 Controller 映射必须有已启用载体")
            .isEmpty();
        assertThat(incomplete)
            .as("映射命中的已启用载体必须同时具备 required_permission_id 与 requirement 行")
            .isEmpty();
    }

    private Set<MappingKey> resolveGuardedControllerMappings() throws Exception {
        Set<MappingKey> mappings = new TreeSet<>(Comparator
            .comparing(MappingKey::method)
            .thenComparing(MappingKey::template));
        List<String> methodlessMappings = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
            : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            // Camunda-disabled mode exposes one explicit 503 fallback for every method under
            // /process and /process/**. Those wildcard envelopes must never become permission
            // carriers; enabled mode is governed by the concrete /process/* endpoint templates.
            if ("com.tiny.platform.application.oauth.workflow.ProcessDisabledFallbackController"
                .equals(handler.getBeanType().getName())) {
                continue;
            }
            Package handlerPackage = handler.getBeanType().getPackage();
            if (handlerPackage == null || !handlerPackage.getName().startsWith("com.tiny.platform")) {
                continue;
            }

            Set<String> paths = entry.getKey().getPatternValues();
            Set<RequestMethod> requestMethods = entry.getKey().getMethodsCondition().getMethods();
            if (requestMethods.isEmpty()) {
                for (String path : paths) {
                    String concretePath = materializeControllerPath(path);
                    for (String probeMethod : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
                        MockHttpServletRequest probe = new MockHttpServletRequest(probeMethod, concretePath);
                        probe.setServletPath(concretePath);
                        ApiEndpointRequirementFilter requirementFilter = findRequirementFilter(probe);
                        if (requirementFilter != null && !requirementFilter.shouldNotFilter(probe)) {
                            methodlessMappings.add(handler.getBeanType().getSimpleName() + " " + path);
                            break;
                        }
                    }
                }
                continue;
            }

            for (String path : paths) {
                for (RequestMethod requestMethod : requestMethods) {
                    String method = requestMethod.name();
                    String concretePath = materializeControllerPath(path);
                    MockHttpServletRequest request = new MockHttpServletRequest(method, concretePath);
                    request.setServletPath(concretePath);

                    ApiEndpointRequirementFilter requirementFilter = findRequirementFilter(request);
                    if (requirementFilter == null || requirementFilter.shouldNotFilter(request)) {
                        continue;
                    }
                    mappings.add(new MappingKey(method, normalizeTemplate(path)));
                }
            }
        }

        assertThat(methodlessMappings)
            .as("业务 Controller 映射必须显式声明 HTTP method，避免漂移门禁无法建立 method+template 身份")
            .isEmpty();
        return mappings;
    }

    private ApiEndpointRequirementFilter findRequirementFilter(MockHttpServletRequest request) {
        for (SecurityFilterChain chain : securityFilterChains) {
            if (!chain.matches(request)) {
                continue;
            }
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof ApiEndpointRequirementFilter requirementFilter) {
                    return requirementFilter;
                }
            }
            return null;
        }
        return null;
    }

    private List<EndpointRow> loadEnabledEndpoints() {
        return jdbcTemplate.query(
            """
            SELECT endpoint_entry.id,
                   endpoint_entry.tenant_id,
                   endpoint_entry.resource_level,
                   endpoint_entry.method,
                   endpoint_entry.uri,
                   endpoint_entry.required_permission_id,
                   (SELECT COUNT(*)
                      FROM api_endpoint_permission_requirement requirement_entry
                     WHERE requirement_entry.api_endpoint_id = endpoint_entry.id) AS requirement_count
              FROM api_endpoint endpoint_entry
             WHERE endpoint_entry.enabled = 1
            """,
            (resultSet, rowNum) -> new EndpointRow(
                resultSet.getLong("id"),
                nullableLong(resultSet, "tenant_id"),
                resultSet.getString("resource_level"),
                resultSet.getString("method"),
                resultSet.getString("uri"),
                nullableLong(resultSet, "required_permission_id"),
                resultSet.getInt("requirement_count")
            )
        );
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String materializeControllerPath(String path) {
        String normalized = normalizePath(path);
        List<String> concreteSegments = new ArrayList<>();
        for (String segment : splitSegments(normalized)) {
            if (PLACEHOLDER_SEGMENT.matcher(segment).matches()) {
                concreteSegments.add("1");
            } else if ("**".equals(segment) || "*".equals(segment)) {
                concreteSegments.add("probe");
            } else {
                concreteSegments.add(segment);
            }
        }
        return "/" + String.join("/", concreteSegments);
    }

    private static String normalizeTemplate(String path) {
        String normalized = normalizePath(path);
        List<String> normalizedSegments = new ArrayList<>();
        for (String segment : splitSegments(normalized)) {
            normalizedSegments.add(PLACEHOLDER_SEGMENT.matcher(segment).matches() ? "{}" : segment);
        }
        return "/" + String.join("/", normalizedSegments);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        String normalized = path.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<String> splitSegments(String path) {
        if ("/".equals(path)) {
            return List.of();
        }
        return List.of(path.substring(1).split("/", -1));
    }

    private static String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    }

    private record MappingKey(String method, String template) {
        @Override
        public String toString() {
            return method + " " + template;
        }
    }

    private record RuntimeKey(String scope, String method, String template) {
        @Override
        public String toString() {
            return scope + " " + method + " " + template;
        }
    }

    private record EndpointRow(Long id,
                               Long tenantId,
                               String resourceLevel,
                               String method,
                               String uri,
                               Long requiredPermissionId,
                               int requirementCount) {
        private String scopeKey() {
            return (resourceLevel == null ? "UNKNOWN" : resourceLevel.trim().toUpperCase(Locale.ROOT))
                + ":" + (tenantId == null ? "PLATFORM" : tenantId);
        }
    }
}
