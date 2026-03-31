-- Report-only: final role scope cleanup reference counts (no data changes)
-- role_binding_cnt uses role_permission（117 后仍可用；不再引用 role_resource）。

SELECT
  r.id, r.tenant_id, r.code, r.name, r.role_level, r.builtin, r.enabled,
  (SELECT COUNT(*) FROM role_assignment ra  WHERE ra.role_id = r.id) AS ra_cnt,
  (SELECT COUNT(*) FROM role_permission rp   WHERE rp.role_id = r.id) AS role_binding_cnt,
  (SELECT COUNT(*) FROM role_data_scope rd  WHERE rd.role_id = r.id) AS rds_cnt,
  (SELECT COUNT(*) FROM role_hierarchy rh   WHERE rh.parent_role_id = r.id OR rh.child_role_id = r.id) AS rh_cnt,
  (SELECT COUNT(*) FROM role_mutex rm        WHERE rm.left_role_id = r.id OR rm.right_role_id = r.id) AS rm_cnt,
  (SELECT COUNT(*) FROM role_prerequisite rp WHERE rp.role_id = r.id OR rp.required_role_id = r.id) AS rp_cnt,
  (SELECT COUNT(*) FROM role_cardinality rc WHERE rc.role_id = r.id) AS rc_cnt
FROM role r
WHERE r.id IN (1044,1045,1048,1049,683,5,6)
ORDER BY r.id;

SELECT id, tenant_id, code, name, role_level, builtin, enabled
FROM role
WHERE id = 1050;

SELECT
  SUM(CASE WHEN tenant_id IS NULL AND code IN ('ROLE_OPERATOR','ROLE_USER_MANAGER','ROLE_TENANT_ADMIN','ROLE_DEVELOPER') THEN 1 ELSE 0 END) AS platform_semantic_roles_remaining_cnt,
  SUM(CASE WHEN tenant_id = 3 AND code = 'ROLE_TENANT_ADMIN' THEN 1 ELSE 0 END) AS tenant3_tenant_admin_roles_cnt,
  SUM(CASE WHEN tenant_id IS NOT NULL AND code IN ('ROLE_SYSTEM_ADMIN','ROLE_TENANT_USER') THEN 1 ELSE 0 END) AS tenant_compat_roles_cnt
FROM role;

