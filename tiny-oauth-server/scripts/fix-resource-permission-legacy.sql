-- Fix C: clean legacy dirty resource.permission values (tenant 1/3).
-- Rule:
-- 1) trim whitespace always
-- 2) set empty-after-trim to NULL (keep deny semantics, avoid noisy dirty values)
-- 3) keep malformed/non-empty values for fail-closed governance (no auto-normalization)

UPDATE resource
SET permission = TRIM(permission)
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND permission <> TRIM(permission);

UPDATE resource
SET permission = NULL
WHERE tenant_id IN (1, 3)
  AND permission IS NOT NULL
  AND TRIM(permission) = '';
