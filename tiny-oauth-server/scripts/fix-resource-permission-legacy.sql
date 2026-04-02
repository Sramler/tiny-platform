-- Fix C: clean legacy dirty carrier.permission values (tenant 1/3).
-- Rule:
-- 1) trim whitespace always
-- 2) set empty-after-trim to NULL (keep deny semantics, avoid noisy dirty values)
-- 3) keep malformed/non-empty values for fail-closed governance (no auto-normalization)

UPDATE menu
SET permission = TRIM(permission)
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND permission <> TRIM(permission);

UPDATE menu
SET permission = NULL
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND TRIM(permission) = '';

UPDATE ui_action
SET permission = TRIM(permission)
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND permission <> TRIM(permission);

UPDATE ui_action
SET permission = NULL
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND TRIM(permission) = '';

UPDATE api_endpoint
SET permission = TRIM(permission)
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND permission <> TRIM(permission);

UPDATE api_endpoint
SET permission = NULL
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND TRIM(permission) = '';
