package com.tiny.platform.infrastructure.tenant.dto;

import java.util.ArrayList;
import java.util.List;

public class TenantPrecheckResponseDto {
    private boolean ok;
    private List<TenantPrecheckIssueDto> blockingIssues = new ArrayList<>();
    private List<TenantPrecheckIssueDto> warnings = new ArrayList<>();
    private TenantInitializationSummaryDto initializationSummary;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public List<TenantPrecheckIssueDto> getBlockingIssues() {
        return blockingIssues;
    }

    public void setBlockingIssues(List<TenantPrecheckIssueDto> blockingIssues) {
        this.blockingIssues = blockingIssues;
    }

    public List<TenantPrecheckIssueDto> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<TenantPrecheckIssueDto> warnings) {
        this.warnings = warnings;
    }

    public TenantInitializationSummaryDto getInitializationSummary() {
        return initializationSummary;
    }

    public void setInitializationSummary(TenantInitializationSummaryDto initializationSummary) {
        this.initializationSummary = initializationSummary;
    }
}
