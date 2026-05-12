package com.tiny.platform.application.oauth.workflow.model;

import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.workflow.service.ProcessModelService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/process/models")
@ConditionalOnProperty(prefix = "camunda.bpm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessModelController {

    private final ProcessModelService processModelService;

    public ProcessModelController(ProcessModelService processModelService) {
        this.processModelService = processModelService;
    }

    @GetMapping
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<List<ProcessModelDto>> list() {
        return ResponseEntity.ok(processModelService.listCurrentScope());
    }

    @GetMapping("/groups")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<List<ProcessModelGroupDto>> listGroups() {
        return ResponseEntity.ok(processModelService.listGroupsCurrentScope());
    }

    @PostMapping
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<ProcessModelDto> create(
        @Valid @RequestBody ProcessModelRequests.Create request,
        Principal principal
    ) {
        return ResponseEntity.ok(processModelService.create(request, actor(principal)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@workflowAccessGuard.canView(authentication)")
    public ResponseEntity<ProcessModelDto> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(processModelService.getCurrentScope(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<ProcessModelDto> update(
        @PathVariable("id") Long id,
        @Valid @RequestBody ProcessModelRequests.Update request,
        Principal principal
    ) {
        return ResponseEntity.ok(processModelService.update(id, request, actor(principal)));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    public ResponseEntity<ProcessModelValidationResponse> validate(
        @PathVariable("id") Long id,
        Principal principal
    ) {
        return ResponseEntity.ok(processModelService.validate(id, actor(principal)));
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("@workflowAccessGuard.canConfig(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<ProcessModelDeployResponse> deploy(
        @PathVariable("id") Long id,
        Principal principal
    ) {
        return ResponseEntity.ok(processModelService.deploy(id, actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal != null && principal.getName() != null ? principal.getName() : "system";
    }
}
