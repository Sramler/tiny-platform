package com.tiny.platform.application.oauth.workflow.model;

import java.util.List;

public record ProcessModelValidationResponse(
    Long id,
    boolean valid,
    String message,
    List<String> warnings,
    String validationStatus
) {
}
