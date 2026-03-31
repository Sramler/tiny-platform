-- Prepare ORG/DEPT fixtures for Suite4.
-- Marker: created_by/granted_by = -999001

SET @fixture_tenant := 1;
SET @fixture_user := (
  SELECT tu.user_id
  FROM tenant_user tu
  WHERE tu.tenant_id = @fixture_tenant
    AND tu.status = 'ACTIVE'
  ORDER BY tu.user_id
  LIMIT 1
);
SET @fixture_role := (
  SELECT r.id
  FROM role r
  WHERE r.tenant_id = @fixture_tenant
    AND r.enabled = 1
  ORDER BY r.id
  LIMIT 1
);

INSERT INTO organization_unit (
  tenant_id, parent_id, unit_type, code, name, sort_order, status, created_at, created_by, updated_at
)
SELECT @fixture_tenant, NULL, 'ORG', 'E2E_ORG_999001', 'E2E ORG 999001', 9001, 'ACTIVE', NOW(), -999001, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM organization_unit ou
  WHERE ou.tenant_id = @fixture_tenant AND ou.code = 'E2E_ORG_999001'
);

SET @org_unit := (
  SELECT ou.id FROM organization_unit ou
  WHERE ou.tenant_id = @fixture_tenant AND ou.code = 'E2E_ORG_999001'
  LIMIT 1
);

INSERT INTO organization_unit (
  tenant_id, parent_id, unit_type, code, name, sort_order, status, created_at, created_by, updated_at
)
SELECT @fixture_tenant, @org_unit, 'DEPT', 'E2E_DEPT_999001', 'E2E DEPT 999001', 9002, 'ACTIVE', NOW(), -999001, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM organization_unit ou
  WHERE ou.tenant_id = @fixture_tenant AND ou.code = 'E2E_DEPT_999001'
);

SET @dept_unit := (
  SELECT ou.id FROM organization_unit ou
  WHERE ou.tenant_id = @fixture_tenant AND ou.code = 'E2E_DEPT_999001'
  LIMIT 1
);

INSERT INTO user_unit (
  tenant_id, user_id, unit_id, is_primary, status, joined_at, left_at, created_at, updated_at
)
SELECT @fixture_tenant, @fixture_user, @org_unit, 1, 'ACTIVE', NOW(), NULL, NOW(), NOW()
WHERE @fixture_user IS NOT NULL
  AND @org_unit IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM user_unit uu
    WHERE uu.tenant_id = @fixture_tenant
      AND uu.user_id = @fixture_user
      AND uu.unit_id = @org_unit
  );

INSERT INTO user_unit (
  tenant_id, user_id, unit_id, is_primary, status, joined_at, left_at, created_at, updated_at
)
SELECT @fixture_tenant, @fixture_user, @dept_unit, 0, 'ACTIVE', NOW(), NULL, NOW(), NOW()
WHERE @fixture_user IS NOT NULL
  AND @dept_unit IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM user_unit uu
    WHERE uu.tenant_id = @fixture_tenant
      AND uu.user_id = @fixture_user
      AND uu.unit_id = @dept_unit
  );

INSERT INTO role_assignment (
  principal_type, principal_id, role_id, tenant_id, scope_type, scope_id,
  status, start_time, end_time, granted_by, granted_at, updated_at
)
SELECT 'USER', @fixture_user, @fixture_role, @fixture_tenant, 'ORG', @org_unit,
       'ACTIVE', NOW(), NULL, -999001, NOW(), NOW()
WHERE @fixture_user IS NOT NULL
  AND @fixture_role IS NOT NULL
  AND @org_unit IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM role_assignment ra
    WHERE ra.principal_type = 'USER'
      AND ra.principal_id = @fixture_user
      AND ra.role_id = @fixture_role
      AND ra.tenant_id = @fixture_tenant
      AND ra.scope_type = 'ORG'
      AND ra.scope_id = @org_unit
      AND ra.granted_by = -999001
  );

INSERT INTO role_assignment (
  principal_type, principal_id, role_id, tenant_id, scope_type, scope_id,
  status, start_time, end_time, granted_by, granted_at, updated_at
)
SELECT 'USER', @fixture_user, @fixture_role, @fixture_tenant, 'DEPT', @dept_unit,
       'ACTIVE', NOW(), NULL, -999001, NOW(), NOW()
WHERE @fixture_user IS NOT NULL
  AND @fixture_role IS NOT NULL
  AND @dept_unit IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM role_assignment ra
    WHERE ra.principal_type = 'USER'
      AND ra.principal_id = @fixture_user
      AND ra.role_id = @fixture_role
      AND ra.tenant_id = @fixture_tenant
      AND ra.scope_type = 'DEPT'
      AND ra.scope_id = @dept_unit
      AND ra.granted_by = -999001
  );

SELECT @fixture_tenant AS tenant_id, @fixture_user AS user_id, @fixture_role AS role_id, @org_unit AS org_scope_id, @dept_unit AS dept_scope_id;
