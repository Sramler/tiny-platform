-- Cleanup ORG/DEPT fixtures created by prepare-org-dept-fixtures.sql
-- Marker: created_by/granted_by = -999001

DELETE FROM role_assignment
WHERE granted_by = -999001
  AND scope_type IN ('ORG', 'DEPT');

DELETE uu
FROM user_unit uu
JOIN organization_unit ou
  ON ou.id = uu.unit_id
WHERE ou.created_by = -999001;

DELETE FROM organization_unit
WHERE created_by = -999001;

SELECT
  (SELECT COUNT(*) FROM role_assignment WHERE granted_by = -999001 AND scope_type IN ('ORG', 'DEPT')) AS remaining_scope_assignments,
  (SELECT COUNT(*) FROM organization_unit WHERE created_by = -999001) AS remaining_scope_units;
