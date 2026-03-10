package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaValidationServiceTest {

    private final JsonSchemaValidationService service = new JsonSchemaValidationService(new ObjectMapper());

    @Test
    void ensureValidSchemaShouldAcceptBlankSchema() {
        assertThatCode(() -> service.ensureValidSchema(" ")).doesNotThrowAnyException();
    }

    @Test
    void ensureValidSchemaShouldRejectInvalidJson() {
        assertThatThrownBy(() -> service.ensureValidSchema("{invalid"))
                .hasMessageContaining("解析 JSON Schema 失败");
    }

    @Test
    void validateShouldSupportNestedObjectsArraysAndEnums() {
        String schema = """
                {
                  "required": ["name", "mode", "config", "items"],
                  "properties": {
                    "name": { "type": "string", "minLength": 3, "pattern": "^[a-z_]+$" },
                    "mode": { "enum": ["FAST", "SAFE"] },
                    "config": {
                      "type": "object",
                      "required": ["enabled"],
                      "properties": {
                        "enabled": { "type": "boolean" }
                      }
                    },
                    "items": {
                      "type": "array",
                      "items": { "type": "integer", "minimum": 1, "maximum": 10 }
                    }
                  }
                }
                """;

        assertThatCode(() -> service.validate(schema, Map.of(
                "name", "daily_stat",
                "mode", "FAST",
                "config", Map.of("enabled", true),
                "items", List.of(1, 2, 3)
        ))).doesNotThrowAnyException();
    }

    @Test
    void validateShouldRejectInvalidPayload() {
        String schema = """
                {
                  "required": ["name", "count"],
                  "properties": {
                    "name": { "type": "string", "minLength": 3 },
                    "count": { "type": "integer", "minimum": 1 }
                  }
                }
                """;

        assertThatThrownBy(() -> service.validate(schema, Map.of(
                "name", "ab",
                "count", 0
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$.name")
                .hasMessageContaining("$.count");
    }
}
