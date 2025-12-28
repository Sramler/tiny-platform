package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsJacksonDeserializer;
import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.config.MultiFactorAuthenticationTokenJacksonDeserializer;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 配置类。
 * <p>
 * <b>核心职责：分离前后端请求和 OAuth2 授权服务的 ObjectMapper 配置</b>
 * <p>
 * 通过两个独立的 ObjectMapper 实现职责分离：
 * <ul>
 *   <li><b>webObjectMapper（@Primary）</b>：用于前后端 HTTP 请求/响应的 JSON 序列化/反序列化
 *       <ul>
 *         <li>不包含 Security/OAuth2 模块，避免类型信息污染业务 DTO</li>
 *         <li>通过 {@link #jacksonCustomizer()} 应用全局配置（时区、Long→String、时间格式等）</li>
 *       </ul>
 *   </li>
 *   <li><b>authorizationMapper（@Qualifier("authorizationMapper")）</b>：专用于 OAuth2 授权服务
 *       <ul>
 *         <li>显式注册 {@link SecurityJackson2Modules} 和 {@link OAuth2AuthorizationServerJackson2Module}</li>
 *         <li>确保 OAuth2AuthorizationRequest、Authentication、Instant 等复杂对象正确序列化/反序列化</li>
 *         <li>避免 {@code ClassCastException: LinkedHashMap cannot be cast to OAuth2AuthorizationRequest}</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * <b>关键设计原则</b>：
 * <ul>
 *   <li>前后端请求使用轻量级的 ObjectMapper，不加载 Security/OAuth2 模块，提升性能</li>
 *   <li>OAuth2 授权服务使用完整的 ObjectMapper，确保所有复杂对象正确序列化/反序列化</li>
 *   <li>通过 {@link #jacksonCustomizer()} 统一配置，避免重复代码</li>
 *   <li>通过 {@link #createCustomModule()} 共享自定义序列化器（Long→String、自定义反序列化器）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
public class JacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);

    /**
     * Web API 专用的 ObjectMapper（用于前后端 HTTP 请求/响应）。
     * <p>
     * <b>职责</b>：
     * <ul>
     *   <li>处理所有 HTTP 请求/响应的 JSON 序列化/反序列化</li>
     *   <li>不包含 Security/OAuth2 模块，避免全局默认类型信息影响业务 DTO</li>
     * </ul>
     * <p>
     * <b>配置来源</b>：
     * <ul>
     *   <li>通过 {@link Jackson2ObjectMapperBuilder} 构建，自动应用 {@link #jacksonCustomizer()} 的全局配置</li>
     *   <li>全局配置包括：UTC 时区、仅输出非 null、Long→String、ISO 时间格式、大小写不敏感枚举等</li>
     * </ul>
     * <p>
     * <b>不注册的模块</b>：
     * <ul>
     *   <li>{@link SecurityJackson2Modules}：避免全局默认类型信息影响业务 DTO</li>
     *   <li>{@link OAuth2AuthorizationServerJackson2Module}：OAuth2 对象仅在授权服务中使用</li>
     * </ul>
     * <p>
     * 通过 {@code @Primary} 标记为默认 ObjectMapper，Spring MVC 会自动使用此 ObjectMapper 进行 HTTP 消息转换。
     *
     * @param builder Jackson2ObjectMapperBuilder，已通过 {@link #jacksonCustomizer()} 定制
     * @return Web API 专用的 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper webObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 使用 builder 保证全局统一配置，不注册 Security/OAuth2 模块
        return builder.build();
    }

    /**
     * OAuth2 授权服务专用的 ObjectMapper（用于 JdbcOAuth2AuthorizationService）。
     * <p>
     * <b>职责</b>：
     * <ul>
     *   <li>处理 OAuth2 授权流程中的复杂对象序列化/反序列化</li>
     *   <li>确保 {@link org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest}、
     *       {@link org.springframework.security.core.Authentication}、{@link java.time.Instant} 等对象正确序列化/反序列化</li>
     * </ul>
     * <p>
     * <b>注册的模块</b>：
     * <ul>
     *   <li>{@link SecurityJackson2Modules}：支持 {@link org.springframework.security.core.Authentication}、
     *       {@link org.springframework.security.core.GrantedAuthority} 等 Spring Security 对象</li>
     *   <li>{@link OAuth2AuthorizationServerJackson2Module}：支持 {@link org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest}、
     *       {@link org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse} 等 OAuth2 对象</li>
     *   <li>{@link JavaTimeModule}：支持 {@link java.time.Instant} 等 Java 8 时间类型</li>
     *   <li>{@link #createCustomModule()}：支持 {@link MultiFactorAuthenticationToken}、
     *       {@link CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails} 等自定义对象</li>
     * </ul>
     * <p>
     * <b>关键修复点</b>：
     * <ul>
     *   <li>必须显式注册所有模块，否则复杂对象会被反序列化为 {@link java.util.LinkedHashMap}</li>
     *   <li>当代码尝试将 {@link java.util.LinkedHashMap} 强制转换为目标类型时，会抛出 {@link ClassCastException}</li>
     *   <li>必须在 {@code build()} 之后显式调用 {@code registerModules()}，不能仅依赖 Builder 配置</li>
     * </ul>
     * <p>
     * 在 {@link com.tiny.platform.core.oauth.config.OAuth2DataConfig#oauth2AuthorizationService} 中通过
     * {@code @Qualifier("authorizationMapper")} 注入使用。
     *
     * @param builder Jackson2ObjectMapperBuilder，已通过 {@link #jacksonCustomizer()} 定制
     * @return OAuth2 授权服务专用的 ObjectMapper
     */
    @Bean("authorizationMapper")
    public ObjectMapper authorizationMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build(); // 使用 builder 统一配置

        // -----------------------------
        // 1. 注册 Spring Security 模块
        // -----------------------------
        // 支持 Authentication、GrantedAuthority 等 Spring Security 对象的序列化/反序列化
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

        // -----------------------------
        // 2. 注册 OAuth2 Authorization Server 模块
        // -----------------------------
        // 支持 OAuth2AuthorizationRequest、OAuth2AuthorizationResponse 等 OAuth2 对象的序列化/反序列化
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // -----------------------------
        // 3. 自定义认证对象序列化/反序列化
        // -----------------------------
        // 支持 MultiFactorAuthenticationToken、CustomWebAuthenticationDetails 等自定义对象的反序列化
        mapper.registerModule(createCustomModule());

        // -----------------------------
        // 4. Java 8 时间类型支持
        // -----------------------------
        // 支持 Instant、LocalDateTime 等 Java 8 时间类型的序列化/反序列化
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 使用 ISO 格式字符串而非时间戳

        // -----------------------------
        // 5. 容忍未知字段和单值数组
        // -----------------------------
        // 允许 JSON 中包含未知字段（前后端字段演进兼容）
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 允许单值表示数组（提升兼容性）
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // -----------------------------
        // 6. 配置允许列表以支持基本 Java 类型（如 Long）- 备用方案
        // -----------------------------
        // 注意：虽然 SecurityUser 的 userId 字段已经通过 @JsonSerialize/@JsonDeserialize 注解
        // 序列化为 String，但保留此模块作为备用方案，以防其他 Long 类型字段需要支持
        // Spring Security 的 allowlist 默认不包含 java.lang.Long，需要显式添加
        // 这修复了反序列化 OAuth2 授权数据时的 IllegalArgumentException
        // LongAllowlistModule 会在 setupModule 时自动修改 allowlist
        mapper.registerModule(new LongAllowlistModule());

        // -----------------------------
        // 7. 验证 SecurityUser 序列化配置
        // -----------------------------
        // 确认 SecurityUser 的 userId 字段已正确配置自定义序列化器/反序列化器
        try {
            var securityUserClass = SecurityUser.class;
            var userIdMethod = securityUserClass.getMethod("getUserId");
            var serializeAnnotation = userIdMethod.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonSerialize.class);
            var deserializeAnnotation = userIdMethod.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonDeserialize.class);
            
            if (serializeAnnotation != null && deserializeAnnotation != null) {
                log.info("[JacksonConfig] ✓ SecurityUser.userId 已配置自定义序列化器: {}, 反序列化器: {}", 
                        serializeAnnotation.using().getSimpleName(),
                        deserializeAnnotation.using().getSimpleName());
            } else {
                log.warn("[JacksonConfig] ⚠ SecurityUser.userId 未找到序列化/反序列化注解");
            }
        } catch (Exception e) {
            log.warn("[JacksonConfig] 无法验证 SecurityUser.userId 配置: {}", e.getMessage());
        }

        return mapper;
    }

    /**
     * 创建自定义 Jackson 模块。
     * <p>
     * <b>包含的内容</b>：
     * <ul>
     *   <li><b>自定义对象反序列化器</b>：
     *       <ul>
     *         <li>{@link MultiFactorAuthenticationToken}：多因素认证 Token 的反序列化</li>
     *         <li>{@link CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails}：自定义认证详情的反序列化</li>
     *       </ul>
     *   </li>
     *   <li><b>Long 序列化器</b>：将 Long 以字符串输出，避免前端 JS 精度丢失（JavaScript 的 Number 类型只能安全表示 -2^53 到 2^53 的整数）</li>
     * </ul>
     * <p>
     * 此模块被 {@link #webObjectMapper(Jackson2ObjectMapperBuilder)} 和 {@link #authorizationMapper(Jackson2ObjectMapperBuilder)} 共享使用。
     *
     * @return 自定义模块
     */
    private SimpleModule createCustomModule() {
        SimpleModule module = new SimpleModule();

        // 自定义对象反序列化（用于 OAuth2 授权流程）
        module.addDeserializer(MultiFactorAuthenticationToken.class, new MultiFactorAuthenticationTokenJacksonDeserializer());
        module.addDeserializer(CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class,
                new CustomWebAuthenticationDetailsJacksonDeserializer());

        // Long -> String（避免前端 JS 精度丢失）
        // JavaScript 的 Number 类型使用 IEEE 754 双精度浮点数，只能安全表示 -2^53 到 2^53 的整数
        // 超过此范围的 Long 值在 JSON 传输到前端时会丢失精度，因此序列化为字符串
        module.addSerializer(Long.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);

        return module;
    }

    /**
     * 全局 Jackson2ObjectMapperBuilder 定制器。
     * <p>
     * 此定制器会影响所有通过 {@link Jackson2ObjectMapperBuilder} 创建的 ObjectMapper，
     * 包括 {@link #webObjectMapper(Jackson2ObjectMapperBuilder)} 和 {@link #authorizationMapper(Jackson2ObjectMapperBuilder)}。
     * <p>
     * <b>配置项</b>：
     * <ul>
     *   <li><b>时区</b>：UTC，避免多部署地区造成时间偏移</li>
     *   <li><b>序列化包含</b>：仅输出非 null 字段，减少冗余字段</li>
     *   <li><b>日期时间</b>：禁用时间戳，使用 ISO 格式字符串（如 "2024-01-01T12:00:00"）</li>
     *   <li><b>枚举</b>：大小写不敏感，降低前后端耦合</li>
     *   <li><b>数组</b>：支持单值表示数组，提升兼容性（如 "value" 可解析为 ["value"]）</li>
     *   <li><b>Long</b>：以字符串输出，避免前端 JS 精度丢失</li>
     * </ul>
     *
     * @return Builder 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 统一时区为 UTC，避免多部署地区造成时间偏移
            builder.timeZone(TimeZone.getTimeZone("UTC"));
            // 统一仅输出非 null 字段，减少冗余字段
            builder.serializationInclusion(JsonInclude.Include.NON_NULL);

            // 禁止 timestamps 写入，使用 ISO 格式字符串（如 "2024-01-01T12:00:00"）
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // 支持大小写不敏感的 Enum，降低前后端耦合
            builder.featuresToEnable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

            // 支持单值数组，提升兼容性（如 "value" 可解析为 ["value"]）
            builder.featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

            // 类型绑定
            // LocalDateTime 使用 ISO_LOCAL_DATE_TIME 格式（如 "2024-01-01T12:00:00"）
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            // Long 以字符串输出，避免前端 JS 精度丢失
            builder.serializerByType(Long.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
        };
    }
}
