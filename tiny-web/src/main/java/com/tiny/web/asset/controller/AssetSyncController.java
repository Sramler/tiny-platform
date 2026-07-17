package com.tiny.web.asset.controller;

import com.tiny.web.asset.dto.AssetSyncRequest;
import com.tiny.web.asset.dto.AssetSyncResult;
import com.tiny.web.asset.service.AssetUsageLocationSyncService;
import com.tiny.web.core.GlobalResponse;
import com.tiny.web.core.ResponseCode;
import com.tiny.web.core.ResponseUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/asset-sync")
public class AssetSyncController {

    private final AssetUsageLocationSyncService syncService;

    public AssetSyncController(AssetUsageLocationSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/usage-location")
    public ResponseEntity<GlobalResponse<AssetSyncResult>> syncUsageLocation(
            @Valid @RequestBody(required = false) AssetSyncRequest request) {
        try {
            return ResponseUtil.ok(syncService.sync(request));
        } catch (IllegalStateException ex) {
            return ResponseUtil.<AssetSyncResult>builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .code(ResponseCode.INVALID_PARAMETER.getCode())
                    .message(ex.getMessage())
                    .build();
        }
    }
}
