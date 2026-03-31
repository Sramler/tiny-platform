package com.tiny.platform.application.controller.datascope;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScope;
import com.tiny.platform.infrastructure.auth.datascope.domain.RoleDataScopeItem;
import com.tiny.platform.infrastructure.auth.datascope.service.DataScopeAdminService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据范围规则管理控制器。
 *
 * <p>提供角色-模块级数据范围规则的查询、创建/更新、删除，以及 CUSTOM 明细管理。</p>
 */
@RestController
@RequestMapping("/sys/data-scope")
public class DataScopeController {

    private final DataScopeAdminService dataScopeAdminService;

    public DataScopeController(DataScopeAdminService dataScopeAdminService) {
        this.dataScopeAdminService = dataScopeAdminService;
    }

    @GetMapping
    @PreAuthorize("@dataScopeAccessGuard.canView(authentication)")
    public ResponseEntity<List<RoleDataScope>> list() {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(dataScopeAdminService.listByTenant(tenantId));
    }

    @GetMapping("/role/{roleId}")
    @PreAuthorize("@dataScopeAccessGuard.canView(authentication)")
    public ResponseEntity<List<RoleDataScope>> listByRole(@PathVariable Long roleId) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(dataScopeAdminService.listByRole(tenantId, roleId));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("@dataScopeAccessGuard.canView(authentication)")
    public ResponseEntity<List<RoleDataScopeItem>> listItems(@PathVariable Long id) {
        return ResponseEntity.ok(dataScopeAdminService.listItems(id));
    }

    @PostMapping
    @PreAuthorize("@dataScopeAccessGuard.canEdit(authentication)")
    public ResponseEntity<RoleDataScope> upsert(@RequestBody Map<String, Object> body) {
        Long tenantId = requireTenantId();
        Long roleId = ((Number) body.get("roleId")).longValue();
        String module = (String) body.get("module");
        String scopeType = (String) body.get("scopeType");
        String accessType = body.containsKey("accessType") ? (String) body.get("accessType") : "READ";

        return ResponseEntity.ok(dataScopeAdminService.upsert(tenantId, roleId, module, scopeType, accessType, null));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("@dataScopeAccessGuard.canEdit(authentication)")
    public ResponseEntity<RoleDataScopeItem> addItem(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        String targetType = (String) body.get("targetType");
        Long targetId = ((Number) body.get("targetId")).longValue();
        return ResponseEntity.ok(dataScopeAdminService.addItem(id, targetType, targetId));
    }

    @PutMapping("/{id}/items")
    @PreAuthorize("@dataScopeAccessGuard.canEdit(authentication)")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> replaceItems(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<RoleDataScopeItem> items = rawItems.stream().map(m -> {
            RoleDataScopeItem item = new RoleDataScopeItem();
            item.setTargetType((String) m.get("targetType"));
            item.setTargetId(((Number) m.get("targetId")).longValue());
            return item;
        }).toList();
        dataScopeAdminService.replaceItems(id, items);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("@dataScopeAccessGuard.canEdit(authentication)")
    public ResponseEntity<Void> delete(@RequestParam Long roleId,
                                       @RequestParam String module,
                                       @RequestParam(defaultValue = "READ") String accessType) {
        Long tenantId = requireTenantId();
        dataScopeAdminService.delete(tenantId, roleId, module, accessType);
        return ResponseEntity.noContent().build();
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少租户上下文");
        }
        return tenantId;
    }
}
