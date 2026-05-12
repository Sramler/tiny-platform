package com.tiny.platform.application.oauth.workflow.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class ProcessModelRequests {

    private ProcessModelRequests() {
    }

    public record Create(
        @Size(max = 128) String modelKey,
        @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Min(1) Integer version,
        String bpmnXml,
        String svg
    ) {
    }

    public record Update(
        @Size(max = 200) String name,
        @Size(max = 1000) String description,
        String bpmnXml,
        String svg,
        Long lockVersion
    ) {
    }
}
