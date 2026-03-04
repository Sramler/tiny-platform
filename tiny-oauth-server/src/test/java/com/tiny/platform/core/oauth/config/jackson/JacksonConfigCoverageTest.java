package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigCoverageTest {

    @Test
    void shouldCustomizeWebObjectMapperAndApplyCommonJacksonSettings() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonCustomizer();
        customizer.customize(builder);

        ObjectMapper mapper = config.webObjectMapper(builder);

        assertThat(mapper.getSerializationConfig().getTimeZone().getID()).isEqualTo("UTC");
        assertThat(mapper.getSerializationConfig().isEnabled(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .isFalse();

        String longJson = mapper.writeValueAsString(Map.of("id", 9007199254740993L));
        assertThat(longJson).contains("\"id\":\"9007199254740993\"");

        String timeJson = mapper.writeValueAsString(Map.of("time", LocalDateTime.of(2024, 1, 2, 3, 4, 5)));
        assertThat(timeJson).contains("\"time\":\"2024-01-02T03:04:05\"");

        EnumHolder enumHolder = mapper.readValue("{\"value\":\"alpha\"}", EnumHolder.class);
        assertThat(enumHolder.value).isEqualTo(TestEnum.ALPHA);

        ListHolder listHolder = mapper.readValue("{\"values\":\"x\",\"unknown\":1}", ListHolder.class);
        assertThat(listHolder.values).containsExactly("x");
    }

    @Test
    void shouldBuildAuthorizationMapperWithCustomModulesAndCompatibilitySettings() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.jacksonCustomizer().customize(builder);

        ObjectMapper mapper = config.authorizationMapper(builder);

        assertThat(mapper).isNotNull();
        assertThat(mapper.getSerializationConfig().isEnabled(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .isFalse();
        assertThat(mapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .isFalse();
        assertThat(mapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
                .isTrue();

        String json = mapper.writeValueAsString(Map.of(
                "id", 123L,
                "time", LocalDateTime.of(2024, 2, 3, 4, 5, 6)));
        assertThat(json).contains("\"123\"");
        assertThat(json).contains("2024-02-03T04:05:06");

    }

    @Test
    void shouldSerializeLongAsStringAndNull() throws Exception {
        SecurityUserLongSerializer serializer = new SecurityUserLongSerializer();
        ObjectMapper mapper = new ObjectMapper();

        StringWriter nonNullOut = new StringWriter();
        JsonGenerator gen1 = new JsonFactory().createGenerator(nonNullOut);
        serializer.serialize(123L, gen1, mapper.getSerializerProvider());
        gen1.close();
        assertThat(nonNullOut.toString()).isEqualTo("\"123\"");

        StringWriter nullOut = new StringWriter();
        JsonGenerator gen2 = new JsonFactory().createGenerator(nullOut);
        serializer.serialize(null, gen2, mapper.getSerializerProvider());
        gen2.close();
        assertThat(nullOut.toString()).isEqualTo("null");
    }

    @Test
    void shouldDeserializeLongFromNumberStringNullAndRejectInvalidTokens() throws Exception {
        SecurityUserLongDeserializer deserializer = new SecurityUserLongDeserializer();
        ObjectMapper mapper = new ObjectMapper();

        JsonParser numberParser = mapper.createParser("123");
        assertThat(deserializer.deserialize(numberParser, mapper.getDeserializationContext())).isEqualTo(123L);

        JsonParser stringParser = mapper.createParser("\"456\"");
        assertThat(deserializer.deserialize(stringParser, mapper.getDeserializationContext())).isEqualTo(456L);

        JsonParser emptyStringParser = mapper.createParser("\"\"");
        assertThat(deserializer.deserialize(emptyStringParser, mapper.getDeserializationContext())).isNull();

        JsonParser nullParser = mapper.createParser("null");
        assertThat(deserializer.deserialize(nullParser, mapper.getDeserializationContext())).isNull();

        JsonParser invalidStringParser = mapper.createParser("\"abc\"");
        assertThatThrownBy(() -> deserializer.deserialize(invalidStringParser, mapper.getDeserializationContext()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("解析为 Long");

        JsonParser objectParser = mapper.createParser("{}");
        assertThatThrownBy(() -> deserializer.deserialize(objectParser, mapper.getDeserializationContext()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("期望 String 或 Number");
    }

    @Test
    void shouldResolveTypeIdsAndSupportFallbackWhenDelegateMissing() throws Exception {
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        LongTypeIdResolver resolver = new LongTypeIdResolver(baseType);

        resolver.init(baseType);
        assertThat(resolver.idFromValue(1L)).isNotBlank();
        assertThat(resolver.idFromValueAndType(null, Long.class)).contains("Long");
        assertThat(resolver.idFromBaseType()).contains("Object");
        assertThat(resolver.getMechanism()).isNotNull();
        // method exists and should be callable regardless of delegate availability
        resolver.getDescForKnownTypeIds();

        JavaType longType = resolver.typeFromId(null, Long.class.getName());
        assertThat(longType.getRawClass()).isEqualTo(Long.class);

        // 强制走无 delegate 的 fallback 分支，覆盖 Class.forName 路径
        ReflectionTestUtils.setField(resolver, "delegate", null);
        JavaType stringType = resolver.typeFromId(null, String.class.getName());
        assertThat(stringType.getRawClass()).isEqualTo(String.class);

        assertThatThrownBy(() -> resolver.typeFromId(null, "com.example.DoesNotExist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not recognized");
    }

    @Test
    void shouldExposeMixinAnnotationAndLongAllowlistModuleName() {
        JsonTypeInfo typeInfo = ResourceResponseDtoMixin.class.getAnnotation(JsonTypeInfo.class);
        assertThat(typeInfo).isNotNull();
        assertThat(typeInfo.use()).isEqualTo(JsonTypeInfo.Id.NONE);

        LongAllowlistModule module = new LongAllowlistModule();
        assertThat(module.getModuleName()).isEqualTo("LongAllowlistModule");
    }

    @Test
    void shouldHandleLongAllowlistModuleSetupAndBeIdempotent() {
        LongAllowlistModule module = new LongAllowlistModule();
        ObjectMapper mapper = new ObjectMapper();

        // 第一次注册会触发 setupModule -> modifyAllowlist
        mapper.registerModule(module);
        // 第二次注册（新实例）应走“已修改”快速返回，不抛异常
        mapper.registerModule(new LongAllowlistModule());
    }

    static class ListHolder {
        public List<String> values;
    }

    static class EnumHolder {
        public TestEnum value;
    }

    enum TestEnum {
        ALPHA, BETA
    }
}
