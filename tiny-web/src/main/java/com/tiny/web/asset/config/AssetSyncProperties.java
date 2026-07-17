package com.tiny.web.asset.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AssetSyncProperties {

    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    @Value("${tiny.asset-sync.source-table:a}")
    private String sourceTable = "a";

    @Value("${tiny.asset-sync.target-table:b}")
    private String targetTable = "b";

    @Value("${tiny.asset-sync.target-detail-table:b1}")
    private String targetDetailTable = "b1";

    @Value("${tiny.asset-sync.source-date-column:data_date}")
    private String sourceDateColumn = "data_date";

    @Value("${tiny.asset-sync.source-code-column:asset_code}")
    private String sourceCodeColumn = "asset_code";

    @Value("${tiny.asset-sync.source-user-column:used_by}")
    private String sourceUserColumn = "used_by";

    @Value("${tiny.asset-sync.source-location-column:storage_location}")
    private String sourceLocationColumn = "storage_location";

    @Value("${tiny.asset-sync.target-code-column:asset_code}")
    private String targetCodeColumn = "asset_code";

    @Value("${tiny.asset-sync.target-detail-ref-column:user_defines}")
    private String targetDetailRefColumn = "user_defines";

    @Value("${tiny.asset-sync.target-detail-id-column:id}")
    private String targetDetailIdColumn = "id";

    @Value("${tiny.asset-sync.target-user-column:used_by}")
    private String targetUserColumn = "used_by";

    @Value("${tiny.asset-sync.target-location-column:storage_location}")
    private String targetLocationColumn = "storage_location";

    @PostConstruct
    public void validateConfiguredIdentifiers() {
        validateIdentifier("tiny.asset-sync.source-table", sourceTable);
        validateIdentifier("tiny.asset-sync.target-table", targetTable);
        validateIdentifier("tiny.asset-sync.target-detail-table", targetDetailTable);
        validateIdentifier("tiny.asset-sync.source-date-column", sourceDateColumn);
        validateIdentifier("tiny.asset-sync.source-code-column", sourceCodeColumn);
        validateIdentifier("tiny.asset-sync.source-user-column", sourceUserColumn);
        validateIdentifier("tiny.asset-sync.source-location-column", sourceLocationColumn);
        validateIdentifier("tiny.asset-sync.target-code-column", targetCodeColumn);
        validateIdentifier("tiny.asset-sync.target-detail-ref-column", targetDetailRefColumn);
        validateIdentifier("tiny.asset-sync.target-detail-id-column", targetDetailIdColumn);
        validateIdentifier("tiny.asset-sync.target-user-column", targetUserColumn);
        validateIdentifier("tiny.asset-sync.target-location-column", targetLocationColumn);
    }

    private static void validateIdentifier(String propertyName, String value) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(propertyName + " is not a safe SQL identifier: " + value);
        }
    }

    public String sourceTable() {
        return sourceTable;
    }

    public String targetTable() {
        return targetTable;
    }

    public String targetDetailTable() {
        return targetDetailTable;
    }

    public String sourceDateColumn() {
        return sourceDateColumn;
    }

    public String sourceCodeColumn() {
        return sourceCodeColumn;
    }

    public String sourceUserColumn() {
        return sourceUserColumn;
    }

    public String sourceLocationColumn() {
        return sourceLocationColumn;
    }

    public String targetCodeColumn() {
        return targetCodeColumn;
    }

    public String targetDetailRefColumn() {
        return targetDetailRefColumn;
    }

    public String targetDetailIdColumn() {
        return targetDetailIdColumn;
    }

    public String targetUserColumn() {
        return targetUserColumn;
    }

    public String targetLocationColumn() {
        return targetLocationColumn;
    }
}
