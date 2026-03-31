# Permission Refactor Phase F Gray Checklist

## Scope

- Phase target: non-menu authorization path gray run only
- Excluded: menu chain migration, old model offlining
- Baseline: `docs/TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md`

## 1) Gray Window Definition

- [ ] Gray window start time (UTC+8):
- [ ] Gray window end time (UTC+8):
- [ ] Rollback owner:
- [ ] Incident contact channel:

## 2) Gray Tenant and Scope Allow List

- [ ] Gray tenant list is explicit (no wildcard):

| tenantId | tenantCode | tenantName | batch | includes ORG/DEPT |
| --- | --- | --- | --- | --- |
|  |  |  | first/second | yes/no |

- [ ] Scope allow list for this batch:
  - [ ] `PLATFORM`
  - [ ] `TENANT`
  - [ ] `ORG` (optional)
  - [ ] `DEPT` (optional)

## 3) Config Snapshot

Record runtime config values before run:

```yaml
permission-refactor:
  authority-diff-log-enabled:
  permission-version-debug-enabled:
  fail-closed-strict-enabled:
  gray-tenant-allow-list:
  gray-scope-type-allow-list:
  diff-sample-rate:
```

Checks:

- [ ] `authority-diff-log-enabled = true` for gray tenants
- [ ] `permission-version-debug-enabled = true` for gray tenants
- [ ] `fail-closed-strict-enabled` behavior documented (log enhancement only, not allow/deny bypass)
- [ ] `diff-sample-rate = 1.0` for first batch (or explicit reason recorded)

## 4) Baseline Validation Before Traffic

- [ ] PLATFORM login path smoke
- [ ] TENANT login path smoke
- [ ] ORG scope smoke (if included)
- [ ] DEPT scope smoke (if included)
- [ ] Existing `/sys/menus/tree` behavior unchanged (non-goal guardrail)

## 5) SQL Verification Execution

Run the scripts and save output artifacts:

```bash
mysql -h<host> -P<port> -u<user> -p <db_name> < tiny-oauth-server/scripts/verify-permission-phase-e-observability.sql
mysql -h<host> -P<port> -u<user> -p <db_name> < tiny-oauth-server/scripts/verify-permission-phase-e-scope-bucket.sql
```

Checks:

- [ ] `verify-permission-phase-e-observability.sql` executed
- [ ] `verify-permission-phase-e-scope-bucket.sql` executed
- [ ] Result files attached to run record

## 6) Runtime Signal Collection

Collect and summarize:

- [ ] `OLD_PERMISSION_ONLY` count (旧口径: `OLD_FALLBACK`) and top causes
- [ ] `DENY_DISABLED` count and top permission codes
- [ ] `DENY_UNKNOWN` count and top permission codes
- [ ] `permissionsVersion` change reasons distribution:
  - `ROLE_ASSIGNMENT_CHANGED`
  - `OLD_PERMISSION_INPUT_CHANGED`
  - `ROLE_PERMISSION_CHANGED`
  - `PERMISSION_MASTER_CHANGED`
  - `ROLE_HIERARCHY_CHANGED`

## 7) Scope Bucket Integrity (ORG/DEPT)

If ORG/DEPT are included in this batch:

- [ ] No cross-bucket compare evidence between different `scopeType + scopeId`
- [ ] Same tenant, different ORG do not contaminate each other
- [ ] Same tenant, different DEPT do not contaminate each other
- [ ] Fallback/deny/version logs include clear `scopeType + scopeId`

## 8) Go/No-Go Decision

Pick exactly one:

- [ ] Expand gray range
- [ ] Keep current range and continue observation
- [ ] Roll back observability switches and investigate

Decision evidence:

- [ ] No unexplained privilege escalation
- [ ] No unexplained privilege drop
- [ ] No unexplained scope bucket contamination
- [ ] No menu drift complaints

## 9) Minimal Rollback Steps

If rollback needed:

1. Remove gray tenants from `gray-tenant-allow-list`
2. Disable:
   - `authority-diff-log-enabled`
   - `permission-version-debug-enabled`
3. Keep D-1 / D-2 / E core logic unchanged
4. Keep menu chain unchanged

Checklist:

- [ ] Rollback config applied
- [ ] Post-rollback smoke passed
- [ ] Incident record updated
