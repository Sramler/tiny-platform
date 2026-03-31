-- Cleanup template for permission refactor E2E.
-- Intentionally conservative: only rollback rows with explicit test markers.

-- 1) Restore permission.enabled toggles if marked by test updater.
UPDATE permission
SET enabled = 1,
    updated_at = NOW()
WHERE updated_by = -999001
  AND enabled = 0;

-- 2) Remove temporary hierarchy edges inserted by tests.
DELETE FROM role_hierarchy
WHERE created_by = -999001;

-- 3) Remove temporary role assignments inserted by tests.
DELETE FROM role_assignment
WHERE granted_by = -999001;

-- 4) Post-cleanup checks.
SELECT
  (SELECT COUNT(*) FROM permission WHERE updated_by = -999001 AND enabled = 0) AS remaining_disabled_probe,
  (SELECT COUNT(*) FROM role_hierarchy WHERE created_by = -999001) AS remaining_hierarchy_probe,
  (SELECT COUNT(*) FROM role_assignment WHERE granted_by = -999001) AS remaining_assignment_probe;
