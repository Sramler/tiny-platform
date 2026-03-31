# Permission Refactor E2E Cases

## Suite 1: Auth and Context

- `E2E-001` PLATFORM login success
  - Preconditions: platform test user exists
  - Assertions: `activeScopeType=PLATFORM`, `permissionsVersion` non-empty, authorities non-empty
  - Cleanup: logout/session clear

- `E2E-002` TENANT login success
  - Preconditions: tenant user in tenant 1
  - Assertions: `activeScopeType=TENANT`, `activeTenantId=1`
  - Cleanup: logout/session clear

- `E2E-003` same user tenant switch (1 -> 3)
  - Assertions: no tenant contamination, version/authority delta explainable
  - Cleanup: session clear

## Suite 2: Permission Mutation Linkage

- `E2E-101` grant role assignment
- `E2E-102` revoke role assignment
- `E2E-103` permission.enabled 1->0
- `E2E-104` permission.enabled 0->1
- `E2E-105` add role_hierarchy edge
- `E2E-106` remove role_hierarchy edge

Each case must include mutate + verify + restore.

## Suite 3: New/Old Path Consistency

- `E2E-201` new path full cover old path
- `E2E-202` OLD_PERMISSION_ONLY (旧口径: `OLD_FALLBACK`) trigger and explain
- `E2E-203` DENY_DISABLED trigger
- `E2E-204` DENY_UNKNOWN trigger (test DB only)

## Suite 4: Scope Bucket Isolation

- `E2E-301` PLATFORM vs TENANT isolation
- `E2E-302` tenant 1 vs tenant 3 isolation
- `E2E-303` ORG scope isolation (pending if fixture absent)
- `E2E-304` DEPT scope isolation (pending if fixture absent)

## Suite 5: Menu Non-Drift

- `E2E-401` `/sys/menus/tree` platform baseline
- `E2E-402` `/sys/menus/tree` tenant baseline
- `E2E-403` permission-side mutate should not drift menu tree unexpectedly

## Suite 6: Short Stability

- `E2E-501` repeated login + tenant switch loop (10-20 rounds)
- `E2E-502` repeated mutate-restore loop for role/permission/hierarchy

## Mandatory Signal Collection

- OLD_PERMISSION_ONLY (旧口径: `OLD_FALLBACK`)
- DENY_DISABLED
- DENY_UNKNOWN
- ROLE_ASSIGNMENT_CHANGED
- OLD_PERMISSION_INPUT_CHANGED
- ROLE_PERMISSION_CHANGED
- PERMISSION_MASTER_CHANGED
- ROLE_HIERARCHY_CHANGED

## Pass Criteria

- Suite 1 and Suite 2 fully pass
- deny/fallback behavior is expected and explainable
- no scope bucket contamination in enabled scopes
- menu non-drift checks pass
- all test data restored
