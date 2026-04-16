package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Jackson 配置。
 *
 * <p>当前应用处于 Spring Boot 4 过渡态：Web / Camunda REST 仍然依赖 Jackson 2，
 * 而 Spring Security 7 的授权持久化链路已经切到 Jackson 3。因此这里显式维护两套
 * mapper：</p>
 *
 * <ul>
 *   <li>{@code webObjectMapper}：继续服务 Spring MVC / Camunda REST 的 Jackson 2</li>
 *   <li>{@code authorizationMapper}：专供 OAuth2 授权持久化使用的 Jackson 3 {@link JsonMapper}</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper webObjectMapper() {
        com.fasterxml.jackson.databind.json.JsonMapper mapper =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build();

        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        mapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        mapper.registerModule(javaTimeModule);

        com.fasterxml.jackson.databind.module.SimpleModule webModule =
                new com.fasterxml.jackson.databind.module.SimpleModule("TinyWebJacksonModule");
        webModule.addSerializer(Long.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
        webModule.addSerializer(Long.TYPE, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
        mapper.registerModule(webModule);

        return mapper;
    }

    @Bean("authorizationMapper")
    public JsonMapper authorizationMapper() {
        ClassLoader classLoader = getClass().getClassLoader();

        BasicPolymorphicTypeValidator.Builder typeValidatorBuilder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Long.class)
                .allowIfSubType(SecurityUser.class)
                .allowIfSubType(MultiFactorAuthenticationToken.class)
                .allowIfSubType(CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class);

        List<JacksonModule> modules = new ArrayList<>(
                SecurityJacksonModules.getModules(classLoader, typeValidatorBuilder));
        modules.add(createAuthorizationModule());

        return JsonMapper.builder()
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .changeDefaultPropertyInclusion(inclusion ->
                        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(tools.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .addModules(modules)
                .build();
    }

    private SimpleModule createAuthorizationModule() {
        SimpleModule module = new SimpleModule("TinyAuthorizationJacksonModule");
        module.addSerializer(MultiFactorAuthenticationToken.class, new MultiFactorAuthenticationTokenJackson3Serializer());
        module.addDeserializer(MultiFactorAuthenticationToken.class, new MultiFactorAuthenticationTokenJackson3Deserializer());
        module.addDeserializer(CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class,
                new CustomWebAuthenticationDetailsJackson3Deserializer());
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
