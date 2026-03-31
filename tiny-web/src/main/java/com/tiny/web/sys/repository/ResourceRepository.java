package com.tiny.web.sys.repository;

import com.tiny.web.sys.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 演示模块：若库中存在 {@code role_permission}（与 oauth 同库），按角色授权链解析可访问资源；否则回退 {@link #findAll()}。
 */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    @Query(value = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'role_permission'",
            nativeQuery = true)
    long countRolePermissionTable();

    @Query(value = """
            SELECT DISTINCT
              COALESCE(NULLIF(TRIM(res.uri), ''), NULLIF(TRIM(res.url), '')) AS path,
              res.method AS method
            FROM role ro
            JOIN role_permission rp
              ON rp.role_id = ro.id
             AND rp.normalized_tenant_id = IFNULL(ro.tenant_id, 0)
             AND rp.tenant_id <=> ro.tenant_id
            JOIN permission p
              ON p.id = rp.permission_id
             AND p.normalized_tenant_id = rp.normalized_tenant_id
            JOIN resource res
              ON res.permission = p.permission_code
             AND res.normalized_tenant_id = p.normalized_tenant_id
            WHERE ro.tenant_id = :tenantId
              AND rp.tenant_id = :tenantId
              AND (ro.code = :role OR ro.name = :role)
              AND res.enabled = 1
            """, nativeQuery = true)
    List<GrantedResourceAccessRow> findGrantedResourceAccessRows(@Param("tenantId") long tenantId, @Param("role") String role);
}