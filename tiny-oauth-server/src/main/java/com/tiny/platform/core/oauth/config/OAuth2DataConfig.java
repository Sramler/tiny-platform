package com.tiny.platform.core.oauth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper;

import javax.sql.DataSource;

/**
 * OAuth2 数据库持久化配置类。
 * <p>
 * <b>核心职责：配置 OAuth2 相关数据在数据库中的持久化存储</b>
 * <p>
 * 包含以下 Bean 配置：
 * <ul>
 *   <li><b>RegisteredClientRepository</b>：客户端信息存储（对应表：{@code oauth2_registered_client}）</li>
 *   <li><b>OAuth2AuthorizationService</b>：授权信息存储（对应表：{@code oauth2_authorization}）</li>
 *   <li><b>OAuth2AuthorizationConsentService</b>：授权同意存储（对应表：{@code oauth2_authorization_consent}）</li>
 * </ul>
 * <p>
 * <b>关键配置说明</b>：
 * <ul>
 *   <li>{@code oauth2AuthorizationService} 使用自定义的 {@code authorizationMapper}（来自 {@link com.tiny.platform.core.oauth.config.jackson.JacksonConfig}）</li>
 *   <li>自定义 ObjectMapper 包含 {@link org.springframework.security.jackson2.SecurityJackson2Modules} 和
 *       {@link org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module}</li>
 *   <li>确保 OAuth2AuthorizationRequest、Authentication、Instant 等复杂对象正确序列化/反序列化</li>
 *   <li>避免 {@code ClassCastException: LinkedHashMap cannot be cast to OAuth2AuthorizationRequest}</li>
 * </ul>
 * <p>
 * <b>Bean 命名说明</b>：
 * <ul>
 *   <li>{@code oauth2AuthorizationService} 使用自定义 Bean 名称，避免与 Camunda 的 {@code authorizationService} 冲突</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
public class OAuth2DataConfig {

    /**
     * 注册客户端仓库（Registered Client Repository）。
     * <p>
     * 用于存储和管理 OAuth2 客户端信息（client_id、client_secret、redirect_uri、scope 等）。
     * <p>
     * <b>对应数据表</b>：{@code oauth2_registered_client}
     * <p>
     * <b>使用场景</b>：
     * <ul>
     *   <li>OAuth2 授权流程中验证客户端身份</li>
     *   <li>获取客户端的配置信息（重定向 URI、作用域、授权类型等）</li>
     * </ul>
     *
     * @param jdbcTemplate JDBC 模板，用于数据库操作
     * @return 注册客户端仓库实例
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * OAuth2 授权服务（OAuth2 Authorization Service）。
     * <p>
     * 用于存储和管理 OAuth2 授权信息，包括授权码（authorization_code）、访问令牌（access_token）、
     * 刷新令牌（refresh_token）等。
     * <p>
     * <b>对应数据表</b>：{@code oauth2_authorization}
     * <p>
     * <b>关键配置</b>：
     * <ul>
     *   <li><b>自定义 ObjectMapper</b>：注入 {@code authorizationMapper}（来自 {@link com.tiny.platform.core.oauth.config.jackson.JacksonConfig}）</li>
     *   <li><b>OAuth2AuthorizationRowMapper</b>：用于将数据库行映射为 {@link org.springframework.security.oauth2.server.authorization.OAuth2Authorization} 对象</li>
     *   <li><b>OAuth2AuthorizationParametersMapper</b>：用于序列化/反序列化授权参数</li>
     * </ul>
     * <p>
     * <b>为什么需要自定义 ObjectMapper</b>：
     * <ul>
     *   <li>默认的 ObjectMapper 不包含 Spring Security 和 OAuth2 的 Jackson 模块</li>
     *   <li>缺少模块会导致复杂对象（如 OAuth2AuthorizationRequest、Authentication、Instant）被反序列化为 {@link java.util.LinkedHashMap}</li>
     *   <li>当代码尝试将 LinkedHashMap 强制转换为目标类型时，会抛出 {@link ClassCastException}</li>
     *   <li>自定义 ObjectMapper 显式注册了所有必要的模块，确保对象正确序列化/反序列化</li>
     * </ul>
     * <p>
     * <b>Bean 命名</b>：使用 {@code oauth2AuthorizationService} 避免与 Camunda 的 {@code authorizationService} 冲突。
     * <p>
     * <b>使用场景</b>：
     * <ul>
     *   <li>存储授权码（authorization_code）和相关的授权信息</li>
     *   <li>存储访问令牌（access_token）和刷新令牌（refresh_token）</li>
     *   <li>验证和刷新令牌</li>
     *   <li>撤销令牌</li>
     * </ul>
     *
     * @param dataSource 数据源
     * @param registeredClientRepository 注册客户端仓库
     * @param authorizationObjectMapper OAuth2 授权服务专用的 ObjectMapper（通过 {@code @Qualifier("authorizationMapper")} 注入）
     * @return OAuth2 授权服务实例
     * @see com.tiny.platform.core.oauth.config.jackson.JacksonConfig#authorizationMapper(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder)
     */
    @Bean(name = "oauth2AuthorizationService")
    public OAuth2AuthorizationService oauth2AuthorizationService(
            DataSource dataSource,
            RegisteredClientRepository registeredClientRepository,
            @Qualifier("authorizationMapper") ObjectMapper authorizationObjectMapper
    ) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);

        // 配置 rowMapper 使用自定义 ObjectMapper，确保正确反序列化复杂对象
        OAuth2AuthorizationRowMapper rowMapper = new OAuth2AuthorizationRowMapper(registeredClientRepository);
        rowMapper.setObjectMapper(authorizationObjectMapper);

        // 配置 parametersMapper 使用自定义 ObjectMapper，确保正确序列化/反序列化授权参数
        OAuth2AuthorizationParametersMapper parametersMapper = new OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(authorizationObjectMapper);

        // 将配置好的 mapper 设置到 service 中
        service.setAuthorizationRowMapper(rowMapper);
        service.setAuthorizationParametersMapper(parametersMapper);

        return service;
    }

    /**
     * OAuth2 授权同意服务（OAuth2 Authorization Consent Service）。
     * <p>
     * 用于存储和管理用户对客户端的授权同意信息（例如：用户同意授权的作用域、权限等）。
     * <p>
     * <b>对应数据表</b>：{@code oauth2_authorization_consent}
     * <p>
     * <b>使用场景</b>：
     * <ul>
     *   <li>存储用户对特定客户端的授权同意</li>
     *   <li>在后续授权请求中检查用户是否已同意授权</li>
     *   <li>支持"记住我的选择"功能，避免用户重复授权</li>
     * </ul>
     * <p>
     * <b>注意</b>：此服务主要用于授权码流程中的授权同意页面，如果应用不需要授权同意页面，
     * 可以不使用此服务。
     *
     * @param jdbcTemplate JDBC 模板，用于数据库操作
     * @param registeredClientRepository 注册客户端仓库
     * @return OAuth2 授权同意服务实例
     */
    @Bean
    public OAuth2AuthorizationConsentService customOAuth2AuthorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }
}
