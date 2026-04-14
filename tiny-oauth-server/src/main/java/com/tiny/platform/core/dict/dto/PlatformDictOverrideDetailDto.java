package com.tiny.platform.core.dict.dto;

/**
 * 平台字典基线与租户 overlay 差异行。
 */
public class PlatformDictOverrideDetailDto {
    private String value;
    private String status;
    private String baselineLabel;
    private String overlayLabel;
    private String effectiveLabel;
    private boolean labelChanged;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBaselineLabel() {
        return baselineLabel;
    }

    public void setBaselineLabel(String baselineLabel) {
        this.baselineLabel = baselineLabel;
    }

    public String getOverlayLabel() {
        return overlayLabel;
    }

    public void setOverlayLabel(String overlayLabel) {
        this.overlayLabel = overlayLabel;
    }

    public String getEffectiveLabel() {
        return effectiveLabel;
    }

    public void setEffectiveLabel(String effectiveLabel) {
        this.effectiveLabel = effectiveLabel;
    }

    public boolean isLabelChanged() {
        return labelChanged;
    }

    public void setLabelChanged(boolean labelChanged) {
        this.labelChanged = labelChanged;
    }
}
