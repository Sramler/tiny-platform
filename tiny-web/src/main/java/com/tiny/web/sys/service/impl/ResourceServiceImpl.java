package com.tiny.web.sys.service.impl;

import com.tiny.web.sys.ResourceService;
import com.tiny.web.sys.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    /** 与默认 seed 租户对齐；演示模块在无租户上下文时固定此值。 */
    private static final long DEMO_AUTH_TENANT_ID = 1L;

    private final ResourceRepository resourceRepository;

    @Override
    @SuppressWarnings("unused")
    public boolean hasAccess(String role, String path, String method) {
        return resourceRepository.findGrantedResourceAccessRows(DEMO_AUTH_TENANT_ID, role).stream()
            .anyMatch(row -> pathsMatch(path, row.getPath()) && methodsMatch(method, row.getMethod()));
    }

    private static boolean pathsMatch(String requested, String stored) {
        if (requested == null || stored == null) {
            return false;
        }
        return requested.trim().equalsIgnoreCase(stored.trim());
    }

    private static boolean methodsMatch(String requested, String stored) {
        String a = requested == null ? "" : requested.trim();
        String b = stored == null ? "" : stored.trim();
        return a.equalsIgnoreCase(b);
    }
}
